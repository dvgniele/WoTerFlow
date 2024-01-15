package wot.search.sparql

import com.jayway.jsonpath.JsonPath
import exceptions.UnsupportedSparqlQueryException
import org.apache.jena.query.Dataset
import org.apache.jena.query.QueryExecution
import org.apache.jena.query.TxnType
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QueryFactory
import org.apache.jena.query.Syntax
import org.apache.jena.query.ResultSetFormatter
import org.apache.jena.riot.Lang
import org.apache.jena.sparql.resultset.ResultsFormat
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
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
            return dataset.writeTransaction {
                val outputStream = ByteArrayOutputStream()
                val queryExecution = QueryExecutionFactory.create(QueryFactory.create(query, Syntax.syntaxSPARQL_11), dataset)

                try {
                    when {
                        queryExecution.query.isAskType -> executeAskQuery(queryExecution, format, outputStream)
                        queryExecution.query.isSelectType -> executeSelectQuery(queryExecution, format, outputStream)
                        queryExecution.query.isConstructType || queryExecution.query.isDescribeType ->
                            executeConstructOrDescribeQuery(queryExecution, outputStream)
                        else -> throw UnsupportedSparqlQueryException("Unsupported query type")
                    }
                }finally {
                    queryExecution.close()
                }

                outputStream
            }
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

        /**
         * Executes the provided [action] within a write transaction on the current [Dataset].
         * Right after executing the action, the transaction is committed and closed.
         *
         * @param action The [action] to be executed within the write transaction.
         * @return The result of the [action], typically a [ByteArrayOutputStream]
         */
        private fun Dataset.writeTransaction(action: Dataset.(Dataset) -> ByteArrayOutputStream): ByteArrayOutputStream {
            begin(TxnType.WRITE)
            try {
                return action(this, this)
            } finally {
                commit()
                end()
            }
        }

        /**
         * Executes a SPARQL ASK query and formats the results based on the specified [format].
         *
         * @param queryExecution The [QueryExecution] object for the ASK query.
         * @param format The desired [ResultsFormat] for the results (JSON, XML, CSV, TSV).
         * @param outputStream The output stream to which the formatted results are written.
         * @throws UnsupportedSparqlQueryException if the specified format is not supported for ASK queries.
         */
        private fun executeAskQuery(queryExecution: QueryExecution, format: ResultsFormat, outputStream: ByteArrayOutputStream) {
            when (format) {
                ResultsFormat.FMT_RS_JSON -> ResultSetFormatter.outputAsJSON(outputStream, queryExecution.execAsk())
                ResultsFormat.FMT_RS_XML -> ResultSetFormatter.outputAsXML(outputStream, queryExecution.execAsk())
                ResultsFormat.FMT_RS_CSV -> ResultSetFormatter.outputAsCSV(outputStream, queryExecution.execAsk())
                ResultsFormat.FMT_RS_TSV -> ResultSetFormatter.outputAsTSV(outputStream, queryExecution.execAsk())
                else -> throw UnsupportedSparqlQueryException("Unsupported format for ASK query")
            }
        }

        /**
         * Executes a SPARQL SELECT query and formats the results based on the specified [format].
         *
         * @param queryExecution The [QueryExecution] object for the SELECT query.
         * @param format The desired [ResultsFormat] for the results (JSON, XML, CSV, TSV).
         * @param outputStream The output stream to which the formatted results are written.
         * @throws UnsupportedSparqlQueryException if the specified format is not supported for SELECT queries.
         */
        private fun executeSelectQuery(queryExecution: QueryExecution, format: ResultsFormat, outputStream: ByteArrayOutputStream) {
            when (format) {
                ResultsFormat.FMT_RS_JSON -> ResultSetFormatter.outputAsJSON(outputStream, queryExecution.execSelect())
                ResultsFormat.FMT_RS_XML -> ResultSetFormatter.outputAsXML(outputStream, queryExecution.execSelect())
                ResultsFormat.FMT_RS_CSV -> ResultSetFormatter.outputAsCSV(outputStream, queryExecution.execSelect())
                ResultsFormat.FMT_RS_TSV -> ResultSetFormatter.outputAsTSV(outputStream, queryExecution.execSelect())
                else -> throw UnsupportedSparqlQueryException("Unsupported format for SELECT query")
            }
        }

        /**
         * Executes a SPARQL CONSTRUCT or DESCRIBE query and writes the results to the specified [outputStream].
         *
         * @param queryExecution The [QueryExecution] object for the CONSTRUCT or DESCRIBE query.
         * @param outputStream The output stream to which the results are written.
         */
        private fun executeConstructOrDescribeQuery(queryExecution: QueryExecution, outputStream: ByteArrayOutputStream) {
            val resultModel = if (queryExecution.query.isConstructType) {
                queryExecution.execConstruct()
            } else {
                queryExecution.execDescribe()
            }
            resultModel.write(outputStream, Lang.TURTLE.name)
        }
    }
}