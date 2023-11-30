package wot.search.sparql

import com.fasterxml.jackson.databind.node.ObjectNode
import com.jayway.jsonpath.JsonPath
import exceptions.UnsupportedSparqlQueryException
import org.apache.jena.query.*
import org.apache.jena.riot.Lang
import org.apache.jena.sparql.resultset.ResultsFormat
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import utils.RDFConverter
import utils.Utils
import java.io.ByteArrayOutputStream

/**
 * Service to execute [JsonPath] queries.
 */
class SparqlService {
    companion object {
        /**
         * Executes the SPARQL query.
         *
         * @param query The SPARQL query to execute.
         * @param format The [ResultsFormat] of the query to execute.
         * @param dataset The Things [Dataset] to operate on.
         *
         * @return [ByteArrayOutputStream] containing the query results.
         */
        fun executeQuery(query: String, format: ResultsFormat, dataset: Dataset): ByteArrayOutputStream {
            dataset.begin(TxnType.WRITE)

            val outputStream = ByteArrayOutputStream()
            val queryExecution = QueryExecutionFactory.create(QueryFactory.create(query, Syntax.syntaxSPARQL_11), dataset)

            try {
                when {
                    queryExecution.query.isAskType -> {
                        when (format) {
                            ResultsFormat.FMT_RS_JSON -> ResultSetFormatter.outputAsJSON(
                                outputStream,
                                queryExecution.execAsk()
                            )

                            ResultsFormat.FMT_RS_XML -> ResultSetFormatter.outputAsXML(
                                outputStream,
                                queryExecution.execAsk()
                            )

                            ResultsFormat.FMT_RS_CSV -> ResultSetFormatter.outputAsCSV(
                                outputStream,
                                queryExecution.execAsk()
                            )

                            ResultsFormat.FMT_RS_TSV -> ResultSetFormatter.outputAsTSV(
                                outputStream,
                                queryExecution.execAsk()
                            )

                            else -> throw UnsupportedSparqlQueryException("Unsupported format for ASK query")
                        }
                    }

                    queryExecution.query.isSelectType -> {
                        when (format) {
                            ResultsFormat.FMT_RS_JSON -> ResultSetFormatter.outputAsJSON(
                                outputStream,
                                queryExecution.execSelect()
                            )

                            ResultsFormat.FMT_RS_XML -> ResultSetFormatter.outputAsXML(
                                outputStream,
                                queryExecution.execSelect()
                            )

                            ResultsFormat.FMT_RS_CSV -> ResultSetFormatter.outputAsCSV(
                                outputStream,
                                queryExecution.execSelect()
                            )

                            ResultsFormat.FMT_RS_TSV -> ResultSetFormatter.outputAsTSV(
                                outputStream,
                                queryExecution.execSelect()
                            )

                            else -> throw UnsupportedSparqlQueryException("Unsupported format for SELECT query")
                        }
                    }

                    queryExecution.query.isConstructType || queryExecution.query.isDescribeType -> {
                        val resultModel = if (queryExecution.query.isConstructType) {
                            queryExecution.execConstruct()
                        } else {
                            queryExecution.execDescribe()
                        }

                        resultModel.write(outputStream, Lang.TURTLE.name)
                    }
                    else -> throw UnsupportedSparqlQueryException("Unsupported query type")
                }

                dataset.commit()
            } finally {
                queryExecution.close()
                dataset.end()
            }

            return outputStream
        }

        /**
         * Executes the update [query] the [Dataset].
         *
         * @param query The SPARQL query to execute.
         * @param dataset The Things [Dataset] to operate on.
         */
        fun update(query: String, dataset: Dataset) {
            try {
                val updateQuery = UpdateFactory.create(query)
                val updateProcessor = UpdateExecutionFactory.create(updateQuery, dataset)

                updateProcessor.execute()
            } catch (e: Exception) {
                throw Exception("${e.message}")
            }
        }
    }
}