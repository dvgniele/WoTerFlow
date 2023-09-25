package errors

data class ValidationError (
    val field: String,
    val description: String
)