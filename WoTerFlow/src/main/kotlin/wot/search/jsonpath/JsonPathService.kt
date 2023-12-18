package wot.search.jsonpath

import com.fasterxml.jackson.databind.node.ObjectNode
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.InvalidPathException
import com.jayway.jsonpath.JsonPath

/**
 * Service to execute [JsonPath] queries.
 */
class JsonPathService {
    companion object{
        /**
         * Executes the [JsonPath] query.
         *
         * @param query The [JsonPath] query to execute.
         * @param map The Things map to operate on.
         *
         * @return [List] of [ObjectNode] obtained via the [JsonPath] query.
         */
        fun executeQuery(query: JsonPath, map: Map<String, ObjectNode>): List<ObjectNode> {
            val jsonPathConfig = Configuration.defaultConfiguration()

            return map.values.filter { td ->
                val matchingNodes = JsonPath.using(jsonPathConfig)
                    .parse(td.toString())
                    .read(query) as List<ObjectNode>

                matchingNodes.isNotEmpty()
            }
        }

        /**
         * Validates if a [JsonPath] query is valid or not.
         *
         * @param query The query to validate.
         *
         * @return The compiled [JsonPath] query.
         * @throws InvalidPathException If [JsonPath] [query] not valid
         */
        fun validateQuery(query: String): JsonPath {
            if (!query.startsWith("$")){
                throw InvalidPathException("JSONPath should start with `$`")
            }

            return JsonPath.compile(query)
        }
    }
}