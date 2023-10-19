package wot.search.jsonpath

import com.fasterxml.jackson.databind.node.ObjectNode
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.InvalidPathException
import com.jayway.jsonpath.JsonPath

class JsonPathService {
    companion object{
        fun executeQuery(query: JsonPath, map: MutableMap<String, ObjectNode>): List<ObjectNode> {
            val jsonPathConfig = Configuration.defaultConfiguration()

            val matchingTDs = map.values.filter { td ->
                val matchingNodes = JsonPath.using(jsonPathConfig)
                    .parse(td.toString())
                    .read(query) as List<ObjectNode>

                matchingNodes.isNotEmpty()
            }

            return matchingTDs
        }

        fun validateQuery(query: String): JsonPath {
            if (!query.startsWith("$")){
                throw InvalidPathException("JSONPath should start with `$`")
            }

            return JsonPath.compile(query)
        }
    }
}