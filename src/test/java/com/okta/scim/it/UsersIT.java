package com.okta.scim.it;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for /scim/v2/Users and /scim/v2/Users/{id}
 *
 * Covers:
 *  - Create (POST) → 201
 *  - Read single (GET) → 200
 *  - List (GET) → 200 with SCIM ListResponse structure
 *  - Filter by userName
 *  - Full update (PUT)
 *  - Partial update / deactivation (PATCH)
 *  - Not-found (GET unknown id) → 404
 *
 * Each test creates its own data using a unique userName suffix so tests are
 * independent and can run in any order.
 */
@DisplayName("Users CRUD")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UsersIT extends ScimIntegrationTestBase {

    private static final String BASE = "/scim/v2/Users";

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String uniqueUserName() throws Exception {
        return "test.user." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private Map<String, Object> buildUserPayload(String userName) {
        Map<String, Object> name = new LinkedHashMap<>();
        name.put("givenName",  "Test");
        name.put("familyName", "User");
        name.put("middleName", "");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemas",     Collections.singletonList("urn:ietf:params:scim:schemas:core:2.0:User"));
        payload.put("userName",    userName);
        payload.put("name",        name);
        payload.put("displayName", "Test User");
        payload.put("active",      true);
        return payload;
    }

    /** Creates a user and returns the parsed response body. */
    private JSONObject createUser(String userName) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(buildUserPayload(userName), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url(BASE), request, String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "Expected 201 Created for POST /Users");
        return new JSONObject(response.getBody());
    }

    // -----------------------------------------------------------------------
    // Create
    // -----------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("POST /Users returns HTTP 201 and user resource")
    void createUserReturns201() throws Exception {
        String userName = uniqueUserName();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(buildUserPayload(userName), headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url(BASE), request, String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        JSONObject body = new JSONObject(response.getBody());
        assertFalse(body.getString("id").isBlank(), "Expected non-blank id");
        assertEquals(userName, body.getString("userName"));
        assertTrue(body.getBoolean("active"));
        assertEquals("urn:ietf:params:scim:schemas:core:2.0:User",
                body.getJSONArray("schemas").getString(0));
    }

    // -----------------------------------------------------------------------
    // Read
    // -----------------------------------------------------------------------

    @Test
    @Order(2)
    @DisplayName("GET /Users/{id} returns HTTP 200 with full user resource")
    void getSingleUserReturns200() throws Exception {
        String userName = uniqueUserName();
        JSONObject created = createUser(userName);
        String userId = created.getString("id");

        ResponseEntity<String> response = restTemplate.getForEntity(url(BASE + "/" + userId), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JSONObject body = new JSONObject(response.getBody());
        assertEquals(userId, body.getString("id"));
        assertEquals(userName, body.getString("userName"));
        assertTrue(body.has("meta"),   "Expected 'meta' in response");
        assertTrue(body.has("name"),   "Expected 'name' in response");
        assertTrue(body.has("emails"), "Expected 'emails' in response");
    }

    @Test
    @Order(3)
    @DisplayName("GET /Users/{unknownId} returns HTTP 404")
    void getSingleUserNotFoundReturns404() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(
                url(BASE + "/" + UUID.randomUUID()), String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // -----------------------------------------------------------------------
    // List
    // -----------------------------------------------------------------------

    @Test
    @Order(4)
    @DisplayName("GET /Users returns HTTP 200 with SCIM ListResponse structure")
    void listUsersReturns200() throws Exception {
        createUser(uniqueUserName());

        ResponseEntity<String> response = restTemplate.getForEntity(url(BASE), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JSONObject body = new JSONObject(response.getBody());
        assertEquals("urn:ietf:params:scim:api:messages:2.0:ListResponse",
                body.getJSONArray("schemas").getString(0));
        assertTrue(body.has("totalResults"), "Expected 'totalResults'");
        assertTrue(body.has("Resources"),    "Expected 'Resources' array");
    }

    @Test
    @Order(5)
    @DisplayName("GET /Users?filter=userName eq \"...\" returns matching user")
    void filterUsersByUserName() throws Exception {
        String userName = uniqueUserName();
        createUser(userName);

        String filterUrl = url(BASE + "?filter=userName eq \"" + userName + "\"");
        ResponseEntity<String> response = restTemplate.getForEntity(filterUrl, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JSONObject body = new JSONObject(response.getBody());
        JSONArray resources = body.getJSONArray("Resources");
        assertEquals(1, resources.length(), "Expected exactly 1 user matching the filter");
        assertEquals(userName, resources.getJSONObject(0).getString("userName"));
    }

    @Test
    @Order(6)
    @DisplayName("GET /Users supports pagination via count and startIndex")
    void listUsersPagination() throws Exception {
        createUser(uniqueUserName());
        createUser(uniqueUserName());

        ResponseEntity<String> response = restTemplate.getForEntity(
                url(BASE + "?count=1&startIndex=1"), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JSONObject body = new JSONObject(response.getBody());
        JSONArray resources = body.getJSONArray("Resources");
        assertTrue(resources.length() <= 1, "Expected at most 1 result with count=1");
    }

    // -----------------------------------------------------------------------
    // Full update (PUT)
    // -----------------------------------------------------------------------

    @Test
    @Order(7)
    @DisplayName("PUT /Users/{id} updates user attributes and returns 200")
    void putUserUpdatesAttributes() throws Exception {
        String userName = uniqueUserName();
        JSONObject created = createUser(userName);
        String userId = created.getString("id");

        Map<String, Object> name = new LinkedHashMap<>();
        name.put("givenName",  "Updated");
        name.put("familyName", "Name");
        name.put("middleName", "M");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemas",     Collections.singletonList("urn:ietf:params:scim:schemas:core:2.0:User"));
        payload.put("userName",    userName);
        payload.put("name",        name);
        payload.put("displayName", "Updated Name");
        payload.put("active",      true);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url(BASE + "/" + userId), HttpMethod.PUT, request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JSONObject body = new JSONObject(response.getBody());
        assertEquals("Updated", body.getJSONObject("name").getString("givenName"));
        assertEquals("Name",    body.getJSONObject("name").getString("familyName"));
        assertEquals("Updated Name", body.getString("displayName"));
    }

    // -----------------------------------------------------------------------
    // Partial update / deactivation (PATCH)
    // -----------------------------------------------------------------------

    @Test
    @Order(8)
    @DisplayName("PATCH /Users/{id} deactivates a user (active → false)")
    void patchUserDeactivate() throws Exception {
        // Create an active user
        String userName = uniqueUserName();
        JSONObject created = createUser(userName);
        String userId = created.getString("id");
        assertTrue(created.getBoolean("active"), "User should start active");

        // Build PATCH body — the server reads the value from the "userId" key (non-standard)
        Map<String, Object> valueMap = new LinkedHashMap<>();
        valueMap.put("active", false);

        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("op",     "replace");
        operation.put("userId", valueMap);

        Map<String, Object> patchBody = new LinkedHashMap<>();
        patchBody.put("schemas",    Collections.singletonList(
                "urn:ietf:params:scim:api:messages:2.0:PatchOp"));
        patchBody.put("Operations", Collections.singletonList(operation));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(patchBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url(BASE + "/" + userId), HttpMethod.PATCH, request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JSONObject body = new JSONObject(response.getBody());
        assertFalse(body.getBoolean("active"), "User should be inactive after PATCH");
    }

    @Test
    @Order(9)
    @DisplayName("PATCH /Users/{id} can re-activate a user (active → true)")
    void patchUserReactivate() throws Exception {
        // Create user, deactivate, then reactivate
        String userName = uniqueUserName();
        JSONObject created = createUser(userName);
        String userId = created.getString("id");

        // Deactivate
        Map<String, Object> deactivateValue = new LinkedHashMap<>();
        deactivateValue.put("active", false);
        Map<String, Object> deactivateOp = new LinkedHashMap<>();
        deactivateOp.put("op",     "replace");
        deactivateOp.put("userId", deactivateValue);
        Map<String, Object> deactivateBody = new LinkedHashMap<>();
        deactivateBody.put("schemas",    Collections.singletonList(
                "urn:ietf:params:scim:api:messages:2.0:PatchOp"));
        deactivateBody.put("Operations", Collections.singletonList(deactivateOp));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.exchange(url(BASE + "/" + userId), HttpMethod.PATCH,
                new HttpEntity<>(deactivateBody, headers), String.class);

        // Reactivate
        Map<String, Object> activateValue = new LinkedHashMap<>();
        activateValue.put("active", true);
        Map<String, Object> activateOp = new LinkedHashMap<>();
        activateOp.put("op",     "replace");
        activateOp.put("userId", activateValue);
        Map<String, Object> activateBody = new LinkedHashMap<>();
        activateBody.put("schemas",    Collections.singletonList(
                "urn:ietf:params:scim:api:messages:2.0:PatchOp"));
        activateBody.put("Operations", Collections.singletonList(activateOp));

        ResponseEntity<String> response = restTemplate.exchange(
                url(BASE + "/" + userId), HttpMethod.PATCH,
                new HttpEntity<>(activateBody, headers), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JSONObject body = new JSONObject(response.getBody());
        assertTrue(body.getBoolean("active"), "User should be active again after re-activation PATCH");
    }

    @Test
    @Order(10)
    @DisplayName("PATCH /Users/{id} without schemas returns error")
    void patchUserWithoutSchemasReturnsError() throws Exception {
        String userName = uniqueUserName();
        JSONObject created = createUser(userName);
        String userId = created.getString("id");

        // Omit schemas field
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("op", "replace");
        operation.put("userId", Collections.singletonMap("active", false));

        Map<String, Object> patchBody = new LinkedHashMap<>();
        patchBody.put("Operations", Collections.singletonList(operation));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(
                url(BASE + "/" + userId), HttpMethod.PATCH,
                new HttpEntity<>(patchBody, headers), String.class);

        // Controller returns a SCIM error (not an HTTP 4xx itself, but an error body)
        String body = response.getBody();
        assertTrue(body.contains("schema"), "Expected a SCIM error mentioning 'schema'");
    }

    @Test
    @Order(11)
    @DisplayName("GET /Users?filter=active eq \"false\" returns only inactive users")
    void filterUsersByActiveStatus() throws Exception {
        // Create one inactive user via POST (active=false)
        String inactiveUserName = uniqueUserName();
        Map<String, Object> name = new LinkedHashMap<>();
        name.put("givenName", "Inactive");
        name.put("familyName", "User");
        name.put("middleName", "");
        Map<String, Object> inactivePayload = new LinkedHashMap<>();
        inactivePayload.put("schemas",  Collections.singletonList("urn:ietf:params:scim:schemas:core:2.0:User"));
        inactivePayload.put("userName", inactiveUserName);
        inactivePayload.put("name",     name);
        inactivePayload.put("active",   false);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.postForEntity(url(BASE), new HttpEntity<>(inactivePayload, headers), String.class);

        String filterUrl = url(BASE + "?filter=active eq \"false\"");
        ResponseEntity<String> response = restTemplate.getForEntity(filterUrl, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JSONObject body = new JSONObject(response.getBody());
        JSONArray resources = body.getJSONArray("Resources");
        assertTrue(resources.length() >= 1, "Expected at least one inactive user");
        for (int i = 0; i < resources.length(); i++) {
            assertFalse(resources.getJSONObject(i).getBoolean("active"),
                    "All returned users should be inactive");
        }
    }
}
