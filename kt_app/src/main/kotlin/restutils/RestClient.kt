package restutils

import io.restassured.RestAssured
import io.restassured.http.ContentType

class RestClient (private val baseUrl: String){
    fun get(path: String): String{
        return RestAssured.given()
            .contentType(ContentType.JSON)
            .baseUri(baseUrl)
            .basePath(path)
            .`when`()
            .get()
            .then()
            .statusCode(200)
            .extract()
            .asString()
    }

    fun post(path: String, body: Any): String{
        val response = RestAssured.given()
            .contentType(ContentType.JSON)
            .baseUri(baseUrl)
            .basePath(path)
            .body(body)
            .`when`()
            .post()

        val statusCode = response.statusCode

        if (statusCode !in 200..299){
            throw RuntimeException("Failed to send POST request with status code $statusCode")
        }

        return response.asString()
    }
}