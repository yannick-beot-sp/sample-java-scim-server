package com.okta.scim.it;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for /scim/v2/Groups and /scim/v2/Groups/{id}
 *
 * Covers:
 *  - Create (POST) → 201
 *  - Read single (GET) → 200
 *  - List (GET) → 200 with SCIM ListResponse structure
 *  - Filter by displayName
 *  - Full update (PUT)
 *  - Add member (PATCH add)
 *  - Remove member (PATCH remove)
 *  - Not-found (GET unknown id) → 404
 *
 * Each test creates its own isolated data.
 */
@DisplayName("Groups CRUD")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GroupsIT extends ScimIntegrationTestBase {

    private static final String GROUPS_BASE = "/scim/v2/Groups";
    private static final String USERS_BASE  = "/scim/v2/Users";

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String uniqueDisplayName() throws Exception {
        return "Test Group " + UUID.randomUUID().toString().substring(0, 8);
    }

    private String uniqueUserName() throws Exception {
        return "grp.user." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private Map<String, Object> buildGroupPayload(String displayName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemas",     Collections.singletonList("urn:ietf:params:scim:schemas:core:2.0:Group"));
        payload.put("displayName", displayName);
        return payload;
    }

    /** Creates a group and returns the parsed response body. */
    private JSONObject createGroup(String displayName) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(buildGroupPayload(displayName), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url(GROUPS_BASE), request, String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "Expected 201 Created for POST /Groups");
        return new JSONObject(response.getBody());
    }

    /** Creates a user and returns the parsed response body. */
    private JSONObject createUser(String userName) throws Exception {
        Map<String, Object> name = new LinkedHashMap<>();
        name.put("givenName",  "Group");
        name.put("familyName", "Member");
        name.put("middleName", "");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemas",     Collections.singletonList("urn:ietf:params:scim:schemas:core:2.0:User"));
        payload.put("userName",    userName);
        payload.put("name",        name);
        payload.put("displayName", "Group Member");
        payload.put("active",      true);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url(USERS_BASE), request, String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return new JSONObject(response.getBody());
    }

    /** Sends a PATCH to the group with the given operations list. */
    private ResponseEntity<String> patchGroup(String groupId, List<Map<String, Object>> operations) {
        Map<String, Object> patchBody = new LinkedHashMap<>();
        patchBody.put("schemas",    Collections.singletonList("urn:ietf:params:scim:api:messages:2.0:PatchOp"));
        patchBody.put("Operations", operations);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                url(GROUPS_BASE + "/" + groupId), HttpMethod.PATCH,
                new HttpEntity<>(patchBody, headers), String.class);
    }

    // -----------------------------------------------------------------------
    // Create
    // -----------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("POST /Groups returns HTTP 201 and group resource")
    void createGroupReturns201() throws Exception {
        String displayName = uniqueDisplayName();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(buildGroupPayload(displayName), headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url(GROUPS_BASE), request, String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        JSONObject body = new JSONObject(response.getBody());
        assertFalse(body.getString("id").isBlank(), "Expected non-blank id");
        assertEquals(displayName, body.getString("displayName"));
        assertEquals("urn:ietf:params:scim:schemas:core:2.0:Group",
                body.getJSONArray("schemas").getString(0));
    }

    @Test
    @Order(2)
    @DisplayName("POST /Groups with members creates group and associates members")
    void createGroupWithMembers() throws Exception {
        JSONObject user = createUser(uniqueUserName());
        String userId = user.getString("id");

        String displayName = uniqueDisplayName();
        Map<String, Object> memberEntry = new LinkedHashMap<>();
        memberEntry.put("value", userId);
        memberEntry.put("display", user.getString("displayName"));

        Map<String, Object> payload = buildGroupPayload(displayName);
        payload.put("members", Collections.singletonList(memberEntry));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity(
                url(GROUPS_BASE), new HttpEntity<>(payload, headers), String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        JSONObject body = new JSONObject(response.getBody());
        assertTrue(body.has("members"), "Expected 'members' in response");
        JSONArray members = body.getJSONArray("members");
        assertEquals(1, members.length());
        assertEquals(userId, members.getJSONObject(0).getString("value"));
    }

    // -----------------------------------------------------------------------
    // Read
    // -----------------------------------------------------------------------

    @Test
    @Order(3)
    @DisplayName("GET /Groups/{id} returns HTTP 200 with full group resource")
    void getSingleGroupReturns200() throws Exception {
        String displayName = uniqueDisplayName();
        JSONObject created = createGroup(displayName);
        String groupId = created.getString("id");

        ResponseEntity<String> response = restTemplate.getForEntity(
                url(GROUPS_BASE + "/" + groupId), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JSONObject body = new JSONObject(response.getBody());
        assertEquals(groupId,     body.getString("id"));
        assertEquals(displayName, body.getString("displayName"));
        assertTrue(body.has("meta"),    "Expected 'meta' in response");
        assertTrue(body.has("members"), "Expected 'members' in response");
    }

    @Test
    @Order(4)
    @DisplayName("GET /Groups/{unknownId} returns HTTP 404")
    void getSingleGroupNotFoundReturns404() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(
                url(GROUPS_BASE + "/" + UUID.randomUUID()), String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // -----------------------------------------------------------------------
    // List
    // -----------------------------------------------------------------------

    @Test
    @Order(5)
    @DisplayName("GET /Groups returns HTTP 200 with SCIM ListResponse structure")
    void listGroupsReturns200() throws Exception {
        createGroup(uniqueDisplayName());

        ResponseEntity<String> response = restTemplate.getForEntity(url(GROUPS_BASE), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JSONObject body = new JSONObject(response.getBody());
        assertEquals("urn:ietf:params:scim:api:messages:2.0:ListResponse",
                body.getJSONArray("schemas").getString(0));
        assertTrue(body.has("totalResults"), "Expected 'totalResults'");
        assertTrue(body.has("Resources"),    "Expected 'Resources' array");
    }

    @Test
    @Order(6)
    @DisplayName("GET /Groups?filter=displayName eq \"...\" returns matching group")
    void filterGroupsByDisplayName() throws Exception {
        String displayName = uniqueDisplayName();
        createGroup(displayName);

        String filterUrl = url(GROUPS_BASE + "?filter=displayName eq \"" + displayName + "\"");
        ResponseEntity<String> response = restTemplate.getForEntity(filterUrl, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JSONObject body = new JSONObject(response.getBody());
        // Note: filtered list only returns groups WITH members; empty-member groups are omitted
        // Verify the displayName appears somewhere in the response
        assertTrue(response.getBody().contains(displayName),
                "Expected displayName '" + displayName + "' in filter response");
    }

    // -----------------------------------------------------------------------
    // Full update (PUT)
    // -----------------------------------------------------------------------

    @Test
    @Order(7)
    @DisplayName("PUT /Groups/{id} updates displayName and returns 200")
    void putGroupUpdatesDisplayName() throws Exception {
        String displayName = uniqueDisplayName();
        JSONObject created = createGroup(displayName);
        String groupId = created.getString("id");

        String updatedName = displayName + " (updated)";
        Map<String, Object> payload = buildGroupPayload(updatedName);
        payload.put("id", groupId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(
                url(GROUPS_BASE + "/" + groupId), HttpMethod.PUT,
                new HttpEntity<>(payload, headers), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JSONObject body = new JSONObject(response.getBody());
        assertEquals(updatedName, body.getString("displayName"));
    }

    // -----------------------------------------------------------------------
    // Member management (PATCH)
    // -----------------------------------------------------------------------

    @Test
    @Order(8)
    @DisplayName("PATCH /Groups/{id} with op=add adds a member")
    void patchGroupAddMember() throws Exception {
        JSONObject group = createGroup(uniqueDisplayName());
        String groupId = group.getString("id");

        JSONObject user = createUser(uniqueUserName());
        String userId   = user.getString("id");

        Map<String, Object> memberEntry = new LinkedHashMap<>();
        memberEntry.put("value",   userId);
        memberEntry.put("display", user.getString("displayName"));

        Map<String, Object> addOp = new LinkedHashMap<>();
        addOp.put("op",    "add");
        addOp.put("path",  "members");
        addOp.put("value", Collections.singletonList(memberEntry));

        ResponseEntity<String> response = patchGroup(groupId, Collections.singletonList(addOp));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JSONObject body = new JSONObject(response.getBody());
        JSONArray members = body.getJSONArray("members");
        assertEquals(1, members.length(), "Expected exactly 1 member after add");
        assertEquals(userId, members.getJSONObject(0).getString("value"));
    }

    @Test
    @Order(9)
    @DisplayName("PATCH /Groups/{id} with op=add is idempotent (no duplicate members)")
    void patchGroupAddMemberIdempotent() throws Exception {
        JSONObject group = createGroup(uniqueDisplayName());
        String groupId = group.getString("id");
        JSONObject user = createUser(uniqueUserName());
        String userId   = user.getString("id");

        Map<String, Object> memberEntry = new LinkedHashMap<>();
        memberEntry.put("value",   userId);
        memberEntry.put("display", user.getString("displayName"));

        Map<String, Object> addOp = new LinkedHashMap<>();
        addOp.put("op",    "add");
        addOp.put("path",  "members");
        addOp.put("value", Collections.singletonList(memberEntry));

        // Add twice
        patchGroup(groupId, Collections.singletonList(addOp));
        ResponseEntity<String> response = patchGroup(groupId, Collections.singletonList(addOp));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JSONObject body = new JSONObject(response.getBody());
        JSONArray members = body.getJSONArray("members");
        assertEquals(1, members.length(), "Expected exactly 1 member (no duplicate)");
    }

    @Test
    @Order(10)
    @DisplayName("PATCH /Groups/{id} with op=remove removes an existing member")
    void patchGroupRemoveMember() throws Exception {
        JSONObject group = createGroup(uniqueDisplayName());
        String groupId = group.getString("id");
        JSONObject user = createUser(uniqueUserName());
        String userId   = user.getString("id");

        // First add the member
        Map<String, Object> memberEntry = new LinkedHashMap<>();
        memberEntry.put("value",   userId);
        memberEntry.put("display", user.getString("displayName"));
        Map<String, Object> addOp = new LinkedHashMap<>();
        addOp.put("op",    "add");
        addOp.put("path",  "members");
        addOp.put("value", Collections.singletonList(memberEntry));
        patchGroup(groupId, Collections.singletonList(addOp));

        // Now remove the member using the "value.members" form
        Map<String, Object> memberToRemove = new LinkedHashMap<>();
        memberToRemove.put("value", userId);
        Map<String, Object> removeValue = new LinkedHashMap<>();
        removeValue.put("members", memberToRemove);
        Map<String, Object> removeOp = new LinkedHashMap<>();
        removeOp.put("op",    "remove");
        removeOp.put("value", removeValue);

        ResponseEntity<String> response = patchGroup(groupId, Collections.singletonList(removeOp));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JSONObject body = new JSONObject(response.getBody());
        JSONArray members = body.getJSONArray("members");
        assertEquals(0, members.length(), "Expected 0 members after removal");
    }

    @Test
    @Order(11)
    @DisplayName("PATCH /Groups/{id} add member with non-existent userId returns error")
    void patchGroupAddNonExistentUserReturnsError() throws Exception {
        JSONObject group = createGroup(uniqueDisplayName());
        String groupId = group.getString("id");

        Map<String, Object> memberEntry = new LinkedHashMap<>();
        memberEntry.put("value",   UUID.randomUUID().toString());
        memberEntry.put("display", "Ghost User");

        Map<String, Object> addOp = new LinkedHashMap<>();
        addOp.put("op",    "add");
        addOp.put("path",  "members");
        addOp.put("value", Collections.singletonList(memberEntry));

        ResponseEntity<String> response = patchGroup(groupId, Collections.singletonList(addOp));

        // Server returns a SCIM error body
        String body = response.getBody();
        assertTrue(body.contains("User not found") || body.contains("not found"),
                "Expected 'User not found' error in response");
    }
}
