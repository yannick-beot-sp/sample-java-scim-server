package com.okta.scim.it;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for /scim/v2/ResourceTypes
 *
 * Verifies:
 *  - List endpoint returns HTTP 200 and validates against ResourceTypes.schema.json
 *  - Individual GET for User and Group validates against ResourceType.schema.json
 *  - Unknown resource type returns HTTP 404
 */
@DisplayName("ResourceTypes endpoint")
class ResourceTypesIT extends ScimIntegrationTestBase {

    private static final String BASE = "/scim/v2/ResourceTypes";

    // ------------------------------------------------------------------
    // List endpoint
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /ResourceTypes returns HTTP 200")
    void listReturns200() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(BASE), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("GET /ResourceTypes response validates against ResourceTypes.schema.json")
    void listValidatesSchema() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(BASE), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertValidatesSchema(response.getBody(), "ResourceTypes.schema.json");
    }

    @Test
    @DisplayName("GET /ResourceTypes returns a ListResponse with User and Group resource types")
    void listContainsUserAndGroup() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(BASE), String.class);
        JSONObject body = new JSONObject(response.getBody());
        assertEquals("urn:ietf:params:scim:api:messages:2.0:ListResponse",
                body.getJSONArray("schemas").getString(0));
        assertEquals(2, body.getInt("totalResults"));
        String bodyStr = response.getBody();
        assertTrue(bodyStr.contains("\"User\""), "Expected 'User' resource type");
        assertTrue(bodyStr.contains("\"Group\""), "Expected 'Group' resource type");
    }

    // ------------------------------------------------------------------
    // Individual resource types
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /ResourceTypes/User returns HTTP 200")
    void getUserResourceTypeReturns200() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(BASE + "/User"), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("GET /ResourceTypes/User validates against ResourceType.schema.json")
    void getUserResourceTypeValidatesSchema() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(BASE + "/User"), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertValidatesSchema(response.getBody(), "ResourceType.schema.json");
    }

    @Test
    @DisplayName("GET /ResourceTypes/User contains expected fields")
    void getUserResourceTypeFields() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(BASE + "/User"), String.class);
        JSONObject body = new JSONObject(response.getBody());
        assertEquals("User", body.getString("name"));
        assertEquals("/Users", body.getString("endpoint"));
        assertEquals("urn:ietf:params:scim:schemas:core:2.0:User", body.getString("schema"));
        assertEquals("ResourceType", body.getJSONObject("meta").getString("resourceType"));
    }

    @Test
    @DisplayName("GET /ResourceTypes/Group returns HTTP 200")
    void getGroupResourceTypeReturns200() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(BASE + "/Group"), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("GET /ResourceTypes/Group validates against ResourceType.schema.json")
    void getGroupResourceTypeValidatesSchema() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(BASE + "/Group"), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertValidatesSchema(response.getBody(), "ResourceType.schema.json");
    }

    @Test
    @DisplayName("GET /ResourceTypes/Group contains expected fields")
    void getGroupResourceTypeFields() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(BASE + "/Group"), String.class);
        JSONObject body = new JSONObject(response.getBody());
        assertEquals("Group", body.getString("name"));
        assertEquals("/Groups", body.getString("endpoint"));
        assertEquals("urn:ietf:params:scim:schemas:core:2.0:Group", body.getString("schema"));
        assertEquals("ResourceType", body.getJSONObject("meta").getString("resourceType"));
    }

    // ------------------------------------------------------------------
    // Error cases
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /ResourceTypes/Unknown returns HTTP 404")
    void getUnknownResourceTypeReturns404() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(
                url(BASE + "/UnknownResource"), String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
