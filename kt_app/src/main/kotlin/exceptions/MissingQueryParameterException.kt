package exceptions

import io.ktor.http.*

class MissingQueryParameterException(message: String, val statusCode: HttpStatusCode = HttpStatusCode.BadRequest) :
RuntimeException(message)