# WoTerFlow: Experimental Kotlin Implementation for Web of Things Directory


## Overview

WoTerFlow is an experimental Directory implementation in Kotlin, compliant with the [W3C Web of Things Directory](https://www.w3.org/TR/wot-discovery/).
The main goal of this implementation is to provide a high-performance and system but requiring low specifications utilizing Kotlin.

## Key Features

- **Experimental Kotlin Implementation:** WoTerFlow leverages the power of Kotlin for modern software development
- **Concurrent Request Handling:** WoTerFlow is designed to efficiently handle concurrent requests, ensuring seamless performance in a WoT environment.
- **Data Correctness and Security:** The server prioritizes the correctness and security of the persistent data layer, implementing synchronized constructs to guarantee data integrity.
- **[Ktor CIO Engine](https://api.ktor.io/ktor-client/ktor-client-cio/io.ktor.client.engine.cio/index.html):** Built on the Ktor framework with the CIO (Coroutine-based I/O) engine, the system delivers efficiency and scalability, taking full advance of Kotlin's coroutines asynchronous capabilities.
- **Jena for RDF Storage:** [Apache Jena](https://jena.apache.org/) is used for persistent [RDF](https://jena.apache.org/documentation/rdf/index.html) storage and triple store functionality, to allow [Semantic queries via SPARQL](https://jena.apache.org/documentation/query/index.html).
- **Optimized Lookup Calls:** To enhance performance, the server relies on a preloaded, in-memory data structure for lookup calls, eliminating the need for frequent queries to the triple store.
- **JSON-LD v1.1:** Performs conversion to the latest [JSONLD-LD v1.1](https://www.w3.org/TR/json-ld11/) format.


## Features

- [x] Things API
  - [x] Creation
  - [x] Retrieval
  - [x] Update
  - [x] Deletion
  - [x] Listing
  - [x] Validation
- [x] Events API
  - [x] Creation Event
  - [x] Update Event
  - [x] Deletion Event
  - [ ] Diff support
- [x] Search API
  - [x] Syntactic search: JSONPath
  - [x] Syntactic search: XPath
  - [x] Semantic search: SPARQL

## Supported APIs

### [Things APIs Endpoints](https://w3c.github.io/wot-discovery/#exploration-directory-api-things)
| API Endpoint                               | Method   | Headers                                                  | Reference                                                                                                                                                                            | Description                                                                                                                                                                                                                                                                                          |
|--------------------------------------------|----------|----------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `/things`                                  | `HEAD`   | `n/a`                                                    | [Listing](https://w3c.github.io/wot-discovery/#exploration-directory-api-things-listing)                                                                                             | Responds to the `HEAD` request for the _listing_ route.                                                                                                                                                                                                                                              |
| `/things/`                                 | `HEAD`   | `n/a`                                                    | [Listing](https://w3c.github.io/wot-discovery/#exploration-directory-api-things-listing)                                                                                             | Responds to the `HEAD` request for the _listing_ route.                                                                                                                                                                                                                                              |
| `/things`                                  | `GET`    | `Accept:` `application/ld+json` `application/json`       | [Listing](https://w3c.github.io/wot-discovery/#exploration-directory-api-things-listing)                                                                                             | Retrieves the listing of all the stored Thing Descriptions, in a `JSON-LD` format.                                                                                                                                                                                                                   |
| `/things/`                                 | `GET`    | `Accept:` `application/ld+json` `application/json`       | [Listing](https://w3c.github.io/wot-discovery/#exploration-directory-api-things-listing)                                                                                             | Retrieves the listing of all the stored Thing Descriptions, in a `JSON-LD` format.                                                                                                                                                                                                                   |
| `/things{offset,limit,sort_by,sort_order}` | `GET`    | `Accept:` `application/ld+json` `application/json`       | [Listing](https://w3c.github.io/wot-discovery/#exploration-directory-api-things-listing)                                                                                             | Retrieves the listing of all the stored Thing Descriptions, in a `JSON-LD` format, using the specified filters.                                                                                                                                                                                      |
| `/things/{id}`                             | `HEAD`   | `n/a`                                                    | [Retrieval](https://w3c.github.io/wot-discovery/#exploration-directory-api-things-retrieval)                                                                                         | Checks if a [Thing Descriptions](https://www.w3.org/TR/wot-thing-description/#introduction-td) exists within the system.                                                                                                                                                                                                                                            |
| `/things/{id}`                             | `GET`    | `Accept:` `application/ld+json` `application/json`       | [Retrieval](https://w3c.github.io/wot-discovery/#exploration-directory-api-things-retrieval)                                                                                         | Retrieves a Thing Description with the corresponding request parameter `id` (if existing), in a `JSON-LD` format.                                                                                                                                                                                    |
| `/things`                                  | `POST`   | `Content-Type:` `application/ld+json` `application/json` | [Creation (anonymous)](https://w3c.github.io/wot-discovery/#exploration-directory-api-things-creation)                                                                               | Creates an [Anonymous Thing Description](https://w3c.github.io/wot-discovery/#exploration-directory-anonymous-td) with the provided request body (MUST be `JSON-LD` format). The generated `uuid` is returned in the response `Location Header`.                                                     |
| `/things/`                                 | `POST`   | `Content-Type:` `application/ld+json` `application/json` | [Creation (anonymous)](https://w3c.github.io/wot-discovery/#exploration-directory-api-things-creation)                                                                               | Creates an [Anonymous Thing Description](https://w3c.github.io/wot-discovery/#exploration-directory-anonymous-td) with the provided request body (MUST be `JSON-LD` format). The generated `uuid` is returned in the response `Location Header`.                                                     |
| `/things/{id}`                             | `PUT`    | `Content-Type:` `application/ld+json` `application/json` | [Creation](https://w3c.github.io/wot-discovery/#exploration-directory-api-things-creation) or [Update](https://w3c.github.io/wot-discovery/#exploration-directory-api-things-update) | Creates (if not existing) or Updates (instead) an [Anonymous Thing Description](https://w3c.github.io/wot-discovery/#exploration-directory-anonymous-td) with the provided request body (MUST be `JSON-LD` format). The generated (or updated) `uuid` is returned in the response `Location Header`. |
| `/things/{id}`                             | `PATCH`  | `Content-Type:` `application/ld+json` `application/json` | [Partial Update (Patch)](https://w3c.github.io/wot-discovery/#exploration-directory-api-things-update)                                                                               | Partially updates an existing [Anonymous Thing Description](https://w3c.github.io/wot-discovery/#exploration-directory-anonymous-td) with the provided request body. The TD `id` is returned in the response `Location Header`.                                                                      |                                                                                                            
| `/things/{id}`                             | `DELETE` | `n/a`                                                    | [Deletion](https://w3c.github.io/wot-discovery/#exploration-directory-api-things-deletion)                                                                                           | Deletes an [Anonymous Thing Description](https://w3c.github.io/wot-discovery/#exploration-directory-anonymous-td) with the corresponding request parameter `id`.                                                                                                                                     |                                                                                                                                            

### [Events APIs Endpoints](https://w3c.github.io/wot-discovery/#exploration-directory-api-notification)
| API Endpoint            | Method | Headers | Reference                                                                                          | Description                                                                                                                                                                                                                                                                                                                    |
|-------------------------|--------|---------|----------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `/events`               | `GET`  | `n/a`   | [Events Notification](https://w3c.github.io/wot-discovery/#exploration-directory-api-notification) | Subscribes to `all` the events (`creation`, `update`, `deletion`) notification supported by the server. The events are streamed via [Server-Sent Events (SSE)](https://www.w3.org/TR/2009/WD-eventsource-20091222/). Whenever a stored Thing Description is `created`/`updated`/`deleted` a notification will be sent via SSE. | 
| `/events/thing_created` | `GET`  | `n/a`   | [Events Notification](https://w3c.github.io/wot-discovery/#exploration-directory-api-notification) | Subscribes to the `cretion` events notification. The events are streamed via [Server-Sent Events (SSE)](https://www.w3.org/TR/2009/WD-eventsource-20091222/). Whenever a stored Thing Description is `created` a notification will be sent via SSE.                                                                            |
| `/events/thing_updated` | `GET`  | `n/a`   | [Events Notification](https://w3c.github.io/wot-discovery/#exploration-directory-api-notification) | Subscribes to the `update` events notification. The events are streamed via [Server-Sent Events (SSE)](https://www.w3.org/TR/2009/WD-eventsource-20091222/). Whenever a stored Thing Description is `updated` a notification will be sent via SSE.                                                                             |
| `/events/thing_deleted` | `GET`  | `n/a`   | [Events Notification](https://w3c.github.io/wot-discovery/#exploration-directory-api-notification) | Subscribes to the `deletion` events notification. The events are streamed via [Server-Sent Events (SSE)](https://www.w3.org/TR/2009/WD-eventsource-20091222/). Whenever a stored Thing Description is `deleted` a notification will be sent via SSE.                                                                           |

### [Search APIs Endpoints](https://w3c.github.io/wot-discovery/#exploration-directory-api-search)
| API Endpoint              | Method | Headers                                                                                                                                       | Reference                                                                            | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
|---------------------------|--------|-----------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `/search/sparql`          | `HEAD` | `n/a`                                                                                                                                         | [Semantic Search: SPARQL](https://w3c.github.io/wot-discovery/#search-semantic)      | Responds to the `HEAD` request for the _SPARQL Semantic Search_ route.                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `/search/sparql{query}`   | `GET`  | `Accept:` `application/sparql-results+json` or `application/sparql-results+xml` or `text/csv` or `text/tab-separated-values` or `text/turtle` | [Semantic Search: SPARQL](https://w3c.github.io/wot-discovery/#search-semantic)      | Solves the search of Thing Descriptions via a `SPARQL` query (following the [SPARQL query standards](https://www.w3.org/TR/sparql11-overview/)). Query results can be output in the following format, depending by the request header (JSON by default if no specific format is required). <br>`ASK` or `SELECT` queries: `JSON` (`application/sparql-results+json`), `XML` (`application/sparql-results+xml`), `CSV` (`text/csv`), `TSV` (`text/tab-separated-values`). <br> `CONSTRUCT` or `DESCRIBE` queries: `TURTLE` (`text/turtle`) |
| `/search/sparql{query}`   | `POST` | `Accept:` `application/sparql-results+json` or `application/sparql-results+xml` or `text/csv` or `text/tab-separated-values` or `text/turtle` | [Semantic Search: SPARQL](https://w3c.github.io/wot-discovery/#search-semantic)      | Solves the search of Thing Descriptions via a `SPARQL` query (following the [SPARQL query standards](https://www.w3.org/TR/sparql11-overview/)). Query results can be output in the following format, depending by the request header (JSON by default if no specific format is required). <br>`ASK` or `SELECT` queries: `JSON` (`application/sparql-results+json`), `XML` (`application/sparql-results+xml`), `CSV` (`text/csv`), `TSV` (`text/tab-separated-values`). <br> `CONSTRUCT` or `DESCRIBE` queries: `TURTLE` (`text/turtle`) |                                                                                    
| `/search/jsonpath{query}` | `GET`  | `Accept:` `application/sparql-results+json` or `application/sparql-results+xml` or `text/csv` or `text/tab-separated-values` or `text/turtle` | [Syntactic Search: JSONPath](https://w3c.github.io/wot-discovery/#jsonpath-semantic) | Solves the search of Thing Descriptions via a `JSONPath` query (following the [JSONPath query standards](https://datatracker.ietf.org/doc/html/draft-ietf-jsonpath-base)). Query results are sent as response in `JSON` format.                                                                                                                                                                                                                                                                                                           |
| `/search/xpath{query}`    | `GET`  | `Accept:` `application/sparql-results+json` or `application/sparql-results+xml` or `text/csv` or `text/tab-separated-values` or `text/turtle` | [Syntactic Search: XPath](https://w3c.github.io/wot-discovery/#xpath-semantic)       | Solves the search of Thing Descriptions via a `XPath` query (following the [XPath query standards](https://www.w3.org/TR/xpath-31/)). Query results are sent as response in `JSON` format.                                                                                                                                                                                                                                                                                                                                                |


## Development

### Testing Suite

This project has been tested via the testing suite at the following repository: [_farshidtz:
WoT Discovery Testing_](https://github.com/farshidtz/wot-discovery-testing)

Some editing has been done to the testing suite:

###### [search_test.go](https://github.com/farshidtz/wot-discovery-testing/blob/main/directory/search_test.go):
```
func TestSPARQL(t *testing.T) {

    const query = `select * { ?s ?p ?o }limit 5`
    ...
}
```
the query has been modified to support the RDF Named Graph structure as follows:
```
func TestSPARQL(t *testing.T) {

	const query = `SELECT ?s ?p ?o ?g WHERE { GRAPH ?g { ?s ?p ?o }	} LIMIT 5`
	...
}
```


## Release

---