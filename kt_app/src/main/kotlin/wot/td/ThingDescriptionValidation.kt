package wot.td

import com.github.jsonldjava.utils.JsonUtils
import exceptions.ValidationException
import org.apache.jena.rdf.model.Model
import org.apache.jena.shacl.ShaclValidator
import org.apache.jena.shacl.Shapes
import org.apache.jena.shacl.ValidationReport
import org.glassfish.json.JsonUtil
import utils.Utils

//  (prev) ttl validation -> https://www.w3.org/ns/td.ttl
//  (prev) jsonld validation -> https://www.w3.org/ns/td.jsonld


//  (curr) xml shacl validation -> https://www.w3.org/ns/shacl.rdf
//  (curr) td json schema validation -> https://github.com/w3c/wot-thing-description/blob/main/validation/td-json-schema-validation.json
//  (curr) tm json schema valdation -> https://github.com/w3c/wot-thing-description/blob/main/validation/tm-json-schema-validation.json
//  (curr) td validation ttl -> https://github.com/w3c/wot-thing-description/blob/main/validation/td-validation.ttl

class ThingDescriptionValidation {

    val utils = Utils()

    fun validateSemantic(tdModel: Model, ttlModel: Model): List<String> {
        try {
            val validationReport: ValidationReport = ShaclValidator.get().validate(ttlModel.graph, tdModel.graph)

            if(!validationReport.conforms()) {
                val validationFailures = validationReport.entries.map { entry ->
                    utils.strconcat("code: ", entry.focusNode().toString(), "\nmessage: ", entry.toString())
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

            if(!validationReport.conforms()) {
                val validationFailures = validationReport.entries.map { entry ->
                    utils.strconcat("code: ", entry.focusNode().toString(), "\nmessage: ", entry.toString())
                }
                return validationFailures
            }

            return emptyList()
        } catch (e: Exception) {
            throw Exception("Error performing Syntactic Validation: ${e.message}")
        }
    }

    /* to verify
    fun validateSemantic(tdModel: Model, contextModel: Model): Boolean {

        val validationResults = mutableListOf<Boolean>()

        contextModel.listSubjects().forEachRemaining { contextSubject ->
            val tdStatements = tdModel.listStatements(contextSubject, RDF.type, null as RDFNode?)
            validationResults.add(tdStatements.hasNext())
        }

        return validationResults.all { it }

    }
     */
}