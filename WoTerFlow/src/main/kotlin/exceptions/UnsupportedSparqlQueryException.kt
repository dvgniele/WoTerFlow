package exceptions

import io.ktor.http.*

/**
 * Exception thrown when a SPARQL query is not supported.
 *
 * @param message A detailed error message describing the SPARQL query problem.
 * @param statusCode The HTTP Status Code associated with the exception (default is [HttpStatusCode.BadRequest]).
 */
class UnsupportedSparqlQueryException(message: String, val statusCode: HttpStatusCode = HttpStatusCode.BadRequest) :
    RuntimeException(message)