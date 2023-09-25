package exceptions

import errors.ValidationError

class ValidationException(val errors: List<ValidationError>, message: String? = null): RuntimeException(message) {
}