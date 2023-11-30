package errors

/**
 * Represents details of an error response.
 *
 * @property type The type of error, if available.
 * @property title The title of the error.
 * @property status The HTTP Status Code associated with the error.
 * @property detail A detailed description of the errors.
 * @property instance The specific instance or occurrence of the errors, if available.
 * @property validationErrors A list of the validation errors, if applicable.
 */
data class ErrorDetails (
    val type: String? = null,
    val title: String,
    val status: Int,
    val detail: String,
    val instance: String? = null,
    val validationErrors: List<ValidationError>? = null
)