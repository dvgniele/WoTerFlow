package exceptions

import io.ktor.http.*

/**
 * Exception thrown when a required query parameter is missing.
 *
 * @param message A detailed error message describing the missing query parameter.
 * @param statusCode The HTTP Status Code associated with the exception (default is [HttpStatusCode.BadRequest]).
 */
class MissingQueryParameterException(message: String, val statusCode: HttpStatusCode = HttpStatusCode.BadRequest) :
RuntimeException(message)