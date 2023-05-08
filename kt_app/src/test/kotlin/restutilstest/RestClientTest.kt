package restutilstest

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import restutils.RestClient
import kotlin.test.assertEquals

class RestClientTest (){

    private lateinit var restClient: RestClient

    @BeforeEach
    fun setup(){
        restClient = RestClient("https://jsonplaceholder.typicode.com")
    }

    @Test
    fun testGet(){
        val response = restClient.get("/todos/1")

        val expected = """
        {
          "userId": 1,
          "id": 1,
          "title": "delectus aut autem",
          "completed": false
        }
    """.trimIndent()

        assertEquals(expected, response)
    }

    @Test
    fun testPost() {
        val body = mapOf(
            "title" to "foo",
            "body" to "bar",
            "userId" to 1
        )
        val response = restClient.post("/posts", body).trimIndent()
        val expected = """
        {
          "title": "foo",
          "body": "bar",
          "userId": 1,
          "id": 101
        }
    """.trimIndent()

        //val expected = Gson().toJson(expectedMap)
        assertEquals(expected, response)
    }


}