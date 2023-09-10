package wot

import io.restassured.RestAssured
import io.restassured.response.Response
import io.restassured.specification.RequestSpecification
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


class DiscovererTest {
    private lateinit var discoverer: Discoverer
    private lateinit var requestSpecification: RequestSpecification


    @BeforeEach
    fun setup(){
        RestAssured.baseURI = "uri"
        requestSpecification = RestAssured.given().log().all()
        discoverer = Discoverer()
    }

    @Test
    fun testDiscovererMustSupportIntros(){
        // Test code for discoverer-must-support-intros
        val response: Response = RestAssured.get("/introspection")

        assertEquals(200, response.statusCode)
        assertNotNull(response.body().asString())
    }

    @Test
    fun testDiscovererMustSupportFetching(){
        // Test code for discoverer-must-support-fetching

        val response: Response = RestAssured.get("/fetching")

        assertEquals(200, response.statusCode)
        assertNotNull(response.body().asString())
    }

    @Test
    fun testDiscovererMayMultipleIntro() {
        // Test code for discoverer-may-multiple-intro

        val response: Response = RestAssured.get("/multiple-intro")

        assertEquals(200, response.statusCode)
        assertNotNull(response.body().asString())
    }

    @Test
    fun testDiscovererMergeIntros() {
        // Test code for discoverer-merge-intros
        val response: Response = RestAssured.get("/merge-intros")

        assertEquals(200, response.statusCode)
        assertNotNull(response.body().asString())
    }

    @Test
    fun testDiscovererTDIdentify() {
        // Test code for discoverer-td-identify
        val response: Response = RestAssured.get("/td-identify")

        assertEquals(200, response.statusCode)
        assertNotNull(response.body().asString())
    }

    @Test
    fun testDiscovererFetchTDD() {
        // Test code for discoverer-fetch-tdd

        val response: Response = RestAssured.get("/fetch-tdd")

        assertEquals(200, response.statusCode)
        assertNotNull(response.body().asString())
    }

    @Test
    fun testDiscovererFetchLinks() {
        // Test code for discoverer-fetch-links
        val response: Response = RestAssured.get("/fetch-links")

        assertEquals(200, response.statusCode)
        assertNotNull(response.body().asString())
    }

    @Test
    fun testDiscovererFetchIteration() {
        // Test code for discoverer-fetch-iteration
        val response: Response = RestAssured.get("/fetch-iteration")

        assertEquals(200, response.statusCode)
        assertNotNull(response.body().asString())
    }

    @Test
    fun testDiscovererTermination() {
        // Test code for discoverer-termination
        val response: Response = RestAssured.get("/termination")

        assertEquals(200, response.statusCode)
        assertNotNull(response.body().asString())
    }

    @Test
    fun testDiscovererAnyOrder() {
        // Test code for discoverer-any-order
        val response: Response = RestAssured.get("/any-order")

        assertEquals(200, response.statusCode)
        assertNotNull(response.body().asString())
    }

    @Test
    fun testDiscovererTrack() {
        // Test code for discoverer-track
        val response: Response = RestAssured.get("/track")

        assertEquals(200, response.statusCode)
        assertNotNull(response.body().asString())
    }
}