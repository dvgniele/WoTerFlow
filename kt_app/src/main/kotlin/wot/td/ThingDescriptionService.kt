package wot.td

import com.fasterxml.jackson.databind.node.ObjectNode
import errors.ValidationError
import exceptions.ConversionException
import exceptions.ThingException
import exceptions.ValidationException
import io.ktor.server.plugins.*
import org.apache.jena.query.*
import org.apache.jena.rdf.model.*
import org.apache.jena.riot.Lang
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.apache.jena.update.UpdateProcessor
import org.mapdb.DB
import utils.RDFConverter
import utils.Utils
import java.time.Instant
import java.util.*


class ThingDescriptionService(dbRdf: Dataset, dbJson: DB?) {

    private val converter: RDFConverter = RDFConverter()
    private val validator: ThingDescriptionValidation = ThingDescriptionValidation()


    private val BASE_URI = "http://example.com/ktwot/"

    //private val GRAPH_PREFIX = BASE_URI + "graph/"
    private val GRAPH_PREFIX = BASE_URI + "graph/"

    private val rdfDataset: Dataset = dbRdf
    /*
    private val jsonDB: DB = dbJson
    val thingsMap = jsonDB.hashMap("things")
        .keySerializer(Serializer.STRING)
        .valueSerializer(ObjectNodeSerializer())
        .createOrOpen()
     */

    private val thingsMap: MutableMap<String, ObjectNode> = mutableMapOf()

    val jsonContextUrl10 = "https://www.w3.org/2019/wot/td/v1"
    val jsonContextUrl11 = "https://www.w3.org/2022/wot/td/v1.1"
    //private val jsonContext11 = "data/validation/tdv11.jsonld"
    private val jsonContext11 = "data/validation/tm-json-schema-validation.json"
    //private val jsonContext11 = "data/validation/td-json-schema-validation.json"
    val jsonLdContextResourcePath = "data/validation/td.jsonld"
    private val ttlContextResourcePath = "data/validation/td-validation.ttl"
    private val xmlShapesPath = "data/validation/shacl.xml"

    private var jsonLdContextModel: Model = Utils.loadModel(jsonContext11, Lang.JSONLD11)
    private var ttlContextModel: Model = Utils.loadModel(ttlContextResourcePath, Lang.TURTLE)
    private var xmlShapesModel: Model = Utils.loadModel(xmlShapesPath, Lang.RDFXML)


    private fun refreshJsonDbItem(graphId: String) {
        try {
            val ttlModel = Utils.loadRDFModelById(rdfDataset, graphId)


            val objNode = converter.fromRdf(ttlModel)
            val thing = converter.toJsonLd11(Utils.toJson(objNode.toString()))

            if (thingsMap.containsKey(graphId)) {
                thingsMap.remove(graphId)
            }

            thingsMap[graphId] = thing
        } catch (e: Exception) {
            throw ThingException("Error refreshing the JsonDb item with id: $graphId: ${e.message}")
        } finally {

        }
    }
    fun refreshJsonDb() {
        rdfDataset.begin(ReadWrite.READ)

        try {
            val ttlList = Utils.loadRDFDatasetIntoModelList(rdfDataset)
            val things = ttlList.map { converter.toJsonLd11(converter.fromRdf(it)) }

            //  Clear the things map and populate it back with the updated dataset
            thingsMap.clear()

            things.forEach { thing ->
                thing["id"]?.asText()?.let { id ->
                    thingsMap[Utils.strconcat(GRAPH_PREFIX,id)] = thing
                }
            }

            println("\t###\tREADY\t###")
        } catch (e: Exception) {
            throw ThingException("Error refreshing the JsonDb: ${e.message}")
        } finally {
            rdfDataset.end()
        }

    }

    fun insertAnonymousThing(td: ObjectNode): String {
        rdfDataset.begin(ReadWrite.WRITE)

        var query = ""

        var uuid = UUID.randomUUID().toString()
        var id = Utils.strconcat("urn:uuid:", uuid)
        var graphId = Utils.strconcat(GRAPH_PREFIX, id)

        try {
            while (Utils.idExists(thingsMap.keys, graphId)){
                uuid = UUID.randomUUID().toString()
                id = Utils.strconcat("urn:uuid:", uuid)
                graphId = Utils.strconcat(GRAPH_PREFIX, id)
            }

            td.put("@id", id)
            //td.put("id", graphId)

            // Checking the jsonld version and upgrading if needed
            val tdVersion11p = Utils.isJsonLd11OrGreater(td)

            val tdV11 = if (!tdVersion11p) converter.toJsonLd11(td) else td

            // JsonLd decoration with missing fields
            decorateThingDescription(tdV11)

            val jsonRdfModel = converter.convertJsonLdToRdf(tdV11.toString(), Lang.JSONLD11)

            //  Performing Syntactic Validation
            val syntacticValidationFailures = validator.validateSyntactic(jsonRdfModel, xmlShapesModel)

            if (syntacticValidationFailures.isNotEmpty()) {
                val validationErrors = syntacticValidationFailures.map {
                    ValidationError(
                        "Syntactic Validation",
                        it)
                }
                throw ValidationException(validationErrors, "Syntactic Validation Failed")
            }

            //  Performing Semantic Validation

            val semanticValidationFailures = validator.validateSemantic(jsonRdfModel, ttlContextModel)

            if (semanticValidationFailures.isNotEmpty()) {
                val validationErrors = semanticValidationFailures.map {
                    ValidationError(
                        "Semantic Validation",
                        it)
                }
                throw ValidationException(validationErrors, "Semantic Validation Failed")
            }


            // Query preparation for RDF data storing
            val rdfTriplesString = converter.toString(jsonRdfModel)
            query = """
            INSERT DATA {
                GRAPH <$graphId> {
                    $rdfTriplesString
                }
            }
        """.trimIndent()

            println("query:\n$query")

            //  Execute query
            val update = UpdateFactory.create(query)
            val updateExecution: UpdateProcessor = UpdateExecutionFactory.create(update, rdfDataset)
            updateExecution.execute()

            refreshJsonDbItem(graphId)

            //  Commit to close db connection
            rdfDataset.commit()

            return id
        } catch (e: ThingException) {
            rdfDataset.abort()

            throw ThingException("An error occurred while storing the thing: ${e.message}")
        } catch (e: ValidationException) {
            rdfDataset.abort()

            throw e
        } catch (e: ConversionException) {
            rdfDataset.abort()

            throw ConversionException("Create Anonymous Thing Error: ${e.message}")
        } catch (e: Exception) {
            rdfDataset.abort()

            throw ThingException("Create new Thing Error: ${e.message}\nquery:\n$query")
        } finally {
                rdfDataset.end()
        }
    }

    fun updateThing(td: ObjectNode): Pair<String, Boolean> {
        val id: String = td.get("@id")?.takeIf { it.isTextual }?.asText()
            ?: td.get("id")?.takeIf { it.isTextual }?.asText()
            ?: throw BadRequestException("Invalid or missing @id field in the JSON body.")

        rdfDataset.begin(ReadWrite.WRITE)

        var query = ""

        try {
            val existsAlready = checkIfThingExists(id)
            val graphId = Utils.strconcat(GRAPH_PREFIX, id)

            val tdVersion11p = Utils.isJsonLd11OrGreater(td)
            val tdV11 = if (!tdVersion11p) converter.toJsonLd11(td) else td

            decorateThingDescription(tdV11)

            val jsonRdfModel = converter.convertJsonLdToRdf(tdV11.toPrettyString(), Lang.JSONLD11)

            //  Performing Syntactic Validation
            val syntacticValidationFailures = validator.validateSyntactic(jsonRdfModel, xmlShapesModel)

            if (syntacticValidationFailures.isNotEmpty()) {
                val validationErrors = syntacticValidationFailures.map {
                    ValidationError(
                        "Syntactic Validation",
                        it)
                }
                throw ValidationException(validationErrors, "Syntactic Validation Failed")
            }

            //  Performing Semantic Validation

            val semanticValidationFailures = validator.validateSemantic(jsonRdfModel, ttlContextModel)

            if (semanticValidationFailures.isNotEmpty()) {
                val validationErrors = semanticValidationFailures.map {
                    ValidationError(
                        "Semantic Validation",
                        it)
                }
                throw ValidationException(validationErrors, "Semantic Validation Failed")
            }

            // Query preparation for RDF data storing
            val rdfTriplesString = converter.toString(jsonRdfModel)
            query = """
            DELETE WHERE {
                GRAPH <$graphId> {
                    ?s ?p ?o
                }
            };
            INSERT DATA {
                GRAPH <$graphId> {
                    $rdfTriplesString
                }
            }
        """.trimIndent()

            println("query:\n$query")

            //  Execute query
            val update = UpdateFactory.create(query)
            val updateExecution: UpdateProcessor = UpdateExecutionFactory.create(update, rdfDataset)
            updateExecution.execute()

            refreshJsonDbItem(graphId)

            //  Commit to close db connection
            rdfDataset.commit()

            return Pair(id, existsAlready)
        } catch (e: ThingException) {
            rdfDataset.abort()

            throw ThingException("An error occurred while updating the thing: ${e.message}")
        } catch (e: ValidationException) {
            rdfDataset.abort()

            throw e
        } catch (e: Exception) {
            rdfDataset.abort()

            throw ThingException("Update Thing Error: ${e.message}")
        } finally {
            rdfDataset.end()
        }
    }

    fun patchThing(td: ObjectNode, id: String): String {
        /*
        val id: String = td.get("@id")?.takeIf { it.isTextual }?.asText()
            ?: td.get("id")?.takeIf { it.isTextual }?.asText()
            ?: throw BadRequestException("Invalid or missing @id field in the JSON body.")
         */

        rdfDataset.begin(ReadWrite.WRITE)

        var query = ""

        try {
            val graphId = Utils.strconcat(GRAPH_PREFIX, id)
            val thing = retrieveThingById(id)

            if (thing != null) {
                thing.setAll<ObjectNode>(td)

                removeEmptyProperties(thing)

                decorateThingDescription(thing)

                val jsonRdfModel = converter.convertJsonLdToRdf(thing.toString(), Lang.JSONLD11)

                //  Performing Syntactic Validation
                val syntacticValidationFailures = validator.validateSyntactic(jsonRdfModel, xmlShapesModel)

                if (syntacticValidationFailures.isNotEmpty()) {
                    val validationErrors = syntacticValidationFailures.map {
                        ValidationError(
                            "Syntactic Validation",
                            it)
                    }
                    throw ValidationException(validationErrors, "Syntactic Validation Failed")
                }

                //  Performing Semantic Validation

                val semanticValidationFailures = validator.validateSemantic(jsonRdfModel, ttlContextModel)

                if (semanticValidationFailures.isNotEmpty()) {
                    val validationErrors = semanticValidationFailures.map {
                        ValidationError(
                            "Semantic Validation",
                            it)
                    }
                    throw ValidationException(validationErrors, "Semantic Validation Failed")
                }

                // Query preparation for RDF data storing
                val rdfTriplesString = converter.toString(jsonRdfModel)
                query = """
                DELETE WHERE {
                    GRAPH <$graphId> {
                        ?s ?p ?o
                    }
                };
                INSERT DATA {
                    GRAPH <$graphId> {
                        $rdfTriplesString
                    }
                }
            """.trimIndent()

                println("query:\n$query")

                //  Execute query
                val update = UpdateFactory.create(query)
                val updateExecution: UpdateProcessor = UpdateExecutionFactory.create(update, rdfDataset)
                updateExecution.execute()

                refreshJsonDbItem(graphId)

                //  Commit to close db connection
                rdfDataset.commit()

                return id
            } else {
                throw ThingException("Thing with id: $graphId does not exists.")
            }
        } catch (e: ThingException) {
            rdfDataset.abort()

            throw ThingException("An error occurred while patching the thing: ${e.message}")
        } catch (e: ValidationException) {
            rdfDataset.abort()

            throw e
        } catch (e: Exception) {
            rdfDataset.abort()

            throw ThingException("Patch Thing Error: ${e.message}")
        } finally {
            rdfDataset.end()
        }
    }

    fun deleteThingById(id: String) {
        rdfDataset.begin(ReadWrite.WRITE)

        try {
            val graphId = Utils.strconcat(GRAPH_PREFIX, id)
            val deleteQuery = "DELETE WHERE { GRAPH <$graphId> { ?s ?p ?o } }"

            println("query: $deleteQuery")

            val deleteUpdate = UpdateFactory.create(deleteQuery)
            val deleteExecution: UpdateProcessor = UpdateExecutionFactory.create(deleteUpdate, rdfDataset)
            deleteExecution.execute()

            refreshJsonDbItem(graphId)

            //  Commit to close db connection
            rdfDataset.commit()

        } catch (e: Exception) {
            rdfDataset.abort()

            throw ThingException("An error occurred while Deleting the thing: ${e.message}")
        } finally {
            rdfDataset.end()
        }
    }

    fun retrieveThingById(id: String): ObjectNode? {
        try {
            val graphId = Utils.strconcat(GRAPH_PREFIX, id)
            return thingsMap[graphId]
        } catch (e: Exception){
            println("${e.message}")
            throw ThingException("Retrieve Get: ${e.message}")
        }
    }

    fun checkIfThingExists(id: String): Boolean {
        try {
            val graphId = Utils.strconcat(GRAPH_PREFIX, id)
            return thingsMap.containsKey(graphId)
        } catch (e: Exception){
            println("${e.message}")
            throw ThingException("Retrieve Head: ${e.message}")
        }
    }

    fun retrieveAllThings(): List<ObjectNode> {
        try {
            return thingsMap.values.toList()
        } catch (e: Exception){
            println("${e.message}")
            throw ThingException("Retrieve All: ${e.message}")
        }
    }

    private fun decorateThingDescription(td: ObjectNode) {
        if (!td.has("@type")) {
            val typeArray = td.putArray("@type")
            typeArray.add("Thing")
        } else if (!Utils.hasThingType(td)) {
            val typeArray = td.withArray("@type")
            typeArray.add("Thing")
        }

        //  Add registration info: "created" if not already existing
        val registrationInfo: ObjectNode = if (!td.has("registration")) {
            Utils.jsonMapper.createObjectNode().put("created", Instant.now().toString())
        } else {
            td["registration"] as ObjectNode
        }

        //  Add registration info: "modified" with the current timestamp
        registrationInfo.put("modified", Instant.now().toString())
        td.set<ObjectNode>("registration", registrationInfo)
    }

    private fun removeEmptyProperties(objectNode: ObjectNode) {
        val fieldsToRemove = mutableListOf<String>()

        objectNode.fields().forEach { (fieldName, fieldValue)  ->
            if (fieldValue.isNull) {
                fieldsToRemove.add(fieldName)
            } else if (fieldValue.isObject) {
                removeEmptyProperties(fieldValue as ObjectNode) //  Recursive call for nested objects
            }
        }

        fieldsToRemove.forEach {
            objectNode.remove(it)
        }
    }
}