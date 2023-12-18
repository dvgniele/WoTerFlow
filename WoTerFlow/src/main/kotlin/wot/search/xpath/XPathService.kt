package wot.search.xpath

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import java.io.StringReader
import net.sf.saxon.s9api.Processor
import net.sf.saxon.s9api.XdmNode
import net.sf.saxon.s9api.XdmValue
import utils.Utils
import wot.directory.DirectoryConfig
import javax.xml.transform.stream.StreamSource

class XPathService {
    companion object {
        val processor = Processor(false)

        /**
         * Executes the XPath query.
         *
         * @param query The XPath query to execute.
         * @param map The Things map to operate on.
         *
         * @return [List] of [ObjectNode] obtained via the XPath query.
         */
        fun executeQuery(query: String, map: Map<String, ObjectNode>): List<ObjectNode> {
            return map.values
                .mapNotNull { td ->
                    val jsonNode = ObjectMapper().valueToTree<JsonNode>(td)
                    val xmlString = XmlMapper().writeValueAsString(jsonNode)
                        .replace("@", "")
                    val tdDocument = buildXmlDocument(xmlString)
                    val results = evaluateXPath(query, tdDocument)
                    extractMatchingTD(results, map)
                }
        }

        /**
         * Builds an [XdmNode] representing an XML document from the given XML string.
         *
         * @param xmlString A string containing XML content.
         *
         * @return An [XdmNode] representing the XML document.
         */
        private fun buildXmlDocument(xmlString: String): XdmNode {
            val documentBuilder = processor.newDocumentBuilder()
            val xmlSource = StreamSource(StringReader(xmlString))

            return documentBuilder.build(xmlSource)
        }

        /**
         * Evaluates an XPath query on the provided [XdmNode] and returns the result as an [XdmValue].
         *
         * @param query The XPath query to be evaluated.
         * @param tdDocument An [XdmNode] representing the XML document on which the XPath query will be executed.
         *
         * @return An [XdmValue] containing the result of the XPath query.
         */
        private fun evaluateXPath(query: String, tdDocument: XdmNode): XdmValue {
            val compiler = processor.newXPathCompiler()
            compiler.languageVersion = "3.1"
            val xpathSelector = compiler.compile(query).load()
            xpathSelector.contextItem = tdDocument

            return xpathSelector.evaluate()
        }

        /**
         * Extracts a matching [ObjectNode] from the provided map based on the results of an XQuery evaluation.
         *
         * @param results An [XdmValue] containing the results of an XQuery evaluation.
         * @param map A map where keys are graph IDs (prefixed with [DirectoryConfig.GRAPH_PREFIX]) and values are [ObjectNode] instances.
         *
         * @return An [ObjectNode] from the map corresponding to the "id" property extracted from the [XdmValue].
         * Returns null if the [XdmValue] does not contain a valid [ObjectNode].
         */
        private fun extractMatchingTD(results: XdmValue, map: Map<String, ObjectNode>): ObjectNode? {
            if (results is XdmNode) {
                val resultJson = convertXdmToJson(results)
                return if (resultJson is ObjectNode) {
                    resultJson["id"]?.textValue().let {
                        map[Utils.strconcat(DirectoryConfig.GRAPH_PREFIX, it!!)] ?: throw NoSuchElementException("No Such Element: id -> $it")
                    }
                } else {
                    null
                }
            }
            return null
        }

        /**
         * Converts the [XdmNode] to [JsonNode].
         *
         * @param xdmNode The [XdmNode] to convert.
         *
         * @return The converted [XdmNode] to [JsonNode].
         */
        private fun convertXdmToJson(xdmNode: XdmNode): JsonNode {
            val objectMapper = XmlMapper()
            val jsonText = xdmNode.toString()
            return objectMapper.readTree(jsonText)
        }
    }
}