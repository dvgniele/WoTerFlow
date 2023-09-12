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
import utils.Utils
import java.io.StringReader
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.*
import kotlin.collections.ArrayList


class ThingDescriptionService(dbRdf: Dataset, dbJson: DB?) {

    private val utils: Utils = Utils()
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
            val things = ttlList.map { convertRdfModelToObjectNode(it) }

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

            val tdV11 = if (!tdVersion11p) convertJsonLd10ToJsonLd11(td) else td
            //val tdV11 = td

            // JsonLd decoration with missing fields
            decorateThingDescription(tdV11)


            val jsonRdfModel = convertJsonLdToRdf(tdV11.toPrettyString(), Lang.JSONLD11)
            val jsonRdfModelString = convertRdfToStringSerialization(jsonRdfModel, Lang.JSONLD11)

            val thingTurtleString = convertRdfToStringSerialization(jsonRdfModel, Lang.TURTLE)
            val turtleModel = convertRdfStringToRdf(thingTurtleString, Lang.TURTLE)

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
            val tdV11 = if (!tdVersion11p) convertJsonLd10ToJsonLd11(td) else td

            decorateThingDescription(tdV11)

            val jsonRdfModel = convertJsonLdToRdf(tdV11.toPrettyString(), Lang.JSONLD11)
            val jsonRdfModelString = convertRdfToStringSerialization(jsonRdfModel, Lang.JSONLD11)

            val thingTurtleString = convertRdfToStringSerialization(jsonRdfModel, Lang.TURTLE)
            val tutleModel = convertRdfStringToRdf(thingTurtleString, Lang.TURTLE)


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


    //  todo: all patch logic
    fun patchThing(td: ObjectNode): String {
        rdfDataset.begin(ReadWrite.READ)

        try {
            val ttlList = utils.loadRDFDatasetIntoModelList(rdfDataset)
            val objectNodeList = ttlList.map { convertRdfModelToObjectNode(it) }

            rdfDataset.commit()

            return "oke"
        } catch (e: Exception) {
            rdfDataset.abort()

            throw ThingException("An error occurred while Patching the thing: ${e.message}")
        } finally {
            rdfDataset.end()
        }
    }

    fun retrieveThingById(id: String): ObjectNode? {
        return thingsMap[id]
    }

    fun retrieveAllThings(): List<ObjectNode> {
        return thingsMap.values.toList()
    }

    private fun convertRdfModelToObjectNode(rdfModelString: Model): ObjectNode {
        try {
            val serializedModel = convertRdfToStringSerialization(rdfModelString, Lang.TURTLE)

            return convertTurtleToJsonLd(serializedModel)
        } catch (e: Exception) {
            throw ThingException("Error converting RDF Model String to ObjectNode: ${e.message}")
        }
    }

    private fun convertJsonLd10ToJsonLd11(jsonLd10: ObjectNode): ObjectNode {
        try {
            val jsonLd11 = ObjectMapper().createObjectNode()

            // Copy the existing fields from JSON-LD 1.0 to JSON-LD 1.1
            jsonLd10.fieldNames().forEach { fieldName ->
                val fieldValue: JsonNode? = jsonLd10.get(fieldName)
                jsonLd11.set<JsonNode>(fieldName, fieldValue)
            }

            // Add "@version" required field for JSON-LD 1.1
            jsonLd11.put("@version", "1.1")

            return jsonLd11
        } catch (e: Exception) {
            throw Exception("Error converting JSON-LD 1.0 to JSON-LD 1.1: ${e.message}")
        }
    }

    private fun convertRdfToString(model: Model, lang: Lang): String {
        try {
            val outputStream = ByteArrayOutputStream()
            RDFDataMgr.write(outputStream, model, lang)
            return outputStream.toString(StandardCharsets.UTF_8)
        } catch (e: Exception) {
            throw Exception("Error converting RDF to string: ${e.message}")
        }
    }

    fun convertTurtleToJsonLd(turtleData: String): ObjectNode {
        try {
            val model = ModelFactory.createDefaultModel()
            val rdfDataReader = StringReader(turtleData)
            RDFDataMgr.read(model, rdfDataReader, null, Lang.TURTLE)

            val jsonLdContext = "https://www.w3.org/2019/wot/td/v1"

            val jsonLdObject = JsonNodeFactory.instance.objectNode()
            jsonLdObject.put("@context", jsonLdContext)

            //  Create a map to store the subject blocks
            val subjectBlocks = mutableMapOf<String, ObjectNode>()

            //  Iterate over the statements of the RDF model
            val stmtIterator = model.listStatements()
            while (stmtIterator.hasNext()) {
                val stmt = stmtIterator.nextStatement()
                val subjectURI = stmt.subject.toString()
                val predicateURI = stmt.predicate.toString()
                val objectNode = stmt.`object`

                //  Get or create the subject block
                val subjectBlock = subjectBlocks.getOrPut(subjectURI) {
                    JsonNodeFactory.instance.objectNode()
                }

                //  Add the predicate and object to the subject block
                addProperty(subjectBlock, predicateURI, objectNode)
            }

            //  Find the subject with "urn:" and add it as a top-level block with @id
            val urnSubject = subjectBlocks.keys.find { it.startsWith("urn:") }
            if (urnSubject != null) {
                val topLevelBlock = subjectBlocks[urnSubject]
                topLevelBlock?.put("@id", urnSubject)
                jsonLdObject.setAll<ObjectNode>(topLevelBlock)
            }

            return jsonLdObject
        } catch (e: Exception) {
            throw Exception("Error converting Turtle to JSON-LD: ${e.message}")
        }
    }


    fun addProperty(node: ObjectNode, predicateURI: String, objectNode: RDFNode) {
        val predicateName = predicateURI.substringAfterLast("#")

        if (predicateName.startsWith("@")) {
            //  Handle predicates starting with "@", e.g., "@type"
            node.put(predicateName, objectNode.toString())
        } else {
            //  Handle other predicates
            if (objectNode.isLiteral) {
                // Handle literal values
                val literalValue = objectNode.asLiteral().string
                node.put(predicateName, literalValue)
            } else if (objectNode.isResource) {
                //  Handle resource values
                val resourceURI = objectNode.asResource().uri

                //  handle the object if represents a collection
                if (objectNode.isAnon) {

                    val collectionNode = handleCollection(objectNode.asResource())
                    if (node.has(predicateName)) {

                        val existingValue = node.get(predicateName)
                        if (existingValue is ArrayNode) {
                            existingValue.add(collectionNode)
                        } else {
                            val arrayNode = JsonNodeFactory.instance.arrayNode()
                            arrayNode.add(existingValue)
                            arrayNode.add(collectionNode)
                            node.set(predicateName, arrayNode)
                        }
                    } else {
                        node.set(predicateName, collectionNode)
                    }

                }
                else {
                    node.put(utils.strconcat("@", predicateName), resourceURI)
                }
            }
        }
    }

    fun handleCollection(collection: Resource): ObjectNode {
        val collectionNode = JsonNodeFactory.instance.objectNode()
        val iterator = collection.listProperties()
        while (iterator.hasNext()) {
            val arrayElement = iterator.next()
            val predicateName = arrayElement.predicate.toString()
                .substringAfterLast("/")
                .substringAfterLast("#")
            val objectValue = arrayElement.`object`.toString()
            collectionNode.put(predicateName, objectValue)
        }
        return collectionNode
    }

    private fun convertToTtl(stringModel: String): Model {
        try {
            val model = ModelFactory.createDefaultModel()

            val rdfDataReader = StringReader(stringModel)
            model.read(rdfDataReader, null, Lang.TURTLE.name)

            return model
        }
        catch (e: Exception) {
            throw Exception("Error converting to TURTLE: ${e.message}")
        }
    }

    private fun convertJsonLdToRdf(jsonLdString: String, lang: Lang): Model {
        try {
            val jsonLdNode = JsonUtils.fromString(jsonLdString)

            val options = JsonLdOptions()

            // Set options to include the @context dynamically if present in the JSON-LD
            if ((jsonLdNode is Map<*, *>) && jsonLdNode.containsKey("@context")) {
                val context = jsonLdNode["@context"]
                if (context is String) {
                    // Create a HashMap and set the context
                    val contextMap = HashMap<String, Any>()
                    contextMap["@context"] = context
                    options.expandContext = contextMap
                } else if (context is Map<*, *>) {
                    options.expandContext = context
                }
            }

            val expandedJsonLd = JsonLdProcessor.expand(jsonLdNode, options)

            val model = ModelFactory.createDefaultModel()

            // Convert the expanded JSON-LD to RDF
            val jsonLdStringReader = StringReader(JsonUtils.toPrettyString(expandedJsonLd))
            RDFDataMgr.read(model, jsonLdStringReader, null, lang)

            return model
        } catch (e: Exception) {
            throw Exception("Error converting JSON-LD to ${lang.name.uppercase()}: ${e.message}")
        }
    }

    fun convertRdfStringToRdf(jsonLdString: String, lang: Lang): Model {
        try {
            val model = ModelFactory.createDefaultModel()

            RDFDataMgr.read(model, jsonLdString.reader(), null, lang)

            return model
        } catch (e: Exception){
            throw Exception("Error converting JSON-LD to ${lang.name.uppercase()}: ${e.message}")
        }
    }

    fun convertTrigToJsonLd(trigModel: Model): String {
        try {
            val writer = StringWriter()

            // Serialize the TRIG model to JSON-LD 1.1 format
            RDFDataMgr.write(writer, trigModel, Lang.JSONLD11)

            return writer.toString()
        } catch (e: Exception) {
            throw ThingException("An error occurred during conversion from TRIG to JSON-LD 1.1: ${e.message}")
        }
    }

    fun convertRdfToStringSerialization(model: Model, lang: Lang): String {
        try {
            val outputStream = ByteArrayOutputStream()

            RDFDataMgr.write(outputStream, model, lang)

            return  outputStream.toString(StandardCharsets.UTF_8)
        } catch (e: Exception) {
            throw Exception("Error converting RDF to String Serialization: ${e.message}")
        }
    }

    fun convertJsonLdToJsonObject(jsonLdString: String): ObjectNode {
        try {
            val objectMapper = ObjectMapper()

            // Deserialize the JSON-LD string into an ObjectNode
            return objectMapper.readValue(jsonLdString, ObjectNode::class.java)
        } catch (e: Exception) {
            throw ThingException("Error converting JSON-LD to JSON-LD object: ${e.message}")
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