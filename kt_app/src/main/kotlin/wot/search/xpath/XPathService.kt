package wot.search.xpath

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import java.io.StringReader
import net.sf.saxon.s9api.Processor
import net.sf.saxon.s9api.XdmNode
import utils.Utils
import wot.directory.DirectoryConfig
import javax.xml.transform.stream.StreamSource

class XPathService {
    companion object {
        fun executeQuery(query: String, map: MutableMap<String, ObjectNode>): List<ObjectNode> {
            val matchingTDs = mutableListOf<ObjectNode>()

            val processor = Processor(false)
            val compiler = processor.newXPathCompiler()
            compiler.languageVersion = "3.1"

            for (td in map.values){
                val objectMapper = ObjectMapper()
                val jsonNode = objectMapper.valueToTree<ObjectNode>(td)

                val xmlMapper = XmlMapper()

                val xmlString = xmlMapper.writeValueAsString(jsonNode).replace("@", "")

                val documentBuilder = processor.newDocumentBuilder()
                val xmlSource = StreamSource(StringReader(xmlString))
                val tdDocument = documentBuilder.build(xmlSource)

                val xpathSelector = compiler.compile(query).load()

                xpathSelector.contextItem = tdDocument

                val results = xpathSelector.evaluate()

                if (results is XdmNode) {
                    val resultJson = convertXdmToJson(results)
                    if (resultJson is ObjectNode) {
                        matchingTDs.add(resultJson)
                    }
                }
            }

            return matchingTDs.mapNotNull {
                it["id"]?.textValue()?.let {
                    id -> map[Utils.strconcat(
                        DirectoryConfig.GRAPH_PREFIX,
                        id
                    )]
                }
            }

        }

        private fun convertXdmToJson(xdmNode: XdmNode): JsonNode {
            val objectMapper = XmlMapper()
            val jsonText = xdmNode.toString()
            return objectMapper.readTree(jsonText)
        }
    }
}