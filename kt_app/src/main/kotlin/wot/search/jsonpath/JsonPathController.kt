package wot.search.jsonpath

import com.fasterxml.jackson.databind.node.ObjectNode
import com.jayway.jsonpath.InvalidPathException
import com.jayway.jsonpath.JsonPath
import exceptions.MissingQueryParameterException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

class JsonPathController {
    companion object {
        suspend fun executeJsonPathQuery(call: ApplicationCall, map: MutableMap<String, ObjectNode>) {
            try {
                val query = call.request.queryParameters["query"]
                if (query.isNullOrBlank()) {
                    throw MissingQueryParameterException("Query parameter is missing or blank")
                }

                val jsonpath = JsonPathService.validateQuery(query)

                val results = JsonPathService.executeQuery(jsonpath, map)

                call.response.header(HttpHeaders.ContentType, "application/json")
                call.respond(HttpStatusCode.OK, results)
            } catch (e: MissingQueryParameterException) {
                call.respond(HttpStatusCode.BadRequest, "${e.message}")
            } catch (e: InvalidPathException) {
                call.respond(HttpStatusCode.BadRequest, "Invalid JSONPath query: ${e.message}")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "An error occurred: ${e.message}")
            }
        }
    }
}