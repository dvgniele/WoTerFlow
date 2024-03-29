package wot.td

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import errors.ErrorDetails
import errors.ValidationError
import exceptions.ConversionException
import exceptions.ThingException
import exceptions.ValidationException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpMethod
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.httpMethod
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.response.header
import org.apache.jena.atlas.lib.NotImplemented
import org.apache.jena.shared.NotFoundException
import utils.Utils
import wot.directory.DirectoryConfig
import wot.events.EventController
import wot.events.EventType

/**
 * Controller responsible for managing Thing Descriptions.
 *
 * @param service The [ThingDescriptionService] to use to execute operations on [Thing Descriptions](https://www.w3.org/TR/wot-thing-description/#introduction-td)
 * @param eventController The [EventController] to handle the Server-Sent Events (SSE)
 */
class ThingDescriptionController(service: ThingDescriptionService, private val eventController: EventController) {

    private val ts = service

    /**
     * Retrieves all the [Thing Descriptions](https://www.w3.org/TR/wot-thing-description/#introduction-td).
     *
     * @param call The [ApplicationCall] representing the HTTP request.
     */
    suspend fun retrieveAllThings(call: ApplicationCall) {
        runCatching {
            val request = call.request

            val responseFormat = request.queryParameters["format"]
            val limit = request.queryParameters["limit"]?.toInt() ?: 20
            val offset = request.queryParameters["offset"]?.toInt() ?: 0
            val ordering = request.queryParameters["order"]

            if (ordering != null && ordering != "null") {
                throw NotImplemented("Ordering is not supported")
            }

            call.response.header(HttpHeaders.ContentType, "application/ld+json")

            when (call.request.httpMethod) {
                HttpMethod.Head -> {
                    call.respond(HttpStatusCode.OK)
                }

                HttpMethod.Get -> {
                    call.response.status(HttpStatusCode.OK)

                    val things = ts.retrieveAllThings()

                    val responseBody = if (responseFormat == "collection") {
                        val collectionResponse = generateCollectionResponse(call, things.size, offset, limit)
                        collectionResponse.putArray("members").addAll(things.map { ObjectMapper().valueToTree(it) })
                        collectionResponse
                    } else {
                        generateListingResponse(call, things.size, offset, limit)
                        things
                    }

                    call.respond(responseBody)
                }
            }
        }.onFailure { e ->
            handleException(e, call)
        }
    }

    /**
     * Generates the Listing Response.
     *
     * @param call The [ApplicationCall] representing the HTTP request.
     * @param count The total number of resources available.
     * @param offset The current offset indicating the starting point of the resource list.
     * @param limit The maximum number of resources to include in a single response.
     */
    private fun generateListingResponse(call: ApplicationCall, count: Int, offset: Int, limit: Int) {
        //  Calculate the new offset for the next page, or set it to null if there's no next page
        val newOffset = if (offset + limit >= count) offset + limit else null
        val linkHeader = buildLinkHeader(newOffset, limit)

        //  Append the Link Header to the response
        call.response.headers.append(HttpHeaders.Link, linkHeader)
    }

    /**
     * Generates the Collection Response
     *
     * @param call The [ApplicationCall] representing the HTTP request.
     * @param count The total number of resources available.
     * @param offset The current offset indicating the starting point of the resource list.
     * @param limit The maximum number of resources to include in a single response.
     *
     * @return An [ObjectNode] representing the collection response in JSON format.
     */
    private fun generateCollectionResponse(call: ApplicationCall, count: Int, offset: Int, limit: Int): ObjectNode {
        //  Create an ObjectNode to represent the collection response.
        return Utils.jsonMapper.createObjectNode().apply {
            put("@context", DirectoryConfig.contextV11)
            put("@type", "ThingCollection")
            put("total", count)
            put("@id", "/things?offset=$offset&limit=$limit&format=collection")

            //  calculate the next offset for the next page, if applicable
            val nextOffset = offset + limit
            if (nextOffset <= count) {
                put("next", "/things?offset=$nextOffset&limit=$limit&format=collection")
            }
        }.also { call.response.header(HttpHeaders.Link, buildLinkHeader(offset, limit)) }
    }

    /**
     * Builds the Link header for pagination, indicating the next page if applicable.
     *
     * @param offset The current offset indicating the starting point of the next page.
     * @param limit The maximum number of resources to include in a single page.
     * @return A [String] representing the Link header for pagination.
     */
    private fun buildLinkHeader(offset: Int?, limit: Int): String {
        return offset?.let { "</things?offset=$it&limit=$limit>; rel=\"next\"" } ?: ""
    }

    /**
     * Looks-up a [Thing Descriptions](https://www.w3.org/TR/wot-thing-description/#introduction-td) by its UUID.
     *
     * @param call The [ApplicationCall] representing the HTTP request.
     */
    suspend fun retrieveThingById(call: ApplicationCall) {
        runCatching {
            val id = Utils.hasValidId(call.parameters["id"])
            //val id = idValid.substringAfterLast("h")

            call.response.header(HttpHeaders.ContentType, "application/td+json")

            when (call.request.httpMethod){
                HttpMethod.Head -> {
                    val retrievedThing = ts.checkIfThingExists(id)

                    call.respond(if (retrievedThing) HttpStatusCode.OK else HttpStatusCode.NotFound)
                }
                HttpMethod.Get -> {
                    val retrievedThing = ts.retrieveThingById(id)

                    val json = Utils.jsonMapper.writeValueAsString(retrievedThing)
                    call.respondText(json, ContentType.Application.Json, HttpStatusCode.OK)
                }
            }
        }.onFailure { e ->
            handleException(e, call)
        }
    }

    /**
     * Registers an Anonymous [Thing Descriptions](https://www.w3.org/TR/wot-thing-description/#introduction-td) with a unique UUID
     *
     * @param call The [ApplicationCall] representing the HTTP request.
     */
    suspend fun registerAnonymousThing(call: ApplicationCall) {
        runCatching {
            val request = call.request

            if (!Utils.hasJsonContent(request.header(HttpHeaders.ContentType))) {
                throw ThingException(
                    "ContentType not supported. application/td+json required",
                    HttpStatusCode.UnsupportedMediaType
                )
            }

            val thing = Utils.hasBody(call.receive())

            if (thing != null) {
                val requestBodyId: String? = thing.get("@id")?.takeIf { it.isTextual }?.asText()
                    ?: thing.get("id")?.takeIf { it.isTextual }?.asText()

                if (requestBodyId != null) {
                    throw ThingException("The thing must NOT have a 'id' or '@id' property")
                }

                val pair = ts.insertAnonymousThing(thing)

                call.response.header(HttpHeaders.Location, pair.first)
                call.respond(HttpStatusCode.Created)

                eventController.notify(EventType.THING_CREATED, "{\n\"id\": \"${pair.first}\"\n}")
            }
        }.onFailure { e ->
            handleException(e, call)
        }
    }

    /**
     * Creates or Updates (if already existing) a [Thing Descriptions](https://www.w3.org/TR/wot-thing-description/#introduction-td) with a given `UUID`
     *
     * @param call The [ApplicationCall] representing the HTTP request.
     */
    suspend fun updateThing(call: ApplicationCall) {
        runCatching {
            val request = call.request

            if (!Utils.hasJsonContent(request.header(HttpHeaders.ContentType))) {
                throw ThingException(
                    "ContentType not supported. application/td+json required",
                    HttpStatusCode.UnsupportedMediaType
                )
            }

            val thing = Utils.hasBody(call.receive())

            if (thing != null) {
/*
                val requestBodyId = thing.get("@id")?.takeIf { it.isTextual }?.asText()
                    ?: thing.get("id")?.takeIf { it.isTextual }?.asText()


                if (requestBodyId != null) {
                    //throw ThingException("The thing must NOT have a 'id' or '@id' property")
                }
*/

                val thingUpdate = ts.updateThing(thing)
                val thingId = thingUpdate.first
                val thingExists = thingUpdate.second

                call.response.header(HttpHeaders.Location, thingId)

                if (!thingExists) {
                    call.respond(HttpStatusCode.Created)
                    eventController.notify(EventType.THING_CREATED, "{ \n\"id\": \"${thingUpdate.first}\" }")
                }
                else {
                    call.respond(HttpStatusCode.NoContent)
                    eventController.notify(EventType.THING_UPDATED, "{ \n\"id\": \"${thingUpdate.first}\" }")
                }
            }
        }.onFailure { e ->
            handleException(e, call)
        }
    }

    /**
     * Partially updates a [Thing Descriptions](https://www.w3.org/TR/wot-thing-description/#introduction-td)
     *
     * @param call The [ApplicationCall] representing the HTTP request.
     */
    suspend fun patchThing(call: ApplicationCall) {
        runCatching {
            val id = Utils.hasValidId(call.parameters["id"])
            val request = call.request

            if (!Utils.hasJsonContent(request.header(HttpHeaders.ContentType))) {
                throw ThingException(
                    "ContentType not supported. application/td+json required",
                    HttpStatusCode.UnsupportedMediaType
                )
            }

            val thing = Utils.hasBody(call.receive())

            if (thing != null) {
                val thingId = ts.patchThing(thing, id)
                call.response.header(HttpHeaders.Location, thingId)

                call.respond(HttpStatusCode.NoContent)
                eventController.notify(EventType.THING_UPDATED, "{ \n\"id\": \"${thingId}\" }")
            }
        }.onFailure { e ->
            handleException(e, call)
        }
    }

    /**
     * Deletes a [Thing Descriptions](https://www.w3.org/TR/wot-thing-description/#introduction-td) from the Triple-Store
     *
     * @param call The [ApplicationCall] representing the HTTP request.
     */
    suspend fun deleteThing(call: ApplicationCall) {

        val id = call.parameters["id"] ?: throw ThingException("Missing thing ID")
        //val id = requestId.substringAfterLast("h")

        runCatching {
            ts.deleteThingById(id)
            call.respond(HttpStatusCode.NoContent)
            eventController.notify(EventType.THING_DELETED, "{ \n\"id\": \"${id}\" }")
        }.onFailure { e ->
            handleException(e, call)
        }
    }

    /**
     * Extension function to [ApplicationCall] class. Responds to the client with an error status along with error details.
     *
     * @param status The HTTP status code for the error response.
     * @param title The title of the error.
     * @param detail The detailed description of the error.
     */
    private suspend fun ApplicationCall.respondError(status: HttpStatusCode, title: String, detail: String){
        val errorDetails = ErrorDetails(
            title = title,
            status = status.value,
            detail = detail
        )
        respond(status, errorDetails)
    }

    /**
     * Extension function to [ApplicationCall] class. Responds to the client with a validation error status along with validation error details.
     *
     * @param errors The list of validation errors.
     */
    private suspend fun ApplicationCall.respondValidationError(errors: List<ValidationError>?){
        val errorDetails = ErrorDetails(
            title = "Validation Exception",
            status = HttpStatusCode.BadRequest.value,
            detail = "The input did not pass the Schema Validation",
            validationErrors = errors
        )
        respond(HttpStatusCode.BadRequest, errorDetails)
    }

    /**
     * Handles exceptions by responding with an appropriate error status and details.
     *
     * @param e The thrown exception.
     * @param call The current application call context.
     */
    private suspend inline fun handleException(e: Throwable, call: ApplicationCall) {
        when (e) {
            is ThingException, is BadRequestException, is ConversionException -> call.respondError(HttpStatusCode.BadRequest, "Bad Request", e.message ?: "")
            is ValidationException -> call.respondValidationError(e.errors)
            is NotFoundException -> call.respondError(HttpStatusCode.NotFound, "Not Found", e.message ?: "")
            else -> call.respondError(HttpStatusCode.InternalServerError, "Internal Server Error", e.message ?: "")
        }
    }
}