package exceptions

/**
 * Exception thrown when a conversion operation encounters an error.
 *
 * @param message A detailed error message describing the conversion failure.
 */
class ConversionException (message: String): RuntimeException(message) {
}