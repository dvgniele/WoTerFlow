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
import utils.RDFConverter
import utils.Utils
import wot.directory.DirectoryConfig
import wot.search.sparql.SparqlService
import java.time.Instant
import java.util.*


class ThingDescriptionService(dbRdf: Dataset, private val thingsMap: MutableMap<String, ObjectNode>) {
    private val rdfDataset: Dataset = dbRdf

    private var ttlContextModel: Model = Utils.loadModel(DirectoryConfig.ttlContextLocal, Lang.TURTLE)
    private var xmlShapesModel: Model = Utils.loadModel(DirectoryConfig.xmlShapesLocal, Lang.RDFXML)

    private val converter = RDFConverter()

    private fun refreshJsonDbItem(graphId: String) {
        try {
            val ttlModel = Utils.loadRDFModelById(rdfDataset, graphId)

            if (!ttlModel.isEmpty){
                val objNode = converter.fromRdf(ttlModel)
                val thing = converter.toJsonLd11(Utils.toJson(objNode.toString()))

                thingsMap[graphId] = thing
            } else {
                thingsMap.remove(graphId)
            }
        } catch (e: Exception) {
            throw ThingException("Error refreshing the JsonDb item with id: $graphId: ${e.message}")
        }
    }

    fun refreshJsonDb() {
        rdfDataset.begin(TxnType.READ)

        try {
            val ttlList = Utils.loadRDFDatasetIntoModelList(rdfDataset)
            val things = ttlList.map { converter.toJsonLd11(converter.fromRdf(it)) }

            //  Clear the things map and populate it back with the updated dataset
            thingsMap.clear()

            things.forEach { thing ->
                thing["id"]?.asText()?.let { id ->
                    thingsMap[Utils.strconcat(DirectoryConfig.GRAPH_PREFIX, id)] = thing
                }
            }

            println("\t###\tREADY\t###")
        } catch (e: Exception) {
            throw ThingException("Error refreshing the JsonDb: ${e.message}")
        } finally {
            rdfDataset.end()
        }
    }

    fun insertAnonymousThing(td: ObjectNode): Pair<String, ObjectNode> {
        rdfDataset.begin(TxnType.WRITE)

        var query = ""

        var uuid = UUID.randomUUID().toString()
        var id = Utils.strconcat("urn:uuid:", uuid)
        var graphId = Utils.strconcat(DirectoryConfig.GRAPH_PREFIX, id)

        try {
            while (Utils.idExists(thingsMap.keys, graphId)) {
                uuid = UUID.randomUUID().toString()
                id = Utils.strconcat("urn:uuid:", uuid)
                graphId = Utils.strconcat(DirectoryConfig.GRAPH_PREFIX, id)
            }

            td.put("@id", id)

            // Checking the jsonld version and upgrading if needed
            val tdVersion11p = Utils.isJsonLd11OrGreater(td)

            val tdV11 = if (!tdVersion11p) converter.toJsonLd11(td) else td

            // JsonLd decoration with missing fields
            decorateThingDescription(tdV11)

            val jsonRdfModel = converter.toRdf(tdV11.toString(), Lang.JSONLD11)

            //  Performing Syntactic Validation
            val syntacticValidationFailures =
                ThingDescriptionValidation.validateSyntactic(jsonRdfModel, xmlShapesModel)

            if (syntacticValidationFailures.isNotEmpty()) {
                val validationErrors = syntacticValidationFailures.map {
                    ValidationError(
                        "Syntactic Validation",
                        it
                    )
                }
                throw ValidationException(validationErrors, "Syntactic Validation Failed")
            }

            //  Performing Semantic Validation
            val semanticValidationFailures =
                ThingDescriptionValidation.validateSemantic(jsonRdfModel, ttlContextModel)

            if (semanticValidationFailures.isNotEmpty()) {
                val validationErrors = semanticValidationFailures.map {
                    ValidationError(
                        "Semantic Validation",
                        it
                    )
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

            //  Execute query
            SparqlService.update(query, rdfDataset)

            synchronized(this) {
                //  Commit to close db connection
                rdfDataset.commit()
            }

            return Pair(id, tdV11)

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

            rdfDataset.begin(TxnType.READ)
            refreshJsonDbItem(graphId)
            rdfDataset.end()
        }
    }

    fun updateThing(td: ObjectNode): Pair<String, Boolean> {
        val id: String = td.get("@id")?.takeIf { it.isTextual }?.asText()
            ?: td.get("id")?.takeIf { it.isTextual }?.asText()
            ?: throw BadRequestException("Invalid or missing @id field in the JSON body.")

        rdfDataset.begin(TxnType.WRITE)

        var query = ""
        var existsAlready = false
        val graphId = Utils.strconcat(DirectoryConfig.GRAPH_PREFIX, id)

        try {
            existsAlready = checkIfThingExists(id)

            val tdVersion11p = Utils.isJsonLd11OrGreater(td)
            val tdV11 = if (!tdVersion11p) converter.toJsonLd11(td) else td

            decorateThingDescription(tdV11)

            val jsonRdfModel = converter.toRdf(tdV11.toPrettyString(), Lang.JSONLD11)

            //  Performing Syntactic Validation
            val syntacticValidationFailures =
                ThingDescriptionValidation.validateSyntactic(jsonRdfModel, xmlShapesModel)

            if (syntacticValidationFailures.isNotEmpty()) {
                val validationErrors = syntacticValidationFailures.map {
                    ValidationError(
                        "Syntactic Validation",
                        it
                    )
                }
                throw ValidationException(validationErrors, "Syntactic Validation Failed")
            }

            //  Performing Semantic Validation
            val semanticValidationFailures =
                ThingDescriptionValidation.validateSemantic(jsonRdfModel, ttlContextModel)

            if (semanticValidationFailures.isNotEmpty()) {
                val validationErrors = semanticValidationFailures.map {
                    ValidationError(
                        "Semantic Validation",
                        it
                    )
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

            //  Execute query
            val update = UpdateFactory.create(query)
            val updateExecution: UpdateProcessor = UpdateExecutionFactory.create(update, rdfDataset)
            updateExecution.execute()

            synchronized(this) {
                //  Commit to close db connection
                rdfDataset.commit()
            }

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

            rdfDataset.begin(TxnType.READ)
            refreshJsonDbItem(graphId)
            rdfDataset.end()
        }
    }

    fun patchThing(td: ObjectNode, id: String): String {
        rdfDataset.begin(TxnType.WRITE)

        var query = ""
        val graphId = Utils.strconcat(DirectoryConfig.GRAPH_PREFIX, id)

        try {
            val thing = retrieveThingById(id)

            if (thing != null) {
                thing.setAll<ObjectNode>(td)

                removeEmptyProperties(thing)

                decorateThingDescription(thing)

                val jsonRdfModel = converter.toRdf(thing.toString(), Lang.JSONLD11)

                //  Performing Syntactic Validation
                val syntacticValidationFailures =
                    ThingDescriptionValidation.validateSyntactic(jsonRdfModel, xmlShapesModel)

                if (syntacticValidationFailures.isNotEmpty()) {
                    val validationErrors = syntacticValidationFailures.map {
                        ValidationError(
                            "Syntactic Validation",
                            it
                        )
                    }
                    throw ValidationException(validationErrors, "Syntactic Validation Failed")
                }

                //  Performing Semantic Validation
                val semanticValidationFailures =
                    ThingDescriptionValidation.validateSemantic(jsonRdfModel, ttlContextModel)

                if (semanticValidationFailures.isNotEmpty()) {
                    val validationErrors = semanticValidationFailures.map {
                        ValidationError(
                            "Semantic Validation",
                            it
                        )
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

                //  Execute query
                val update = UpdateFactory.create(query)
                val updateExecution: UpdateProcessor = UpdateExecutionFactory.create(update, rdfDataset)
                updateExecution.execute()

                synchronized(this) {
                    //  Commit to close db connection
                    rdfDataset.commit()
                }

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

            rdfDataset.begin(TxnType.READ)
            refreshJsonDbItem(id)
            rdfDataset.end()
        }
    }

    fun deleteThingById(id: String) {
        rdfDataset.begin(TxnType.WRITE)
        val graphId = Utils.strconcat(DirectoryConfig.GRAPH_PREFIX, id)

        try {
            val deleteQuery = "DELETE WHERE { GRAPH <$graphId> { ?s ?p ?o } }"

            val deleteUpdate = UpdateFactory.create(deleteQuery)
            val deleteExecution: UpdateProcessor = UpdateExecutionFactory.create(deleteUpdate, rdfDataset)
            deleteExecution.execute()

            synchronized(this) {
                //  Commit to close db connection
                rdfDataset.commit()
            }
        } catch (e: Exception) {
            rdfDataset.abort()

            throw ThingException("An error occurred while Deleting the thing: ${e.message}")
        } finally {
            rdfDataset.end()

            rdfDataset.begin(TxnType.READ)
            refreshJsonDbItem(graphId)
            rdfDataset.end()
        }
    }

    fun retrieveThingById(id: String): ObjectNode? {
        try {
            val graphId = Utils.strconcat(DirectoryConfig.GRAPH_PREFIX, id)
            return thingsMap[graphId]
        } catch (e: Exception){
            throw ThingException("Retrieve Get: ${e.message}")
        }
    }

    fun checkIfThingExists(id: String): Boolean {
        try {
            val graphId = Utils.strconcat(DirectoryConfig.GRAPH_PREFIX, id)
            return thingsMap.containsKey(graphId)
        } catch (e: Exception){
            throw ThingException("Retrieve Head: ${e.message}")
        }
    }

    fun retrieveAllThings(): List<ObjectNode> {
        try {
            return thingsMap.values.toList()
        } catch (e: Exception){
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