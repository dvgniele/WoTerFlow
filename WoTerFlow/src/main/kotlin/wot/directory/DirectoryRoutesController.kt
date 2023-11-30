package wot.directory

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import utils.SSEUtils.Companion.respondSse
import utils.Utils
import wot.events.EventType

import wot.search.jsonpath.JsonPathController
import wot.search.sparql.SparqlController
import wot.search.xpath.XPathController

/**
 * Represents the [Directory] routes controller.
 */
class DirectoryRoutesController(private val directory: Directory) {
    /**
     * Setups the routes for the [Directory].
     *
     * @param route Routing tree.
     */
    fun setupRoutes(route: Route) {

        route.route("/things") {

            get(""){
                directory.thingController.retrieveAllThings(call)
            }

            get("/"){
                directory.thingController.retrieveAllThings(call)
            }

            head("") {
                directory.thingController.retrieveAllThings(call)
            }

            head("/") {
                directory.thingController.retrieveAllThings(call)
            }

            get("/{id}") {
                directory.thingController.retrieveThingById(call)
            }

            head("/{id}"){
                directory.thingController.retrieveThingById(call)
            }

            post(""){
                directory.thingController.registerAnonymousThing(call)
            }

            post("/"){
                directory.thingController.registerAnonymousThing(call)
            }

            put("/{id}") {
                directory.thingController.updateThing(call)
            }

            patch("/{id}") {
                directory.thingController.patchThing(call)
            }

            delete("/{id}") {
                directory.thingController.deleteThing(call)
            }
        }

        route.route("/search") {

            head("/sparql") {
                call.respond(HttpStatusCode.OK)
            }

            get("/sparql") {
                SparqlController.executeSparqlQuery(call, directory.db)
            }

            get("/sparql/") {
                SparqlController.executeSparqlQuery(call, directory.db)
            }

            post("/sparql") {
                SparqlController.executeSparqlQuery(call, directory.db)
            }

            get("/jsonpath") {
                JsonPathController.executeJsonPathQuery(call, directory.jsonMap)
            }

            get("/xpath") {
                XPathController.executeXPathQuery(call, directory.jsonMap)
            }
        }

        route.route("/events"){

            get("") {
                if (!Utils.rejectedDiff(call)) {
                    val lastEventId = call.request.headers["Last-Event-ID"]

                    val eventsList = directory.eventController.getPastEvents(
                        lastEventId,
                        EventType.THING_CREATED,
                        EventType.THING_UPDATED,
                        EventType.THING_DELETED
                    )

                    call.respondSse(
                        eventsList,
                        EventType.THING_CREATED to directory.eventController.thingCreatedSseFlow,
                        EventType.THING_UPDATED to directory.eventController.thingUpdatedSseFlow,
                        EventType.THING_DELETED to directory.eventController.thingDeletedSseFlow)
                }
            }

            get("/thing_created") {
                if (!Utils.rejectedDiff(call)) {
                    val lastEventId = call.request.headers["Last-Event-ID"]

                    val eventsList = directory.eventController.getPastEvents(
                        lastEventId,
                        EventType.THING_CREATED
                    )

                    call.respondSse(
                        eventsList,
                        EventType.THING_CREATED to directory.eventController.thingCreatedSseFlow
                    )
                }
            }

            get("/thing_updated") {
                if (!Utils.rejectedDiff(call)) {
                    val lastEventId = call.request.headers["Last-Event-ID"]

                    val eventsList = directory.eventController.getPastEvents(
                        lastEventId,
                        EventType.THING_UPDATED
                    )

                    call.respondSse(
                        eventsList,
                        EventType.THING_UPDATED to directory.eventController.thingUpdatedSseFlow
                    )
                }
            }

            get("/thing_deleted") {
                val lastEventId = call.request.headers["Last-Event-ID"]

                val eventsList = directory.eventController.getPastEvents(
                    lastEventId,
                    EventType.THING_DELETED
                )

                call.respondSse(
                    eventsList,
                    EventType.THING_DELETED to directory.eventController.thingDeletedSseFlow
                )
            }
        }

        route.route("") {
            handle {
                call.respondRedirect("/things", permanent = false)
            }
        }

        route.route("/") {
            handle {
                call.respondRedirect("/things", permanent = false)
            }
        }
    }
}

