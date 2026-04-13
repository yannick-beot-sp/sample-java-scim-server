package com.okta.scim.it;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for /scim/v2/Schemas
 *
 * Verifies:
 *  - List endpoint returns HTTP 200 and validates against Schemas.schema.json
 *  - Individual GET for User schema validates against CoreUser.schema.json and Schema.schema.json
 *  - Individual GET for Group schema validates against CoreGroup.schema.json and Schema.schema.json
 *  - Unknown schema ID returns HTTP 404
 */
@DisplayName("Schemas endpoint")
class SchemasIT extends ScimIntegrationTestBase {

    private static final String BASE        = "/scim/v2/Schemas";
    private static final String USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";
    private static final String GROUP_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group";

    // ------------------------------------------------------------------
    // List endpoint
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /Schemas returns HTTP 200")
    void listReturns200() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(BASE), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("GET /Schemas response validates against Schemas.schema.json")
    void listValidatesSchema() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(BASE), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertValidatesSchema(response.getBody(), "Schemas.schema.json");
    }

    @Test
    @DisplayName("GET /Schemas returns a ListResponse containing User and Group schemas")
    void listContainsUserAndGroupSchemas() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(BASE), String.class);
        JSONObject body = new JSONObject(response.getBody());
        assertEquals("urn:ietf:params:scim:api:messages:2.0:ListResponse",
                body.getJSONArray("schemas").getString(0));
        assertEquals(2, body.getInt("totalResults"));
        String bodyStr = response.getBody();
        assertTrue(bodyStr.contains(USER_SCHEMA),  "Expected User schema URN in list");
        assertTrue(bodyStr.contains(GROUP_SCHEMA), "Expected Group schema URN in list");
    }

    // ------------------------------------------------------------------
    // User schema
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /Schemas/{userSchemaId} returns HTTP 200")
    void getUserSchemaReturns200() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(BASE + "/" + USER_SCHEMA), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("GET /Schemas/{userSchemaId} validates against CoreUser.schema.json")
    void getUserSchemaValidatesCoreUserSchema() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(BASE + "/" + USER_SCHEMA), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertValidatesSchema(response.getBody(), "CoreUser.schema.json");
    }

    @Test
    @DisplayName("GET /Schemas/{userSchemaId} validates against Schema.schema.json")
    void getUserSchemaValidatesSchemaSchema() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(BASE + "/" + USER_SCHEMA), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertValidatesSchema(response.getBody(), "Schema.schema.json");
    }

    @Test
    @DisplayName("GET /Schemas/{userSchemaId} contains expected id and name")
    void getUserSchemaFields() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(BASE + "/" + USER_SCHEMA), String.class);
        JSONObject body = new JSONObject(response.getBody());
        assertEquals(USER_SCHEMA, body.getString("id"));
        assertEquals("User", body.getString("name"));
        assertTrue(body.has("attributes"), "Expected 'attributes' array");
        assertTrue(body.has("meta"),       "Expected 'meta' object");
    }

    // ------------------------------------------------------------------
    // Group schema
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /Schemas/{groupSchemaId} returns HTTP 200")
    void getGroupSchemaReturns200() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(BASE + "/" + GROUP_SCHEMA), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("GET /Schemas/{groupSchemaId} validates against CoreGroup.schema.json")
    void getGroupSchemaValidatesCoreGroupSchema() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(BASE + "/" + GROUP_SCHEMA), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertValidatesSchema(response.getBody(), "CoreGroup.schema.json");
    }

    @Test
    @DisplayName("GET /Schemas/{groupSchemaId} validates against Schema.schema.json")
    void getGroupSchemaValidatesSchemaSchema() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(BASE + "/" + GROUP_SCHEMA), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertValidatesSchema(response.getBody(), "Schema.schema.json");
    }

    @Test
    @DisplayName("GET /Schemas/{groupSchemaId} contains expected id and name")
    void getGroupSchemaFields() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url(BASE + "/" + GROUP_SCHEMA), String.class);
        JSONObject body = new JSONObject(response.getBody());
        assertEquals(GROUP_SCHEMA, body.getString("id"));
        assertEquals("Group", body.getString("name"));
        assertTrue(body.has("attributes"), "Expected 'attributes' array");
        assertTrue(body.has("meta"),       "Expected 'meta' object");
    }

    // ------------------------------------------------------------------
    // Error cases
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /Schemas/unknown returns HTTP 404")
    void getUnknownSchemaReturns404() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(
                url(BASE + "/urn:ietf:params:scim:schemas:unknown"), String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
