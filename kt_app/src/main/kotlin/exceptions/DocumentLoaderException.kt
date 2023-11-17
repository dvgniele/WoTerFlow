package exceptions

/**
 * Exception thrown when a document loading operation encounters an error.
 *
 * @param message A detailed error message describing the document loading failure.
 */
class DocumentLoaderException (message: String): RuntimeException(message) {
}