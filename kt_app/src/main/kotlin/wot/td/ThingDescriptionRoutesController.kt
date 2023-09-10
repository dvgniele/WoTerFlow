package wot.td

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

class ThingDescriptionRoutesController(thingDescriptionController: ThingDescriptionController) {
    val tc = thingDescriptionController

    fun setupRoutes(route: Route) {
        route.route("/things") {

            get(""){
                //tc.retrieveAllThings(call)
                call.respond(HttpStatusCode.NotImplemented)
            }

            get("/"){
                //tc.retrieveAllThings(call)
                call.respond(HttpStatusCode.NotImplemented)
            }

            get("/{id}") {
                //tc.retrieveThingById(call)
                call.respond(HttpStatusCode.NotImplemented)
            }

            post(""){
                tc.registerAnonymousThing(call)
            }

            post("/"){
                tc.registerAnonymousThing(call)
            }

            put("/{id}") {
                //tc.updateThing(call)
                call.respond(HttpStatusCode.NotImplemented)
            }

            patch("/{id}") {
                //tc.patchThing(call)
                call.respond(HttpStatusCode.NotImplemented)
            }

            delete("/{id}") {
                //tc.deleteThing(call)
                call.respond(HttpStatusCode.NotImplemented)
            }
        }
    }
}