package utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.jsonldjava.core.JsonLdOptions
import com.github.jsonldjava.core.JsonLdProcessor
import com.github.jsonldjava.utils.JsonUtils
import exceptions.ThingException
import org.apache.commons.io.output.ByteArrayOutputStream
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import java.io.StringReader
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.util.HashMap

class RDFConverter {

    val utils: Utils = Utils()

    fun convertRdfModelToObjectNode(rdfModelString: Model): ObjectNode {
        try {
            val serializedModel = convertRdfToStringSerialization(rdfModelString, Lang.TURTLE)

            return convertTurtleToJsonLd(serializedModel)
        } catch (e: Exception) {
            throw ThingException("Error converting RDF Model String to ObjectNode: ${e.message}")
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

    fun convertRdfStringToRdf(jsonLdString: String, lang: Lang): Model {
        try {
            val model = ModelFactory.createDefaultModel()

            RDFDataMgr.read(model, jsonLdString.reader(), null, lang)

            return model
        } catch (e: Exception){
            throw Exception("Error converting JSON-LD to ${lang.name.uppercase()}: ${e.message}")
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

    fun convertRdfToStringSerialization(model: Model, lang: Lang): String {
        try {
            val outputStream = ByteArrayOutputStream()

            RDFDataMgr.write(outputStream, model, lang)

            return  outputStream.toString(StandardCharsets.UTF_8)
        } catch (e: Exception) {
            throw Exception("Error converting RDF to String Serialization: ${e.message}")
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

    fun convertJsonLdToRdf(jsonLdString: String, lang: Lang): Model {
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
                .substringBefore("^^")
            collectionNode.put(predicateName, objectValue)
        }
        return collectionNode
    }
}