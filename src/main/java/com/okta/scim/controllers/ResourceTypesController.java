package com.okta.scim.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * URL route (root)/scim/v2/ResourceTypes
 * RFC 7644 §4 — Resource type discovery endpoint
 */
@Controller
@RequestMapping("/scim/v2/ResourceTypes")
public class ResourceTypesController {

    @RequestMapping(method = RequestMethod.GET)
    public @ResponseBody Map<String, Object> resourceTypesGet() {
        List<Map<String, Object>> resources = new ArrayList<>();
        resources.add(buildUserResourceType());
        resources.add(buildGroupResourceType());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemas", Collections.singletonList("urn:ietf:params:scim:api:messages:2.0:ListResponse"));
        result.put("totalResults", resources.size());
        result.put("itemsPerPage", resources.size());
        result.put("startIndex", 1);
        result.put("Resources", resources);
        return result;
    }

    @RequestMapping(value = "/{name}", method = RequestMethod.GET)
    public @ResponseBody Map<String, Object> resourceTypeGetByName(@PathVariable String name) {
        if ("User".equalsIgnoreCase(name)) {
            return buildUserResourceType();
        }
        if ("Group".equalsIgnoreCase(name)) {
            return buildGroupResourceType();
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ResourceType not found: " + name);
    }

    // -------------------------------------------------------------------------
    // ResourceType builders
    // -------------------------------------------------------------------------

    private Map<String, Object> buildUserResourceType() {
        Map<String, Object> rt = new LinkedHashMap<>();
        rt.put("schemas", Collections.singletonList("urn:ietf:params:scim:schemas:core:2.0:ResourceType"));
        rt.put("id", "User");
        rt.put("name", "User");
        rt.put("description", "User Account");
        rt.put("endpoint", "/Users");
        rt.put("schema", "urn:ietf:params:scim:schemas:core:2.0:User");
        rt.put("schemaExtensions", Collections.emptyList());

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("resourceType", "ResourceType");
        meta.put("location", "/scim/v2/ResourceTypes/User");
        rt.put("meta", meta);

        return rt;
    }

    private Map<String, Object> buildGroupResourceType() {
        Map<String, Object> rt = new LinkedHashMap<>();
        rt.put("schemas", Collections.singletonList("urn:ietf:params:scim:schemas:core:2.0:ResourceType"));
        rt.put("id", "Group");
        rt.put("name", "Group");
        rt.put("description", "Group");
        rt.put("endpoint", "/Groups");
        rt.put("schema", "urn:ietf:params:scim:schemas:core:2.0:Group");
        rt.put("schemaExtensions", Collections.emptyList());

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("resourceType", "ResourceType");
        meta.put("location", "/scim/v2/ResourceTypes/Group");
        rt.put("meta", meta);

        return rt;
    }
}
