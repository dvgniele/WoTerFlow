package exceptions

import errors.ValidationError
import io.ktor.http.*

/**
 * Exception thrown when one or more validation errors occur.
 *
 * @param errors List of [ValidationError] objects containing details about validation errors.
 * @param message A detailed error message describing the validation exception (optional).
 */
class ValidationException(val errors: List<ValidationError>, message: String? = null): RuntimeException(message) {
}