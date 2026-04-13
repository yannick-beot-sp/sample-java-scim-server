package com.okta.scim.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * URL route (root)/scim/v2/ServiceProviderConfig
 * RFC 7643 §5 — Service Provider Configuration endpoint
 */
@Controller
@RequestMapping("/scim/v2/ServiceProviderConfig")
public class ServiceProviderConfigController {

    @RequestMapping(method = RequestMethod.GET)
    public @ResponseBody Map<String, Object> serviceProviderConfigGet() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemas", Collections.singletonList(
                "urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("resourceType", "ServiceProviderConfig");
        meta.put("location", "/scim/v2/ServiceProviderConfig");
        result.put("meta", meta);

        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("supported", true);
        result.put("patch", patch);

        Map<String, Object> bulk = new LinkedHashMap<>();
        bulk.put("supported", false);
        bulk.put("maxOperations", 1000);
        bulk.put("maxPayloadSize", 1048576);
        result.put("bulk", bulk);

        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("supported", true);
        filter.put("maxResults", 200);
        result.put("filter", filter);

        Map<String, Object> changePassword = new LinkedHashMap<>();
        changePassword.put("supported", false);
        result.put("changePassword", changePassword);

        Map<String, Object> sort = new LinkedHashMap<>();
        sort.put("supported", false);
        result.put("sort", sort);

        Map<String, Object> etag = new LinkedHashMap<>();
        etag.put("supported", false);
        result.put("etag", etag);

        Map<String, Object> authScheme = new LinkedHashMap<>();
        authScheme.put("type", "oauthbearertoken");
        authScheme.put("name", "OAuth Bearer Token");
        authScheme.put("description", "Authentication scheme using the OAuth Bearer Token Standard");
        result.put("authenticationSchemes", Collections.singletonList(authScheme));

        return result;
    }
}
