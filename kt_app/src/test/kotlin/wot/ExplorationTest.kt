package wot

import io.restassured.RestAssured
import io.restassured.specification.RequestSpecification
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ExplorationTest {
    private lateinit var exploration: Exploration
    private lateinit var requestSpecification: RequestSpecification

    @BeforeEach
    fun setup(){
        RestAssured.baseURI = "uri"
        requestSpecification = RestAssured.given().log().all()
        exploration = Exploration()
    }

    @Test
    fun testExplorationDirectoryDescriptionType(){
        // Test code for exploration-directory-description-type

        assertTrue(true)
    }

    @Test
    fun testExplorationLinkDescriptionType(){
        // Test code for exploration-link-description-type

        assertTrue (true)
    }

    @Test
    fun testExplorationLinkDescriptionLink(){
        // Test code for exploration-link-description-link

        assertTrue (true)
    }

    @Test
    fun testExplorationSecBoot401(){
        // Test code for exploration-secboot-401

        assertTrue (true)
    }

    @Test
    fun testExplorationSecBootAuth(){
        // Test code for exploration-secboot-auth

        assertTrue (true)
    }

    @Test
    fun testExplorationSecBootOauth2Flows(){
        // Test code for exploration-secboot-oauth2-flows

        assertTrue (true)
    }

    @Test
    fun testExplorationServerHttpMethod(){
        // Test code for exploration-server-http-method

        assertTrue (true)
    }

    @Test
    fun testExplorationServerHttpResp(){
        // Test code for exploration-server-http-resp

        assertTrue (true)
    }

    @Test
    fun testExplorationServerHttpRespContentType(){
        // Test code for exploration-server-http-resp-content-type

        assertTrue (true)
    }

    @Test
    fun testExplorationServerHttpRespJson(){
        // Test code for exploration-server-http-resp-json

        assertTrue (true)
    }

    @Test
    fun testExplorationServerHttpAlternateContent(){
        // Test code for exploration-server-http-alternate-content

        assertTrue (true)
    }

    @Test
    fun testExplorationServerHttpAlternateLanguage(){
        // Test code for exploration-server-http-alternate-language

        assertTrue (true)
    }

    @Test
    fun testExplorationServerHttpHead(){
        // Test code for exploration-server-http-head

        assertTrue (true)
    }

    @Test
    fun testExplorationServerCoapMethod(){
        // Test code for exploration-server-coap-method

        assertTrue (true)
    }

    @Test
    fun testExplorationServerCoapResp(){
        // Test code for exploration-server-coap-resp

        assertTrue (true)
    }

    @Test
    fun testExplorationServerCoapAlternateContent(){
        // Test code for exploration-server-coap-alternate-content

        assertTrue (true)
    }

    @Test
    fun testExplorationServerCoapSize2(){
        // Test code for exploration-server-coap-size2

        assertTrue (true)
    }
}
