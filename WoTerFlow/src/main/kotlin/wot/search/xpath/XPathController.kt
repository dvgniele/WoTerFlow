package wot.search.xpath

import com.fasterxml.jackson.databind.node.ObjectNode
import exceptions.MissingQueryParameterException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import net.sf.saxon.s9api.SaxonApiException

/**
 * Controller responsible for managing XPath Syntactic queries.
 */
class XPathController {
    companion object {
        /**
         * Executes the XPath query.
         *
         * @param call The [ApplicationCall] representing the HTTP request.
         * @param map The Things map to operate on.
         */
        suspend fun executeXPathQuery(call: ApplicationCall, map: MutableMap<String, ObjectNode>) {
            try {
                val query = call.request.queryParameters["query"]
                if (query.isNullOrBlank()) {
                    throw MissingQueryParameterException("Query parameter is missing or blank")
                }

                val results = XPathService.executeQuery(query, map)

                call.response.header(HttpHeaders.ContentType, "application/json")
                call.respond(HttpStatusCode.OK, results)
            } catch (e: MissingQueryParameterException) {
                call.respond(HttpStatusCode.BadRequest, "${e.message}")
            } catch (e: SaxonApiException) {
                call.respond(HttpStatusCode.BadRequest, "${e.message}")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "An error occurred: ${e.message}")
            }
        }
    }
}