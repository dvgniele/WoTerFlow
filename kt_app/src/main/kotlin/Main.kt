import com.google.gson.Gson
import restutils.RestClient

fun main(args: Array<String>) {
    println("Program arguments: ${args.joinToString()}")

    val client = RestClient("http://127.0.0.1:5000/")
    println("get:" + client.get("gettest"))

    val requestBody = mapOf(
        "test" to "post"
    )
    val requestBodyJson = Gson().toJson(requestBody)

    println("post:" + client.post("posttest", requestBodyJson))

}