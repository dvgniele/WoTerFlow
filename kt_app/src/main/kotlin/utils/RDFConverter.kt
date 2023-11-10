package utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import exceptions.ConversionException
import org.apache.commons.io.output.ByteArrayOutputStream
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import com.apicatalog.jsonld.JsonLd
import com.apicatalog.jsonld.JsonLdError
import com.apicatalog.jsonld.JsonLdOptions
import com.apicatalog.jsonld.JsonLdVersion
import com.apicatalog.jsonld.document.JsonDocument
import com.apicatalog.jsonld.document.RdfDocument
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.kotlin.readValue
import exceptions.ThingException
import jakarta.json.JsonObject
import wot.directory.DirectoryConfig.Companion.contextV10
import wot.directory.DirectoryConfig.Companion.contextV11
import java.io.StringReader
import java.io.StringWriter
import java.net.URI
import java.nio.charset.StandardCharsets

class RDFConverter {

    val objectMapper = ObjectMapper()

    val contextV10data: String = Utils.downloadFileAsString(contextV10)
    val contextV11data: String = Utils.downloadFileAsString(contextV11)

    var contextV10Document: JsonDocument = JsonDocument.of(contextV10data.reader())
    var contextV11Document: JsonDocument = JsonDocument.of(contextV11data.reader())

    val options11 = JsonLdOptions()

    init {
        val documentCache = mutableMapOf<String, String>()
        documentCache[contextV11] = contextV11data

        options11.documentLoader = CachedDocumentLoader(documentCache)
        //options11.expandContext = contextV11Document
        options11.base = URI(contextV11)
        options11.processingMode = JsonLdVersion.V1_1
        options11.isExplicit = false
        options11.isCompactArrays = true
        options11.isCompactToRelative = true
    }


    fun toJsonLd11(thing: ObjectNode): ObjectNode {
        try {
            if (thing.has("securityDefinitions")) {
                val securityDefinitions = thing.get("securityDefinitions")
                val updatedSecurityDefinitions = JsonNodeFactory.instance.objectNode()

                securityDefinitions.fieldNames().forEach { fieldName ->
                    val securityDefinition = securityDefinitions.get(fieldName)
                    if (fieldName == "@none") {
                        val scheme = securityDefinition.get("scheme").textValue()
                        val newNode = JsonNodeFactory.instance.objectNode()
                        newNode.put("scheme", scheme)
                        updatedSecurityDefinitions.put("nosec_sc", newNode)
                    } else {
                        if (securityDefinition.has("td:scheme")) {
                            val scheme = securityDefinition.get("td:scheme")
                            (securityDefinition as ObjectNode).put("scheme", scheme.textValue())
                            securityDefinition.remove("td:scheme")
                        }
                        updatedSecurityDefinitions.put(fieldName, securityDefinition)
                    }
                }

                thing.put("securityDefinitions", updatedSecurityDefinitions)
            }

            if (thing.has("hasSecurityConfiguration")) {
                thing.set<ObjectNode>("security", thing.remove("hasSecurityConfiguration"))
            }
            if (thing.has("security") && thing.get("security") !is ArrayNode) {
                val security = objectMapper.createArrayNode()
                security.add(thing.remove("security"))
                thing.set<ArrayNode>("security", security)
            }

            val context = thing.get("@context")

            when (context) {
                is ObjectNode -> {
                    val modifiedContext = context.deepCopy()
                    if (modifiedContext.has(contextV10)) {
                        modifiedContext.put(contextV10, contextV11)
                        thing.set<ObjectNode>("@context", modifiedContext)
                    }
                }

                is ArrayNode -> {
                    val modifiedContext = context.deepCopy()
                    val elements = modifiedContext.elements()
                    var index = 0
                    while (elements.hasNext()) {
                        val element = elements.next()
                        if (element.isTextual && element.asText() == contextV10) {
                            (modifiedContext as ArrayNode).set(index, TextNode.valueOf(contextV11))
                        }
                        index++
                    }
                    thing.set<ObjectNode>("@context", modifiedContext)

                }

                is TextNode -> {
                    if (context.asText() == contextV10) {
                        thing.put("@context", contextV11)
                    }
                }

                null -> {
                    thing.put("@context", contextV11)
                }
            }

            if (thing.has("registration")) {
                val registration = thing.get("registration") as ObjectNode
                registration.remove("id")
            }

            if (thing.has("td:title"))
                thing.put("title", thing.remove("td:title"))

            thing.put("@version", "1.1")

            /*
                    if (thing.has("id")) {
                        thing.set<ObjectNode>("@id", thing.remove("id"))
                    }
        */

            // todo: check contexts

            return thing
        } catch (e: Exception) {
            throw ConversionException("Error converting to JSON-LD 1.1")
        }
    }

    fun fromRdf(rdfModel: Model): ObjectNode {
        try {
            val document = RdfDocument.of(toString(rdfModel).reader())
            val jsonArray = JsonLd.fromRdf(document).options(options11).get()
            val jsonDocument = JsonDocument.of(jsonArray.toString().reader())

            /*
                    val jsonLdDocument = JsonLd.toRdf(jsonDocument)
                        .options(options11)
                        .get()
        */

//            val serialized = groupQuads(jsonLdDocument.toList())

            val expanded = JsonLd.expand(jsonDocument).options(options11).get()
            val expandedDocument = JsonDocument.of(expanded)

            val flattened = JsonLd.flatten(expandedDocument).options(options11).get()

            val framed = JsonLd.frame(expandedDocument, contextV11Document).options(options11).get()
            //val compacted = JsonLd.compact(jsonDocument, contextV11Document).get()

            val graphed = getGraphFromModel(framed)



            return graphed!!
        } catch (e: Exception) {
            throw ConversionException("Error converting from RDF to JSON-LD: ${e.message}")
        }
    }

    private fun getGraphFromModel(jsonObject: JsonObject): ObjectNode? {
        val jsonString = jsonObject.toString()

        val node = objectMapper.readValue<ObjectNode>(jsonString)

        val graphArray = node["@graph"]
        if (graphArray != null && graphArray.isArray) {
            val nonAnonItem = graphArray.find { item ->
                item is ObjectNode && item.has("id") && !item["id"].textValue().startsWith("_:")
            }
            if (nonAnonItem is ObjectNode) {
                return nonAnonItem
            }
        }
        return null
    }

    fun toString(model: Model): String {
        try {
            val writer = StringWriter()
            model.write(writer, Lang.NTRIPLES.name, contextV11)
            return writer.toString()
        } catch (e: Exception) {
            throw ConversionException("Error converting RDF to string: ${e.message}")
        }
    }

    fun convertRdfModelToObjectNode(rdfModelString: Model): ObjectNode {
        try {
            val serializedModel = convertRdfToStringSerialization(rdfModelString, Lang.TURTLE)

            return convertTurtleToJsonLd(serializedModel)
        } catch (e: Exception) {
            throw ConversionException("Error converting RDF Model String to ObjectNode: ${e.message}")
        }
    }

    fun convertJsonLd10ToJsonLd11(jsonLd10: ObjectNode): ObjectNode {
        try {
            val jsonLd11 = ObjectMapper().createObjectNode()

            // Copy the existing fields from JSON-LD 1.0 to JSON-LD 1.1
            jsonLd10.fieldNames().forEach { fieldName ->
                val fieldValue: JsonNode? = jsonLd10.get(fieldName)
                jsonLd11.set<JsonNode>(fieldName, fieldValue)
            }

            val context = jsonLd11.get("@context")

            when (context) {
                is ObjectNode -> {
                    val modifiedContext = context.deepCopy()
                    if (modifiedContext.has(contextV10)) {
                        modifiedContext.put(contextV10, contextV11)
                        jsonLd11.set<ObjectNode>("@context", modifiedContext)
                    }
                }

                is ArrayNode -> {
                    val modifiedContext = context.deepCopy()
                    val elements = modifiedContext.elements()
                    var index = 0
                    while (elements.hasNext()) {
                        val element = elements.next()
                        if (element.isTextual && element.asText() == contextV10) {
                            (modifiedContext as ArrayNode).set(index, TextNode.valueOf(contextV11))
                        }
                        index++
                    }
                    jsonLd11.set<ObjectNode>("@context", modifiedContext)

                }

                is TextNode -> {
                    if (context.asText() == contextV10) {
                        jsonLd11.put("@context", contextV11)
                    }
                }
            }

            // Add "version" required field for JSON-LD 1.1
            jsonLd11.put("@version", "1.1")

            return jsonLd11
        } catch (e: Exception) {
            throw ConversionException("Error converting JSON-LD 1.0 to JSON-LD 1.1: ${e.message}")
        }
    }

    private fun convertRdfToString(model: Model, lang: Lang): String {
        try {
            val outputStream = ByteArrayOutputStream()
            RDFDataMgr.write(outputStream, model, lang)
            return outputStream.toString(StandardCharsets.UTF_8)
        } catch (e: Exception) {
            throw ConversionException("Error converting RDF to string: ${e.message}")
        }
    }

    private fun convertToTtl(stringModel: String): Model {
        try {
            val model = ModelFactory.createDefaultModel()

            val rdfDataReader = StringReader(stringModel)
            model.read(rdfDataReader, null, Lang.TURTLE.name)

            return model
        } catch (e: Exception) {
            throw ConversionException("Error converting to TURTLE: ${e.message}")
        }
    }

    fun convertRdfStringToRdf(jsonLdString: String, lang: Lang): Model {
        try {
            val model = ModelFactory.createDefaultModel()

            RDFDataMgr.read(model, jsonLdString.reader(), null, lang)

            return model
        } catch (e: Exception) {
            throw ConversionException("Error converting JSON-LD model to ${lang.name.uppercase()}: ${e.message}")
        }
    }

    fun convertJsonLdToJsonObject(jsonLdString: String): ObjectNode {
        try {
            // Deserialize the JSON-LD string into an ObjectNode
            return objectMapper.readValue(jsonLdString, ObjectNode::class.java)
        } catch (e: Exception) {
            throw ConversionException("Error converting JSON-LD to JSON-LD object: ${e.message}")
        }
    }

    fun convertRdfToStringSerialization(model: Model, lang: Lang): String {
        try {
            val outputStream = ByteArrayOutputStream()

            RDFDataMgr.write(outputStream, model, lang)

            return outputStream.toString(StandardCharsets.UTF_8)
        } catch (e: Exception) {
            throw ConversionException("Error converting RDF to String Serialization: ${e.message}")
        }
    }

    fun convertTrigToJsonLd(trigModel: Model): String {
        try {
            val writer = StringWriter()

            // Serialize the TRIG model to JSON-LD 1.1 format
            RDFDataMgr.write(writer, trigModel, Lang.JSONLD11)

            return writer.toString()
        } catch (e: Exception) {
            throw ConversionException("An error occurred during conversion from TRIG to JSON-LD 1.1: ${e.message}")
        }
    }

    fun convertJsonLdToRdf(jsonLdString: String, lang: Lang): Model {
        try {
            val document = JsonDocument.of(jsonLdString.reader())

            val expanded = JsonLd.expand(document).options(options11)
            val expandedDocument = expanded.get()

            val model = ModelFactory.createDefaultModel()

            for (jsonObject in expandedDocument) {
                val jsonLdStringReader = StringReader(jsonObject.toString())
                RDFDataMgr.read(model, jsonLdStringReader, null, lang)
            }

            return model
        } catch (e: JsonLdError) {
            throw ThingException("${e.message}")
        } catch (e: Exception) {
            throw ThingException("The thing must contain a @context field: ${e.message}")
        }
    }

    fun convertRdfModelToJsonLd(ttlModel: Model): ObjectNode {
        try {
            val converted = convertRdfModelToObjectNode(ttlModel)
            val document = JsonDocument.of(converted.toString().reader())

            val compactedObject = JsonLd.compact(document, contextV11Document)
                .options(options11)
                .get()

            //val contextDownload = utils.downloadFileAsString(contextV11)
            val contextJson = objectMapper.readTree(contextV11data) as ObjectNode

            val compacted = objectMapper.readTree(compactedObject.toString()) as ObjectNode

            removeContextThingFields(compacted, contextJson)

            //removeContextThingFields(objectNode, contextJson)

            compacted.put("@version", "1.1")

            if (compacted is ObjectNode)
                return compacted
            else
                throw ConversionException("Error converting RDF to JSON-LD: Unexpected JSON structure")
        } catch (e: Exception) {
            throw Exception("Error converting RDF to JSON-LD: ${e.message}")
        }
    }

    fun removeContextThingFields(objectNode: ObjectNode, context: ObjectNode) {
        val objCtx = objectNode.get("@context") as ObjectNode
        val ctx = context.get("@context")

        val fieldNamesToRemove = mutableListOf<String>()
        objCtx.fieldNames().forEach { fieldName ->
            if (ctx.has(fieldName)) {
                fieldNamesToRemove.add(fieldName)
            }
        }

        fieldNamesToRemove.forEach {
            objCtx.remove(it)
        }

        val objCtxClean = objectNode.get("@context")
        when {
            objCtxClean.isEmpty -> {
                val newContent = objectMapper.createArrayNode().add(contextV11)
                objectNode.set<ArrayNode>("@context", newContent)
            }

            objCtxClean.isArray -> (objCtxClean as ArrayNode).add(contextV11)
            objCtxClean.isObject -> (objCtxClean as ObjectNode).put("td", contextV11)
        }
    }

    fun convertTurtleToJsonLd(turtleData: String): ObjectNode {
        try {
            val model = ModelFactory.createDefaultModel()
            val rdfDataReader = StringReader(turtleData)
            RDFDataMgr.read(model, rdfDataReader, null, Lang.TURTLE)

            val jsonLdObject = JsonNodeFactory.instance.objectNode()

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
            throw ConversionException("Error converting Turtle to JSON-LD: ${e.message}")
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

                } else {
                    node.put(Utils.strconcat("@", predicateName), resourceURI)
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
                .substringBefore("^")
                .substringAfterLast("#")
            collectionNode.put(predicateName, objectValue)
        }
        return collectionNode
    }
}