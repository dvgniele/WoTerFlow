package utils

import com.apicatalog.jsonld.document.Document
import com.apicatalog.jsonld.document.JsonDocument
import com.apicatalog.jsonld.loader.DocumentLoader
import com.apicatalog.jsonld.loader.DocumentLoaderOptions
import exceptions.DocumentLoaderException
import java.net.URI

/**
 * Utility class to retrieve the [JsonDocument] of a json document loaded in ram.
 *
 * @param documentCache Map of documents. Key: document url. Value: document content.
 *
 */
class CachedDocumentLoader(private val documentCache: Map<String, String>): DocumentLoader{
    /**
     * Loads the document.
     *
     * @param url Url of the document.
     * @param options The options to use for the [JsonDocument].
     *
     * @return The loaded document.
     */
    override fun loadDocument(url: URI, options: DocumentLoaderOptions): Document {
        return if (documentCache.containsKey(url.toString())) {
            val cachedDocument = documentCache[url.toString()]!!
            val document = JsonDocument.of(cachedDocument.reader())

            document
        } else {
            throw DocumentLoaderException("Document not found in cache: $url")
        }
    }
}
