
//import org.slf4j.event.Level

import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.apache.jena.fuseki.main.FusekiServer
import org.apache.jena.query.Dataset
import org.apache.jena.query.ReadWrite
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.RIOT
import org.apache.jena.tdb2.TDB2Factory
import org.slf4j.event.Level
import wot.directory.Directory
import wot.directory.DirectoryRoutesController
import wot.search.SparqlController
import wot.td.ThingDescriptionController
import wot.td.ThingDescriptionService


fun main(args: Array<String>) {
    RIOT.init()

    println("Program arguments: ${args.joinToString()}")

    val rdf_db: Dataset = TDB2Factory.connectDataset("data/tdb-data")

    val json_db = null
    /*
    val json_db: DB = DBMaker
        .fileDB("data/json-data.db")
        .transactionEnable()
        .make()
     */

    val model: Model = ModelFactory.createDefaultModel()
    model.read("data/tdb-data/turtle.ttl")

    rdf_db.begin(ReadWrite.WRITE)
    try{
        val tdbModel = rdf_db.defaultModel
        tdbModel.add(model)
        tdbModel.commit()
    } finally {
        rdf_db.end()
    }

    val server = FusekiServer.create()
        .add("/rdf", rdf_db)
        .build()

    server.start()

    embeddedServer(CIO, port = 8081){
        install(CallLogging){
            level = Level.INFO
            filter { call -> call.request.path().startsWith("/")}
        }

        install(ContentNegotiation){
            jackson()
        }

        val ts = ThingDescriptionService(rdf_db)
        val tc = ThingDescriptionController(ts)

        val directory = Directory(rdf_db, tc)

        ts.refreshJsonDb()
        //tc.initializeDatabaseIfNeeded()

        val routesController = DirectoryRoutesController(directory)

        routing {
            routesController.setupRoutes(this)

        }
    }.start(wait = true)

}