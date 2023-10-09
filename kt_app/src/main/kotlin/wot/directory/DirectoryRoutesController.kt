package wot.directory

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import wot.search.SparqlController
import wot.td.ThingDescriptionController

class DirectoryRoutesController(thingDescriptionController: ThingDescriptionController) {
    val tc = thingDescriptionController

    fun setupRoutes(route: Route) {
        route.route("/things") {

            get(""){
                tc.retrieveAllThings(call)
                //call.respond(HttpStatusCode.NotImplemented)
            }

            get("/"){
                tc.retrieveAllThings(call)
                //call.respond(HttpStatusCode.NotImplemented)
            }

            head("") {
                tc.retrieveAllThings(call)
            }

            head("/") {
                tc.retrieveAllThings(call)
            }

            get("/{id}") {
                tc.retrieveThingById(call)
                //call.respond(HttpStatusCode.NotImplemented)
            }

            get("/http://example.com/ktwot/graph/{id}") {
                tc.retrieveThingById(call)
            }

            head("/{id}"){
                tc.retrieveThingById(call)
            }

            post(""){
                tc.registerAnonymousThing(call)
            }

            post("/"){
                tc.registerAnonymousThing(call)
            }

            put("/{id}") {
                tc.updateThing(call)
                //call.respond(HttpStatusCode.NotImplemented)
            }

            patch("/{id}") {
                tc.patchThing(call)
                //call.respond(HttpStatusCode.NotImplemented)
            }

            delete("/{id}") {
                tc.deleteThing(call)
                //call.respond(HttpStatusCode.NotImplemented)
            }

            delete("/http://example.com/ktwot/graph/{id}") {
                tc.deleteThing(call)
                //call.respond(HttpStatusCode.NotImplemented)
            }
        }

        route.route("/search") {

            get("/sparql") {
                SparqlController.executeSparqlQuery(call)
            }

            post("/sparql") {

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