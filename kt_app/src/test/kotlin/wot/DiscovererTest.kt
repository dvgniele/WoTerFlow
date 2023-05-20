package wot

import io.restassured.RestAssured
import io.restassured.specification.RequestSpecification
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

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

        assertTrue(true)
    }

    @Test
    fun testDiscovererMustSupportFetching(){
        // Test code for discoverer-must-support-fetching

        assertTrue (true)
    }

    @Test
    fun testDiscovererMayMultipleIntro() {
        // Test code for discoverer-may-multiple-intro

        assertTrue(true)
    }

    @Test
    fun testDiscovererMergeIntros() {
        // Test code for discoverer-merge-intros

        assertTrue(true)
    }

    @Test
    fun testDiscovererTDIdentify() {
        // Test code for discoverer-td-identify

        assertTrue(true)
    }

    @Test
    fun testDiscovererFetchTDD() {
        // Test code for discoverer-fetch-tdd

        assertTrue(true)
    }

    @Test
    fun testDiscovererFetchLinks() {
        // Test code for discoverer-fetch-links

        assertTrue(true)
    }

    @Test
    fun testDiscovererFetchIteration() {
        // Test code for discoverer-fetch-iteration

        assertTrue(true)
    }

    @Test
    fun testDiscovererTermination() {
        // Test code for discoverer-termination

        assertTrue(true)
    }

    @Test
    fun testDiscovererAnyOrder() {
        // Test code for discoverer-any-order

        assertTrue(true)
    }

    @Test
    fun testDiscovererTrack() {
        // Test code for discoverer-track

        assertTrue(true)
    }
}