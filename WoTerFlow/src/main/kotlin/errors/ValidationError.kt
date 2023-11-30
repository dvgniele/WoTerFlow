package errors

/**
 * Represents a validation error.
 *
 * @property field The field which caused the error.
 * @property description The description of the error.
 */
data class ValidationError (
    val field: String,
    val description: String
)