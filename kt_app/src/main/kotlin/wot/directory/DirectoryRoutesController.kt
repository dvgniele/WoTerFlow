package wot.directory

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import wot.search.SparqlController
import wot.td.ThingDescriptionController

class DirectoryRoutesController(directory: Directory) {
    val directory = directory

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

            get("/http://example.com/ktwot/graph/{id}") {
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

            delete("/http://example.com/ktwot/graph/{id}") {
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