
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
import org.apache.jena.tdb.TDBFactory
import org.mapdb.DB
import org.mapdb.DBMaker
import org.slf4j.event.Level
import wot.td.ThingDescriptionController
import wot.td.ThingDescriptionRoutesController
import wot.td.ThingDescriptionService
import java.io.File
import javax.xml.crypto.Data

fun listFilesInFolder(folderPath: String) {
    println("verifying what files are there")
    val folder = File(folderPath)

    if (folder.exists() && folder.isDirectory) {
        val files = folder.listFiles()

        if (files != null) {
            for (file in files) {
                if (file.isFile) {
                    println("File: ${file.name}")
                } else if (file.isDirectory) {
                    println("Directory: ${file.name}")
                }
            }
        } else {
            println("Folder is empty.")
        }
    } else {
        println("Folder does not exist or is not a directory.")
    }
}


fun main(args: Array<String>) {
    //listFilesInFolder("data/validation/")

    RIOT.init()

    println("Program arguments: ${args.joinToString()}")

    val rdf_db: Dataset = TDBFactory.createDataset("data/tdb-data")
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


    //val client = RestClient("http://127.0.0.1:5000/")




    embeddedServer(CIO, port = 8081){
        install(CallLogging){
            level = Level.INFO
            filter { call -> call.request.path().startsWith("/")}
        }

        install(ContentNegotiation){
            jackson()
        }

        val ts = ThingDescriptionService(rdf_db, json_db)
        val tc = ThingDescriptionController(rdf_db, json_db, ts)

        ts.refreshJsonDb()
        //tc.initializeDatabaseIfNeeded()

        val routesController = ThingDescriptionRoutesController(tc)

        routing {
            routesController.setupRoutes(this)

            /*
            get("/") {
                call.respondText("Hello, Ktor CIO!")
            }

            get("/json") {
                val jsonData = MyData("Hello, JSON!")
                call.respond(jsonData)
            }

            get("/query") {
                dataset.begin(ReadWrite.READ)
                try {
                    val model = dataset.defaultModel
                    val iter = model.listStatements()

                    val items = mutableListOf<Map<String, String>>()

                    while (iter.hasNext()) {
                        val statement = iter.nextStatement()
                        val subject = statement.subject.toString()
                        val predicate = statement.predicate.toString()
                        val obj = statement.`object`.toString()

                        val itemMap = mapOf("subject" to subject, "predicate" to predicate, "object" to obj)
                        items.add(itemMap)
                    }

                    call.respond(items)
                } finally {
                    dataset.end()
                }
            }



            put("/query") {
                val newData = call.receive<Map<String, Any>>()

                val id = newData["id"] as String
                val name = newData["name"] as String
                val description = newData["description"] as String

                dataset.begin(ReadWrite.WRITE)
                try {
                    val model = dataset.defaultModel
                    val subject = model.createResource(id)
                    subject.addProperty(model.createProperty("http://example.org/wot#", "name"), name)
                    subject.addProperty(model.createProperty("http://example.org/wot#", "description"), description)

                    dataset.commit()
                    call.respond(HttpStatusCode.OK, "Data inserted successfully")
                } finally {
                    dataset.end()
                }
            }


            get("/sparql"){
                val queryString = """
                    PREFIX wot: <http://example.org/wot#>
                    SELECT ?id ?name ?description
                    WHERE {
                        GRAPH <http://example/td-graph> {
                            ?id wot:name ?name;
                                wot:description ?description .
                        }
                    }
                """.trimIndent()

                dataset.begin(ReadWrite.READ)
                try {
                    val query = QueryFactory.create(queryString)
                    val queryExecution = QueryExecutionFactory.create(query, dataset)
                    val results = queryExecution.execSelect()

                    val items = mutableListOf<Map<String, String>>()
                    while (results.hasNext()) {
                        val solution = results.nextSolution()
                        val id = solution.getResource("id").uri
                        val name = solution.getLiteral("name").string
                        val description = solution.getLiteral("description").string

                        val itemMap = mapOf("id" to id, "name" to name, "description" to description)
                        items.add(itemMap)
                    }

                    call.respond(items)
                } finally {
                    dataset.end()
                }

            }

            put("/sparql") {
                val tdData = call.receive<Map<String, Any>>()

                val id = tdData["id"] as String
                val name = tdData["name"] as String
                val description = tdData["description"] as String

                val updateQueryString = """
                    PREFIX wot: <http://example.org/wot#>
                    INSERT DATA
                    {
                        GRAPH <http://example/td-graph>
                        {
                            <$id> wot:name "$name";
                                    wot:description "$description" .
                        }
                    }
                    """.trimIndent()

                dataset.begin(ReadWrite.WRITE)
                try {
                    val update = UpdateFactory.create(updateQueryString)
                    UpdateAction.execute(update, dataset)
                    dataset.commit()
                    call.respond(HttpStatusCode.OK, "Thing Description inserted successfully")
                } finally {
                    dataset.end()
                }
            }

             */
        }
    }.start(wait = true)

    /*

    println("get:" + client.get("gettest"))

    val requestBody = mapOf(
        "test" to "post"
    )
    val requestBodyJson = Gson().toJson(requestBody)

    println("post:" + client.post("posttest", requestBodyJson))
     */

    //server.stop()

}