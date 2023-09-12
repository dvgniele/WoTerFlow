package wot.td

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import exceptions.ThingException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.apache.jena.atlas.lib.NotImplemented
import org.apache.jena.query.Dataset
import org.apache.jena.shared.NotFoundException
import org.mapdb.DB
import utils.Utils

class ThingDescriptionController(dbRdf: Dataset, dbJson: DB?) {

    val ts = ThingDescriptionService(dbRdf, dbJson)
    private var utils: Utils = Utils()

    suspend fun retrieveAllThings(call: ApplicationCall) {
        val request = call.request

        if (!utils.hasJsonContent(request.header(HttpHeaders.Accept))) {
            call.respond(HttpStatusCode.BadRequest, "Unsupported content type")
        }

        val responseFormat = request.queryParameters["format"]
        val limit = request.queryParameters["limit"]?.toInt() ?: 20
        val offset = request.queryParameters["offset"]?.toInt() ?: 0
        val ordering = request.queryParameters["order"]

        if (ordering != null && ordering != "null") {
            throw NotImplemented("Ordering is not supported")
        }

        call.response.header(HttpHeaders.ContentType, "application/ld+json")
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

    private fun generateListingResponse(call: ApplicationCall, count: Int, offset: Int, limit: Int) {
        val newOffset = if (offset + limit >= count) offset + limit else null
        val linkHeader = buildLinkHeader(newOffset, limit)

        call.response.headers.append(HttpHeaders.Link, linkHeader)
    }

    private fun generateCollectionResponse(call: ApplicationCall, count: Int, offset: Int, limit: Int): ObjectNode {
        return utils.jsonMapper.createObjectNode().apply {
            put("@context", "https://w3c.github.io/wot-discovery/context/discovery-context.jsonld")
            put("@type", "ThingCollection")
            put("total", count)
            put("@id", "/things?offset=$offset&limit=$limit&format=collection")
            val nextOffset = offset + limit
            if (nextOffset <= count) {
                put("next", "/things?offset=$nextOffset&limit=$limit&format=collection")
            }
        }.also { call.response.header(HttpHeaders.Link, buildLinkHeader(offset, limit)) }
    }

    private fun buildLinkHeader(offset: Int?, limit: Int): String {
        return offset?.let { "</things?offset=$it&limit=$limit>; rel=\"next\"" } ?: ""
    }

    suspend fun retrieveThingById(call: ApplicationCall) {
        try {
            val id = utils.hasValidId(call.parameters["id"])

            if (!utils.hasJsonContent(call.request.header(HttpHeaders.Accept))) {
                throw ThingException("Unsupported content type")
            }

            val retrievedThing = ts.retrieveThingById(id)

            val json = utils.jsonMapper.writeValueAsString(retrievedThing)
            call.respondText(json, ContentType.Application.Json, HttpStatusCode.OK)
        } catch (e: ThingException) {
            call.respond(HttpStatusCode.BadRequest, e.message.toString())
        } catch (e: NotFoundException) {
            call.respond(HttpStatusCode.NotFound, "Requested Thing not found")
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "An error occurred: ${e.message}")
        }

    }

    suspend fun registerAnonymousThing(call: ApplicationCall) {
        try {
            val request = call.request


            if (!utils.hasJsonContent(request.header(HttpHeaders.ContentType))) {
                throw BadRequestException("Unsupported content type")
            }

            if (utils.hasJsonContent(call.request.header(HttpHeaders.ContentType))) {
                val thing = utils.hasBody(call.receive())

                if (thing != null) {
                    val requestBodyId: String? = thing.get("@id")?.takeIf { it.isTextual }?.asText()
                        ?: thing.get("id")?.takeIf { it.isTextual }?.asText()

                    if (requestBodyId != null) {
                        throw BadRequestException("The thing must NOT have a 'id' or '@id' property.")
                    }

                    if (!thing.has("title")) {
                        throw BadRequestException("The thing must have a 'title' property.")
                    }

                    val thingId = ts.insertAnonymousThing(thing)
                    call.response.header(HttpHeaders.Location, thingId)
                }
            } else {
                throw ThingException("ContentType not supported. application/td+json required.")
            }
            call.respond(HttpStatusCode.Created, "Anonymous Thing created successfully")
        } catch (e: ThingException) {
            call.respond(HttpStatusCode.BadRequest, "${e.message}")
        } catch (e: BadRequestException) {
            call.respond(HttpStatusCode.BadRequest, "${e.message}")
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "${e.message}")
        }
    }

    suspend fun updateThing(call: ApplicationCall) {
        try {
            val id = utils.hasValidId(call.parameters["id"])
            val request = call.request

            if (!utils.hasJsonContent(request.header(HttpHeaders.ContentType))) {
                throw ThingException("Unsupported content type.")
            }

            if (!utils.hasJsonContent(call.request.header(HttpHeaders.ContentType))) {
                throw ThingException("ContentType not supported. application/td+json required.")
            }

            val thing = utils.hasBody(call.receive()) ?: throw BadRequestException("Invalid JSON body")

            val requestBodyId = thing.get("@id")?.takeIf { it.isTextual }?.asText()
                ?: thing.get("id")?.takeIf { it.isTextual }?.asText()

            if (requestBodyId == null || requestBodyId != id) {
                throw BadRequestException("IDs do not match. The provided id in the JSON body does not match the id in the request URL.")
            }

            if (!thing.has("title")) {
                throw BadRequestException("The thing must have a 'title' property.")
            }

            val thingId = ts.updateThing(thing)
            call.response.header(HttpHeaders.Location, thingId)

            call.respond(HttpStatusCode.Created, "Thing updated successfully")
        } catch (e: ThingException) {
            call.respond(HttpStatusCode.BadRequest, "${e.message}")
        } catch (e: BadRequestException) {
            call.respond(HttpStatusCode.BadRequest, "${e.message}")
        } catch (e: NotFoundException) {
            call.respond(HttpStatusCode.NotFound, "${e.message}")
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "${e.message}")
            println("Error during Thing Update: ${e.localizedMessage}")
        }
    }

    suspend fun patchThing(call: ApplicationCall) {
        try {
            val id = utils.hasValidId(call.parameters["id"])
            val request = call.request

            if (!utils.hasJsonContent(request.header(HttpHeaders.ContentType))) {
                throw ThingException("Unsupported content type.")
            }

            if (!utils.hasJsonContent(call.request.header(HttpHeaders.ContentType))) {
                throw ThingException("ContentType not supported. application/td+json required.")
            }

            val thing = utils.hasBody(call.receive()) ?: throw BadRequestException("Invalid JSON body")

            val requestBodyId = thing.get("@id")?.takeIf { it.isTextual }?.asText()
                ?: thing.get("id")?.takeIf { it.isTextual }?.asText()

            if (requestBodyId == null || requestBodyId != id) {
                throw BadRequestException("IDs do not match. The provided id in the JSON body does not match the id in the request URL.")
            }

            if (!thing.has("title")) {
                throw BadRequestException("The thing must have a 'title' property.")
            }

            val thingId = ts.patchThing(thing)
            call.response.header(HttpHeaders.Location, thingId)

            call.respond(HttpStatusCode.Created, "Thing patched successfully")
        } catch (e: ThingException) {
            call.respond(HttpStatusCode.BadRequest, "${e.message}")
        } catch (e: BadRequestException) {
            call.respond(HttpStatusCode.BadRequest, "${e.message}")
        } catch (e: NotFoundException) {
            call.respond(HttpStatusCode.NotFound, "${e.message}")
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "${e.message}")
            println("Error during Thing Patch: ${e.localizedMessage}")
        }
    }

    suspend fun deleteThing(call: ApplicationCall) {
        val id = call.parameters["id"] ?: throw ThingException("Missing thing ID")

        try {
            ts.deleteThingById(id)
            call.respond(HttpStatusCode.NoContent, "Thing deleted successfully")
        } catch (e: ThingException) {
            call.respond(HttpStatusCode.BadRequest, "${e.message}")
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "${e.message}")
        }
    }


    /*
    fun initializeDatabaseIfNeeded(){
        try {
            ts.initializeDatabaseIfNeeded()
        } catch (e: Exception){
            println(e.message)
        }
    }

     */


    /*
    suspend fun retrieveAnything(call: ApplicationCall){
        val request= call.request

        val data = ts.retrieveAnything()

        call.respond(data)
    }
     */
}