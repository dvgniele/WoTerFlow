package exceptions

import io.ktor.http.*

class ThingException(message: String, val statusCode: HttpStatusCode = HttpStatusCode.BadRequest) :
    RuntimeException(message)