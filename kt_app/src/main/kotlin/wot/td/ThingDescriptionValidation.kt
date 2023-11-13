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

class ThingDescriptionValidation {

    companion object {

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