package wot.td

import exceptions.ThingException
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.shacl.ShaclValidator
import org.apache.jena.shacl.Shapes
import org.apache.jena.shacl.ValidationReport
import org.apache.jena.vocabulary.RDF
import java.io.InputStream
import java.net.URL

// ttl validation -> https://www.w3.org/ns/td.ttl
// jsonld validation -> https://www.w3.org/ns/td.jsonld
class ThingDescriptionValidation {

    fun validateSemantic(tdModel: Model, contextModel: Model): Boolean {
        try {
            val validator = ShaclValidator.get()

            val validationReport: ValidationReport = validator.validate(contextModel.graph, tdModel.graph)

            return validationReport.conforms()

        } catch (e: Exception) {
            throw Exception("Error performing Semantic Validation: ${e.message}")
        }
    }

    fun validateSyntactic(tdModel: Model, ttlModel: Model): Boolean {
        val shapes = Shapes.parse(ttlModel)
        val validationReport: ValidationReport = ShaclValidator.get().validate(shapes, tdModel.graph)

        return validationReport.conforms()
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