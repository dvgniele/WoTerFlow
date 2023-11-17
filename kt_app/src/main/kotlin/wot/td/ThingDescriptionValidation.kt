package wot.td

import com.github.jsonldjava.utils.JsonUtils
import exceptions.ValidationException
import org.apache.jena.rdf.model.Model
import org.apache.jena.shacl.ShaclValidator
import org.apache.jena.shacl.Shapes
import org.apache.jena.shacl.ValidationReport
import org.glassfish.json.JsonUtil
import utils.Utils

//  xml shacl validation -> https://www.w3.org/ns/shacl.rdf
//  td json schema validation -> https://github.com/w3c/wot-thing-description/blob/main/validation/td-json-schema-validation.json
//  tm json schema valdation -> https://github.com/w3c/wot-thing-description/blob/main/validation/tm-json-schema-validation.json
//  td validation ttl -> https://github.com/w3c/wot-thing-description/blob/main/validation/td-validation.ttl

/**
 * Validates the semantic aspects of a Thing Description model.
 *
 * This class provides methods to perform semantic validation on a Thing Description model represented as RDF using the XML and Turtle (TTL) serialization format.
 */
class ThingDescriptionValidation {

    companion object {

        /**
         * Validates the semantic aspects of a [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) [Model].
         *
         * @param tdModel The RDF [Model] representing the [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td).
         * @param ttlModel The RDF [Model] representing the semantic validation rules.
         *
         * @return A list of validation messages. An empty list indicates a successful validation.
         * @throws Exception if an error occurred during the validation process.
         */
        fun validateSemantic(tdModel: Model, ttlModel: Model): List<String> {
            try {
                val shapes = Shapes.parse(ttlModel)
                val validationReport: ValidationReport = ShaclValidator.get().validate(shapes, tdModel.graph)

                if (!validationReport.conforms()) {
                    val validationFailures = validationReport.entries.map { entry ->
                        Utils.strconcat("code: ", entry.focusNode().toString(), "\nmessage: ", entry.toString())
                    }
                    return validationFailures
                }

                return emptyList()
            } catch (e: Exception) {
                throw Exception("Error performing Semantic Validation: ${e.message}")
            }
        }

        /**
         * Validates the syntactic aspects of a [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) [Model].
         *
         * @param tdModel The RDF [Model] representing the [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td).
         * @param xmlModel The RDF [Model] representing the syntactic validation rules.
         *
         * @return A list of validation messages. An empty list indicates a successful validation.
         * @throws Exception if an error occurred during the validation process.
         */
        fun validateSyntactic(tdModel: Model, xmlModel: Model): List<String> {
            try {
                val shapes = Shapes.parse(xmlModel)

                val validationReport = ShaclValidator.get().validate(shapes, tdModel.graph)

                if (!validationReport.conforms()) {
                    val validationFailures = validationReport.entries.map { entry ->
                        Utils.strconcat("code: ", entry.focusNode().toString(), "\nmessage: ", entry.toString())
                    }
                    return validationFailures
                }

                return emptyList()
            } catch (e: Exception) {
                throw Exception("Error performing Syntactic Validation: ${e.message}")
            }
        }
    }
}