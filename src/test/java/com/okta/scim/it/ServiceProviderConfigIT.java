package com.okta.scim.it;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for GET /scim/v2/ServiceProviderConfig
 *
 * Verifies:
 *  - HTTP 200 is returned
 *  - Response conforms to ServiceProviderConfig.schema.json
 *  - Key capability flags are present and correctly typed
 */
@DisplayName("ServiceProviderConfig endpoint")
class ServiceProviderConfigIT extends ScimIntegrationTestBase {

    private static final String ENDPOINT = "/scim/v2/ServiceProviderConfig";

    @Test
    @DisplayName("GET returns HTTP 200")
    void getReturns200() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(ENDPOINT), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("GET response body is non-empty JSON")
    void getReturnsNonEmptyBody() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(ENDPOINT), String.class);
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isBlank());
    }

    @Test
    @DisplayName("GET response validates against ServiceProviderConfig.schema.json")
    void getValidatesSchema() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(ENDPOINT), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertValidatesSchema(response.getBody(), "ServiceProviderConfig.schema.json");
    }

    @Test
    @DisplayName("GET response contains correct schemas URN")
    void getContainsCorrectSchemaUrn() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(ENDPOINT), String.class);
        assertTrue(response.getBody().contains(
                "urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"));
    }

    @Test
    @DisplayName("GET response contains all required capability objects")
    void getContainsRequiredCapabilities() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(ENDPOINT), String.class);
        String body = response.getBody();
        assertTrue(body.contains("\"patch\""),        "Missing 'patch' capability");
        assertTrue(body.contains("\"bulk\""),         "Missing 'bulk' capability");
        assertTrue(body.contains("\"filter\""),       "Missing 'filter' capability");
        assertTrue(body.contains("\"changePassword\""), "Missing 'changePassword' capability");
        assertTrue(body.contains("\"sort\""),         "Missing 'sort' capability");
        assertTrue(body.contains("\"etag\""),         "Missing 'etag' capability");
    }
}
