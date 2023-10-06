package utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.DecimalNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import exceptions.ThingException
import io.ktor.http.*
import jakarta.json.JsonObject
import org.apache.jena.query.Dataset
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL

class Utils {
    val jsonMapper: ObjectMapper = jacksonObjectMapper()

    fun downloadFileAsString(uri: String): String {
        val url = URL(uri)
        val connection = url.openConnection()
        val reader = BufferedReader(InputStreamReader(connection.getInputStream()))
        val content = StringBuilder()
        var line: String?

        try {
            while (reader.readLine().also { line = it } != null) {
                content.append(line).append("\n")
            }
        } finally {
            reader.close()
        }

        return content.toString()
    }

    fun readFileAsStream(filePath: String): InputStream? {
        return try {
            File(filePath).inputStream()
        } catch (e: Exception) {
            null
        }
    }

    fun loadModel(filePath: String, lang: Lang): Model {
        try {
            val inputStream = readFileAsStream(filePath)

            if (inputStream != null) {
                val model = ModelFactory.createDefaultModel()
                RDFDataMgr.read(model, inputStream, lang)

                //println("Model loaded successfully\n${model.toString()}")
                return model
            } else {
                throw ThingException("Resource content is null for path: $filePath")
            }
        } catch (e: Exception) {
            println("\n\nException: $e. path: $filePath")
            throw ThingException("Cannot load model: ${e.message} path: $filePath")
        }
    }

    fun loadRDFDatasetIntoModelList(dataset: Dataset): ArrayList<Model> {
        val modelList = ArrayList<Model>()

        dataset.listNames().forEach { graphName ->
            val model = dataset.getNamedModel(graphName)
            modelList.add(model)
        }

        return modelList
    }

    fun loadRDFModelById(dataset: Dataset, graphId: String): Model {
        return dataset.getNamedModel(graphId)
    }

    fun hasThingType(thing: ObjectNode): Boolean {
        var type = thing["@type"]

        if (type is JsonNode) {
            val typeValue = type.asText()
            return typeValue == "Thing" || typeValue == "https://www.w3.org/2019/wot/td#Thing"
        } else if (type is ArrayNode) {
            val uniqueTypes = type.map { it.asText() }.toSet()
            return uniqueTypes.any { it == "Thing" || it == "https://www.w3.org/2019/wot/td#Thing" }
        }

        return false
    }

    fun strconcat(vararg values: String): String {
        return values.joinToString("")
    }

    fun hasValidId(id: String?): String {
        if (id.isNullOrEmpty()) {
            throw ThingException("Thing ID not valid.")
        }
        return id
    }

    fun hasJsonContent(contentTypeHeader: String?): Boolean {
        if (contentTypeHeader == null)
            return false

        val contentType = ContentType.parse(contentTypeHeader)
        val allowedContentTypes = listOf(
            ContentType.Application.Json,
            ContentType.parse("application/td+json"),
            ContentType.parse("application/ld+json"),
            ContentType.parse("application/merge-patch+json")
        )

        return allowedContentTypes.any { it.match(contentType) }
    }

    fun hasBody(body: String?): ObjectNode? {
        if (body.isNullOrEmpty()) {
            throw ThingException("The request body is empty.")
        }

        return jsonMapper.readValue(body)
    }

    fun idExists(map: MutableSet<String>, id: String): Boolean {
        return map.contains(id)
    }

    fun contextsContains(contexts: ArrayNode, targetContext: String): Boolean {
        return contexts.any { it.isTextual && it.asText() == targetContext }
    }

    fun isJsonLd11OrGreater(thing: ObjectNode): Boolean {
        val versionNode = thing["@version"]
        return versionNode != null
    }

    fun toJson(td: String): ObjectNode {
        val objectMapper = ObjectMapper()
        return objectMapper.readValue<ObjectNode>(td)
    }
}