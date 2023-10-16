package exceptions

import io.ktor.http.*

class UnsupportedSparqlQueryException(message: String, val statusCode: HttpStatusCode = HttpStatusCode.BadRequest) :
    RuntimeException(message)