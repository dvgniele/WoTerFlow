package wot.td

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.jsonldjava.core.JsonLdOptions
import com.github.jsonldjava.core.JsonLdProcessor
import com.github.jsonldjava.utils.JsonUtils
import exceptions.ThingException
import io.ktor.server.plugins.*
import org.apache.commons.io.output.ByteArrayOutputStream
import org.apache.jena.query.*
import org.apache.jena.rdf.model.*
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.apache.jena.update.UpdateProcessor
import org.mapdb.DB
import utils.RDFConverter
import utils.Utils
import java.io.StringReader
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.*
import kotlin.collections.ArrayList


class ThingDescriptionService(dbRdf: Dataset, dbJson: DB?) {

    private val utils: Utils = Utils()
    private val converter: RDFConverter = RDFConverter()
    private val validator: ThingDescriptionValidation = ThingDescriptionValidation()

    private val BASE_URI = "http://example.com/ktwot/"

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
    private val jsonContextUrl11 = "https://www.w3.org/2022/wot/td/v1.1"
    val jsonLdContextResourcePath = "data/validation/td.jsonld"
    private val ttlContextResourcePath = "data/validation/td.ttl"

    private var jsonLdContextModel: Model = utils.loadModel(jsonLdContextResourcePath, Lang.JSONLD11)
    private var ttlContextModel: Model = utils.loadModel(ttlContextResourcePath, Lang.TURTLE)

    init {
        refreshJsonDb()
    }
    private fun refreshJsonDb() {
        rdfDataset.begin(ReadWrite.READ)

        try {
            val ttlList = utils.loadRDFDatasetIntoModelList(rdfDataset)
            val things = ttlList.map { converter.convertRdfModelToObjectNode(it) }

            //  Clear the things map and populate it back with the updated dataset
            thingsMap.clear()
            things.forEach { thing ->
                thing["@id"]?.asText()?.let { id ->
                    thingsMap[id] = thing
                }
            }
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
        var id = utils.strconcat("urn:uuid:", uuid)

        try {
            while (utils.idExists(thingsMap.keys, id)){
                uuid = UUID.randomUUID().toString()
                id = utils.strconcat("urn:uuid:", uuid)
            }

            td.put("@id", id)
            val graphId = utils.strconcat(GRAPH_PREFIX, id)

            println(td.toPrettyString())


            // Checking the jsonld version and upgrading if needed
            val tdVersion11p = utils.isJsonLd11OrGreater(td)

            val tdV11 = if (!tdVersion11p) converter.convertJsonLd10ToJsonLd11(td) else td
            //val tdV11 = td

            // JsonLd decoration with missing fields
            decorateThingDescription(tdV11)


            val jsonRdfModel = converter.convertJsonLdToRdf(tdV11.toPrettyString(), Lang.JSONLD11)
            val jsonRdfModelString = converter.convertRdfToStringSerialization(jsonRdfModel, Lang.JSONLD11)

            val thingTurtleString = converter.convertRdfToStringSerialization(jsonRdfModel, Lang.TURTLE)
            val turtleModel = converter.convertRdfStringToRdf(thingTurtleString, Lang.TURTLE)

            //println("ttl model:\n$turtleModel")

            //  Performing Semantic Validation
            val semanticValidation = validator.validateSemantic(jsonRdfModel, jsonLdContextModel)
            println("Semantic Validation: $semanticValidation")

            if (!semanticValidation)
                throw ThingException("Semantic Validation Failed")

            //  Performing Syntactic Validation
            val syntacticValidation = validator.validateSyntactic(turtleModel, ttlContextModel)
            println("Syntactic Validation: $syntacticValidation")

            if (!syntacticValidation)
                throw ThingException("Syntactic Validation Failed")

            // Query preparation for RDF data storing
            val rdfTriplesString = thingTurtleString
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

            //  Commit to close db connection
            rdfDataset.commit()

            refreshJsonDb()

            return uuid
        } catch (e: ThingException) {
            rdfDataset.abort()

            throw ThingException("An error occurred while storing the thing: ${e.message}")
        } catch (e: Exception) {
            rdfDataset.abort()

            throw ThingException("Create new Thing Error: ${e.message}\nquery:\n$query")
        } finally {
            rdfDataset.end()
        }
    }

    fun updateThing(td: ObjectNode): String {
        val id: String = td.get("@id")?.takeIf { it.isTextual }?.asText()
            ?: td.get("id")?.takeIf { it.isTextual }?.asText()
            ?: throw BadRequestException("Invalid or missing @id field in the JSON body.")

        rdfDataset.begin(ReadWrite.WRITE)

        var query = ""

        try {
            val graphId = utils.strconcat(GRAPH_PREFIX, id)

            val tdVersion11p = utils.isJsonLd11OrGreater(td)
            val tdV11 = if (!tdVersion11p) converter.convertJsonLd10ToJsonLd11(td) else td

            decorateThingDescription(tdV11)

            val jsonRdfModel = converter.convertJsonLdToRdf(tdV11.toPrettyString(), Lang.JSONLD11)
            //val jsonRdfModelString = converter.convertRdfToStringSerialization(jsonRdfModel, Lang.JSONLD11)

            val thingTurtleString = converter.convertRdfToStringSerialization(jsonRdfModel, Lang.TURTLE)
            val tutleModel = converter.convertRdfStringToRdf(thingTurtleString, Lang.TURTLE)


            //  Performing Semantic Validation
            val semanticValidation = validator.validateSemantic(jsonRdfModel, jsonLdContextModel)
            println("Semantic Validation: $semanticValidation")

            if (!semanticValidation)
                throw ThingException("Semantic Validation Failed")

            //  Performing Syntactic Validation
            val syntacticValidation = validator.validateSyntactic(tutleModel, ttlContextModel)
            println("Syntactic Validation: $syntacticValidation")

            if (!syntacticValidation)
                throw ThingException("Syntactic Validation Failed")

            // Query preparation for RDF data storing
            val rdfTriplesString = thingTurtleString
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

            //  Commit to close db connection
            rdfDataset.commit()

            refreshJsonDb()

            return id
        } catch (e: ThingException) {
            rdfDataset.abort()

            throw ThingException("An error occurred while updating the thing: ${e.message}")
        } catch (e: Exception) {
            rdfDataset.abort()

            throw ThingException("Update Thing Error: ${e.message}")
        } finally {
            rdfDataset.end()
        }
    }

    fun patchThing(td: ObjectNode): String {
        val id: String = td.get("@id")?.takeIf { it.isTextual }?.asText()
            ?: td.get("id")?.takeIf { it.isTextual }?.asText()
            ?: throw BadRequestException("Invalid or missing @id field in the JSON body.")

        rdfDataset.begin(ReadWrite.WRITE)

        var query = ""

        try {
            val thing = retrieveThingById(id)
            val graphId = utils.strconcat(GRAPH_PREFIX, td.get("@id").asText())

            if (thing != null) {
                thing.setAll<ObjectNode>(td)

                val jsonRdfModel = converter.convertJsonLdToRdf(thing.toPrettyString(), Lang.JSONLD11)
                //val jsonRdfModelString = converter.convertRdfToStringSerialization(jsonRdfModel, Lang.JSONLD11)

                val thingTurtleString = converter.convertRdfToStringSerialization(jsonRdfModel, Lang.TURTLE)
                val tutleModel = converter.convertRdfStringToRdf(thingTurtleString, Lang.TURTLE)

                //  Performing Semantic Validation
                val semanticValidation = validator.validateSemantic(jsonRdfModel, jsonLdContextModel)
                println("Semantic Validation: $semanticValidation")

                if (!semanticValidation)
                    throw ThingException("Semantic Validation Failed")

                //  Performing Syntactic Validation
                val syntacticValidation = validator.validateSyntactic(tutleModel, ttlContextModel)
                println("Syntactic Validation: $syntacticValidation")

                if (!syntacticValidation)
                    throw ThingException("Syntactic Validation Failed")

                // Query preparation for RDF data storing
                val rdfTriplesString = thingTurtleString
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

                //  Commit to close db connection
                rdfDataset.commit()

                refreshJsonDb()

                return id
            } else {
                throw ThingException("Thing with id: $graphId does not exists.")
            }
        } catch (e: ThingException) {
            rdfDataset.abort()

            throw ThingException("An error occurred while patching the thing: ${e.message}")
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
            val graphId = utils.strconcat(GRAPH_PREFIX, id)
            val deleteQuery = "DELETE WHERE { GRAPH <$graphId> { ?s ?p ?o } }"

            val deleteUpdate = UpdateFactory.create(deleteQuery)
            val deleteExecution: UpdateProcessor = UpdateExecutionFactory.create(deleteUpdate, rdfDataset)
            deleteExecution.execute()

            //  Commit to close db connection
            rdfDataset.commit()

            refreshJsonDb()
        } catch (e: Exception) {
            rdfDataset.abort()

            throw ThingException("An error occurred while Deleting the thing: ${e.message}")
        } finally {
            rdfDataset.end()
        }
    }

    fun retrieveThingById(id: String): ObjectNode? {
        try {
            return thingsMap[id]
        } catch (e: Exception){
            println("${e.message}")
            throw ThingException("Retrieve Get: ${e.message}")
        }
    }

    fun checkIfThingExists(id: String): Boolean {
        try {
            return thingsMap.containsKey(id)
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
        } else if (!utils.hasThingType(td)) {
            val typeArray = td.withArray("@type")
            typeArray.add("Thing")
        }

        //  Add registration info: "created" if not already existing
        val registrationInfo: ObjectNode = if (!td.has("registration")) {
            utils.jsonMapper.createObjectNode().put("created", Instant.now().toString())
        } else {
            td["registration"] as ObjectNode
        }

        //  Add registration info: "modified" with the current timestamp
        registrationInfo.put("modified", Instant.now().toString())
        td.set<ObjectNode>("registration", registrationInfo)
    }

}