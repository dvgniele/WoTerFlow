package exceptions

import io.ktor.http.*

/**
 * Exception thrown when there is a problem related with a Thing Description.
 *
 * @param message A detailed error message describing the Thing Description problem.
 * @param statusCode The HTTP Status Code associated with the exception (default is [HttpStatusCode.BadRequest]).
 */
class ThingException(message: String, val statusCode: HttpStatusCode = HttpStatusCode.BadRequest) :
    RuntimeException(message)