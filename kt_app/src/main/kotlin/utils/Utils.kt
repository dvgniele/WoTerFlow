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
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import java.io.File
import java.io.InputStream
import java.net.URL
import java.nio.charset.Charset

class Utils {
    val jsonMapper: ObjectMapper = jacksonObjectMapper()

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

                println("Model loaded successfully\n${model.toString()}")
                return model
            } else {
                throw ThingException("Resource content is null for path: $filePath")
            }
        } catch (e: Exception) {
            println("\n\nException: $e. path: $filePath")
            throw ThingException("Cannot load model: ${e.message}")
        }
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
            ContentType.parse("application/ld+json")
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
        if (versionNode is DecimalNode) {
            val version = versionNode.decimalValue().toDouble()
            return version >= 1.1
        }
        return false
    }
}