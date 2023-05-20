package wot

import io.restassured.RestAssured
import io.restassured.specification.RequestSpecification
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue


class IntroductionTest {

    private lateinit var introduction: Introduction
    private lateinit var requestSpecification: RequestSpecification

    @BeforeEach
    fun setup(){
        RestAssured.baseURI = "uri"
        requestSpecification = RestAssured.given().log().all()
        introduction = Introduction()
    }
    @Test
    fun testDirectURL() {
        // Test code for introduction-direct-url

        assertTrue(true)
    }

    @Test
    fun testDirectThingDescription() {
        // Test code for introduction-direct-thing-description

        assertTrue(true)
    }

    @Test
    fun testDirectDirectoryDescription() {
        // Test code for introduction-direct-directory-description

        assertTrue(true)
    }

    @Test
    fun testWellKnownUri() {
        // Test code for introduction-well-known-uri

        assertTrue(true)
    }

    @Test
    fun testWellKnownPath() {
        // Test code for introduction-well-known-path

        assertTrue(true)
    }

    @Test
    fun testWellKnownThingDescription() {
        // Test code for introduction-well-known-thing-description

        assertTrue(true)
    }

    @Test
    fun testDnsSd() {
        // Test code for introduction-dns-sd

        assertTrue(true)
    }
    @Test
    fun testDnsSdServiceName() {
        // Test code for introduction-dns-sd-service-name

        assertTrue(true)
    }

    @Test
    fun testDnsSdServiceNameDirectory() {
        // Test code for introduction-dns-sd-service-name-directory

        assertTrue(true)
    }

    @Test
    fun testDnsSdServiceNameUdp() {
        // Test code for introduction-dns-sd-service-name-udp

        assertTrue(true)
    }

    @Test
    fun testDnsSdServiceNameDirectoryUdp() {
        // Test code for introduction-dns-sd-service-name-directory-udp

        assertTrue(true)
    }

    @Test
    fun testDnsSdTxtRecord() {
        // Test code for introduction-dns-sd-txt-record

        assertTrue(true)
    }

    @Test
    fun testCoreRd() {
        // Test code for introduction-core-rd

        assertTrue(true)
    }

    @Test
    fun testCoreRdDirectory() {
        // Test code for introduction-core-rd-directory

        assertTrue(true)
    }

    @Test
    fun testCoreRdResourceTypeThing() {
        // Test code for introduction-core-rd-resource-type-thing

        assertTrue(true)
    }

    @Test
    fun testCoreRdResourceTypeDirectory() {
        // Test code for introduction-core-rd-resource-type-directory

        assertTrue(true)
    }
    @Test
    fun testIntroductionDid() {
        // Test code for introduction-did

        assertTrue(true)
    }
    @Test
    fun testIntroductionDidServiceEndpoint() {
        // Test code for introduction-did-service-endpoint

        assertTrue(true)
    }
}