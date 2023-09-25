package errors

data class ErrorDetails (
    val type: String? = null,
    val title: String,
    val status: Int,
    val detail: String,
    val instance: String? = null,
    val validationErrors: List<ValidationError>? = null
)