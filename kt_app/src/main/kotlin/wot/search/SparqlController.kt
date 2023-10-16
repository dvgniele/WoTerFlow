package wot.search

import exceptions.UnsupportedSparqlQueryException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.apache.jena.query.Dataset
import org.apache.jena.query.QueryFactory
import org.apache.jena.query.Syntax
import org.apache.jena.shared.NotFoundException
import org.apache.jena.sparql.resultset.ResultsFormat

class SparqlController {
    companion object {
        private val MIME_SPARQL_JSON = "application/sparql-results+json"
        private val MIME_SPARQL_XML = "application/sparql-results+xml"
        private val MIME_SPARQL_CSV = "text/csv"
        private val MIME_SPARQL_TSV = "text/tab-separated-values"
        private val MIME_SPARQL_TURTLE = "text/turtle"

        suspend fun executeSparqlQuery(call: ApplicationCall, db: Dataset) {
            try {
                val query = getQueryFromRequest(call)
                val accept = call.request.header(HttpHeaders.Accept)

                if (query == null) {
                    // todo
                    throw Exception()
                }

                val format = validateQueryFormat(query, accept)
                    ?: throw UnsupportedSparqlQueryException("Mime format not supported")

                val type = getResponseType(format)
                call.response.header(HttpHeaders.ContentType, type)

                call.respond(SparqlService.executeQuery(query, format, db).toString())
            } catch (e: NotFoundException) {
                call.respond(HttpStatusCode.NotFound, "Requested Thing not found")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "An error occurred: ${e.message}")
            }
        }

        private fun validateQueryFormat(query: String, mimeType: String?): ResultsFormat? {
            val parsedQuery = QueryFactory.create(query, Syntax.syntaxSPARQL_11)
            return when {
                parsedQuery.isAskType || parsedQuery.isSelectType -> {
                    when (mimeType) {
                        MIME_SPARQL_XML -> ResultsFormat.FMT_RS_XML
                        MIME_SPARQL_CSV -> ResultsFormat.FMT_RS_CSV
                        MIME_SPARQL_TSV -> ResultsFormat.FMT_RS_TSV
                        else -> ResultsFormat.FMT_RS_JSON
                    }
                }

                parsedQuery.isConstructType || parsedQuery.isDescribeType -> ResultsFormat.FMT_RDF_TURTLE
                else -> throw Exception("Supported SPARQL queries are SELECT, ASK, DESCRIBE, and CONSTRUCT")
            }
        }

        private fun getResponseType(format: ResultsFormat): String {
            return when (format) {
                ResultsFormat.FMT_RS_JSON -> MIME_SPARQL_JSON
                ResultsFormat.FMT_RS_XML -> MIME_SPARQL_XML
                ResultsFormat.FMT_RS_CSV -> MIME_SPARQL_CSV
                ResultsFormat.FMT_RS_TSV -> MIME_SPARQL_TSV
                ResultsFormat.FMT_RDF_TURTLE -> MIME_SPARQL_TURTLE
                else -> MIME_SPARQL_JSON
            }
        }

        private suspend fun getQueryFromRequest(call: ApplicationCall): String? {
            return when (call.request.httpMethod) {
                HttpMethod.Get -> call.request.queryParameters["query"]
                HttpMethod.Post -> {
                    val body = call.receiveText()
                    body.ifEmpty {
                        call.request.queryParameters["query"]
                    }
                }

                else -> null
            }
        }
    }
}