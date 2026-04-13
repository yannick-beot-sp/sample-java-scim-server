package com.okta.scim.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletResponse;
import java.util.*;

/**
 * URL route (root)/scim/v2/Schemas
 * RFC 7643 §7 — Schema discovery endpoint
 */
@Controller
@RequestMapping("/scim/v2/Schemas")
public class SchemasController {

    private static final String USER_SCHEMA_ID  = "urn:ietf:params:scim:schemas:core:2.0:User";
    private static final String GROUP_SCHEMA_ID = "urn:ietf:params:scim:schemas:core:2.0:Group";

    @RequestMapping(method = RequestMethod.GET)
    public @ResponseBody Map<String, Object> schemasGet(HttpServletResponse response) {
        List<Map<String, Object>> resources = new ArrayList<>();
        resources.add(buildUserSchema());
        resources.add(buildGroupSchema());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemas", Collections.singletonList("urn:ietf:params:scim:api:messages:2.0:ListResponse"));
        result.put("totalResults", resources.size());
        result.put("itemsPerPage", resources.size());
        result.put("startIndex", 1);
        result.put("Resources", resources);
        return result;
    }

    @RequestMapping(value = "/{id:.+}", method = RequestMethod.GET)
    public @ResponseBody Map<String, Object> schemaGetById(@PathVariable String id) {
        if (USER_SCHEMA_ID.equals(id)) {
            return buildUserSchema();
        }
        if (GROUP_SCHEMA_ID.equals(id)) {
            return buildGroupSchema();
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schema not found: " + id);
    }

    // -------------------------------------------------------------------------
    // Schema builders
    // -------------------------------------------------------------------------

    private Map<String, Object> buildUserSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("id", USER_SCHEMA_ID);
        schema.put("name", "User");
        schema.put("description", "User Account");
        schema.put("schemas", Collections.singletonList("urn:ietf:params:scim:schemas:core:2.0:Schema"));

        List<Map<String, Object>> attributes = new ArrayList<>();

        attributes.add(attr("userName", "string", false,
                "Unique identifier for the User (typically email).", true, "readWrite", "always"));

        // name (complex)
        Map<String, Object> nameAttr = attr("name", "complex", false,
                "The components of the user's real name.", false, "readWrite", "default");
        nameAttr.put("subAttributes", Arrays.asList(
                subAttr("formatted",  "string", false, "Full name."),
                subAttr("familyName", "string", false, "Family name (last name)."),
                subAttr("givenName",  "string", false, "Given name (first name)."),
                subAttr("middleName", "string", false, "Middle name.")
        ));
        attributes.add(nameAttr);

        attributes.add(attr("displayName", "string", false,
                "Name of the User suitable for display.", false, "readWrite", "default"));

        attributes.add(attr("active", "boolean", false,
                "Indicates whether the User's administrative status is active.", false, "readWrite", "default"));

        // emails (multi-valued complex)
        Map<String, Object> emailsAttr = attr("emails", "complex", true,
                "Email addresses for the user.", false, "readWrite", "default");
        emailsAttr.put("subAttributes", Arrays.asList(
                subAttr("value",   "string",  false, "Email address."),
                subAttr("type",    "string",  false, "Type of email address (e.g. work, home)."),
                subAttr("primary", "boolean", false, "Whether this is the primary email address.")
        ));
        attributes.add(emailsAttr);

        schema.put("attributes", attributes);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("resourceType", "Schema");
        meta.put("location", "/scim/v2/Schemas/" + USER_SCHEMA_ID);
        schema.put("meta", meta);

        return schema;
    }

    private Map<String, Object> buildGroupSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("id", GROUP_SCHEMA_ID);
        schema.put("name", "Group");
        schema.put("description", "Group");
        schema.put("schemas", Collections.singletonList("urn:ietf:params:scim:schemas:core:2.0:Schema"));

        List<Map<String, Object>> attributes = new ArrayList<>();

        attributes.add(attr("displayName", "string", false,
                "Human-readable name for the Group.", true, "readWrite", "always"));

        // members (multi-valued complex)
        Map<String, Object> membersAttr = attr("members", "complex", true,
                "A list of members of the Group.", false, "readWrite", "default");
        membersAttr.put("subAttributes", Arrays.asList(
                subAttr("value",   "string", false, "Identifier of the member resource."),
                subAttr("$ref",    "reference", false, "URI of the member resource."),
                subAttr("display", "string", false, "Human-readable name of the member."),
                subAttr("type",    "string", false, "Type of the member resource (User or Group).")
        ));
        attributes.add(membersAttr);

        schema.put("attributes", attributes);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("resourceType", "Schema");
        meta.put("location", "/scim/v2/Schemas/" + GROUP_SCHEMA_ID);
        schema.put("meta", meta);

        return schema;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Map<String, Object> attr(String name, String type, boolean multiValued,
                                     String description, boolean required,
                                     String mutability, String returned) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("name", name);
        a.put("type", type);
        a.put("multiValued", multiValued);
        a.put("description", description);
        a.put("required", required);
        if ("string".equals(type)) {
            a.put("caseExact", false);
        }
        a.put("mutability", mutability);
        a.put("returned", returned);
        return a;
    }

    private Map<String, Object> subAttr(String name, String type, boolean multiValued, String description) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("name", name);
        a.put("type", type);
        a.put("multiValued", multiValued);
        a.put("description", description);
        a.put("required", false);
        if ("string".equals(type)) {
            a.put("caseExact", false);
        }
        a.put("mutability", "readWrite");
        a.put("returned", "default");
        return a;
    }
}
