package com.okta.scim.controllers;

import com.okta.scim.database.GroupDatabase;
import com.okta.scim.database.GroupMembershipDatabase;
import com.okta.scim.database.UserDatabase;
import com.okta.scim.models.Group;
import com.okta.scim.models.GroupMembership;
import com.okta.scim.models.User;
import com.okta.scim.utils.ScimUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.*;

/**
 * URL route (root)/scim/v2/Groups/{id}
 */
@Controller
@RequestMapping("/scim/v2/Groups/{id}")
public class SingleGroupController {
    GroupDatabase db;
    GroupMembershipDatabase gmDb;

    UserDatabase userDb;

    @Autowired
    public SingleGroupController(GroupDatabase db, GroupMembershipDatabase gmDb, UserDatabase userDb) {
        this.db = db;
        this.gmDb = gmDb;
        this.userDb = userDb;
    }

    /**
     * Queries database for {@link Group} with identifier
     * Updates response code with '404' if unable to locate {@link Group}
     *
     * @param id       {@link Group#id}
     * @param response HTTP Response
     * @return {@link #scimError(String, Optional)} / JSON {@link Map} of {@link Group}
     */
    @RequestMapping(method = RequestMethod.GET)
    public @ResponseBody
    Map singeGroupGet(@PathVariable String id, HttpServletResponse response) {

        try {
            Group group = db.findById(id).get(0);
            HashMap res = group.toScimResource();

            PageRequest pageRequest = PageRequest.of(0, Integer.MAX_VALUE);
            Page<GroupMembership> gmPage = gmDb.findByGroupId(id, pageRequest);
            List<GroupMembership> gmList = gmPage.getContent();
            ArrayList<Map<String, Object>> gmAL = new ArrayList<>();

            for (GroupMembership gm : gmList) {
                gmAL.add(gm.toScimResource());
            }

            res.put("members", gmAL);
            return res;
        } catch (Exception e) {
            response.setStatus(404);
            return ScimUtils.scimError("Group not found", Optional.of(404));
        }
    }

    /**
     * Update via Put {@link Group} attributes
     *
     * @param payload Payload from HTTP request
     * @param id      {@link Group#id}
     * @return JSON {@link Map} of {@link Group}
     */
    @RequestMapping(method = RequestMethod.PUT)
    public @ResponseBody Map singleGroupPut(@RequestBody Map<String, Object> payload,
                                            @PathVariable String id) {
        Group group = db.findById(id).get(0);
        group.update(payload);
        db.save(group);
        return group.toScimResource();
    }

    /**
     * Delete {@link Group} by identifier, also removes all group memberships
     * @param id {@link Group#id}
     * @param response HTTP Response
     * @return empty map with 204 status, or error map with 404
     */
    @RequestMapping(method = RequestMethod.DELETE)
    public @ResponseBody Map singleGroupDelete(@PathVariable String id, HttpServletResponse response) {
        List<Group> groups = db.findById(id);
        if (groups.isEmpty()) {
            response.setStatus(404);
            return ScimUtils.scimError("Group not found", Optional.of(404));
        }
        PageRequest pageRequest = PageRequest.of(0, Integer.MAX_VALUE);
        Page<GroupMembership> gms = gmDb.findByGroupId(id, pageRequest);
        gmDb.deleteAll(gms.getContent());
        db.delete(groups.get(0));
        response.setStatus(204);
        return new HashMap<>();
    }

    /**
     * Update via Patch {@link Group} attributes (RFC 7644 §3.5.2)
     *
     * @param payload Payload from HTTP request
     * @param id      {@link Group#id}
     * @param response HTTP Response
     * @return SCIM Error / JSON {@link Map} of {@link Group}
     */
    @RequestMapping(method = RequestMethod.PATCH)
    public @ResponseBody Map singleGroupPatch(@RequestBody Map<String, Object> payload,
                                              @PathVariable String id,
                                              HttpServletResponse response) {
        List schema = (List) payload.get("schemas");
        List<Map> operations = (List) payload.get("Operations");

        if (schema == null) {
            return ScimUtils.scimError("Payload must contain schema attribute.", 400, response);
        }
        if (operations == null) {
            return ScimUtils.scimError("Payload must contain operations attribute.", 400, response);
        }

        String schemaPatchOp = "urn:ietf:params:scim:api:messages:2.0:PatchOp";
        if (!schema.contains(schemaPatchOp)) {
            return ScimUtils.scimError("The 'schemas' type in this request is not supported.", 400, response);
        }

        List<Group> byId = db.findById(id);
        if (byId.isEmpty()) {
            return ScimUtils.scimError("Group '" + id + "' was not found.", 404, response);
        }

        Group group = byId.get(0);

        for (Map map : operations) {
            // RFC 7644 §3.5.2: op is case-insensitive
            String op = map.get("op") != null ? map.get("op").toString().toLowerCase() : null;
            if (op == null || (!op.equals("add") && !op.equals("replace") && !op.equals("remove"))) {
                return ScimUtils.scimError("Unsupported PATCH op: " + map.get("op"), 400, response);
            }

            String path = map.get("path") != null ? map.get("path").toString() : null;
            Object value = map.get("value");

            if ("replace".equals(op)) {
                Map error = applyGroupReplace(group, path, value, response);
                if (error != null) {
                    return error;
                }
                db.save(group);
            } else if ("add".equals(op)) {
                Map error = applyGroupMembersAdd(group, id, path, value, response);
                if (error != null) {
                    return error;
                }
            } else if ("remove".equals(op)) {
                Map error = applyGroupMembersRemove(id, path, value, response);
                if (error != null) {
                    return error;
                }
            }
        }

        HashMap res = group.toScimResource();
        PageRequest pageRequest = PageRequest.of(0, Integer.MAX_VALUE);
        Page<GroupMembership> gms = gmDb.findByGroupId(id, pageRequest);
        ArrayList<Map<String, Object>> gmAL = new ArrayList<>();
        for (GroupMembership gm : gms.getContent()) {
            gmAL.add(gm.toScimResource());
        }
        res.put("members", gmAL);
        return res;
    }

    private Map applyGroupReplace(Group group, String path, Object value, HttpServletResponse response) {
        if (path != null) {
            if ("displayName".equals(path) && value != null) {
                group.displayName = value.toString();
                return null;
            }
            return ScimUtils.scimError("Unsupported replace path: " + path, 400, response);
        }
        if (value instanceof Map) {
            Map<String, Object> valueMap = (Map<String, Object>) value;
            if (valueMap.containsKey("displayName") && valueMap.get("displayName") != null) {
                group.displayName = valueMap.get("displayName").toString();
            }
            return null;
        }
        return ScimUtils.scimError(
                "PATCH replace must include 'path' or a 'value' object.", 400, response);
    }

    private Map applyGroupMembersAdd(Group group, String groupId, String path, Object value,
                                     HttpServletResponse response) {
        List<Map<String, Object>> members = extractMembers(path, value);
        if (members == null) {
            return ScimUtils.scimError("PATCH add members requires path 'members' or value.members.",
                    400, response);
        }
        for (Map val : members) {
            String userId = val.get("value") != null ? val.get("value").toString() : null;
            if (!StringUtils.hasText(userId)) {
                continue;
            }
            PageRequest pageRequest = PageRequest.of(0, Integer.MAX_VALUE);
            Page<GroupMembership> gmPage = gmDb.findByGroupIdAndUserId(groupId, userId, pageRequest);
            if (gmPage.hasContent()) {
                continue; // idempotent
            }
            List<User> users = userDb.findById(userId);
            if (users == null || users.size() != 1) {
                return ScimUtils.scimError("User not found", 400, response);
            }
            User user = users.get(0);
            GroupMembership gm = new GroupMembership(val);
            gm.id = UUID.randomUUID().toString();
            gm.groupId = groupId;
            gm.groupDisplay = group.displayName;
            gm.userDisplay = user.displayName;
            gmDb.save(gm);
        }
        return null;
    }

    private Map applyGroupMembersRemove(String groupId, String path, Object value,
                                        HttpServletResponse response) {
        // RFC / Okta: path = members[value eq "id"]
        if (path != null && path.startsWith("members[") && path.contains("value eq")) {
            String userId = extractFilterValue(path);
            if (userId == null) {
                return ScimUtils.scimError("Invalid members filter path: " + path, 400, response);
            }
            deleteMembership(groupId, userId);
            return null;
        }

        List<Map<String, Object>> members = extractMembers(path, value);
        if (members == null || members.isEmpty()) {
            return ScimUtils.scimError(
                    "PATCH remove members requires path filter or value with members.", 400, response);
        }
        for (Map val : members) {
            if (val.get("value") == null) {
                continue;
            }
            deleteMembership(groupId, val.get("value").toString());
        }
        return null;
    }

    private void deleteMembership(String groupId, String userId) {
        PageRequest pageRequest = PageRequest.of(0, Integer.MAX_VALUE);
        Page<GroupMembership> gmPage = gmDb.findByGroupIdAndUserId(groupId, userId, pageRequest);
        if (gmPage.hasContent()) {
            gmDb.deleteAll(gmPage.getContent());
        }
    }

    /**
     * Resolve members list from RFC path/value forms:
     * - path=members, value=[{value:...}]
     * - no path, value={members: {...} | [...]}
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractMembers(String path, Object value) {
        if ("members".equals(path) && value instanceof List) {
            return (List<Map<String, Object>>) value;
        }
        if (value instanceof Map && ((Map<?, ?>) value).containsKey("members")) {
            Object members = ((Map<?, ?>) value).get("members");
            if (members instanceof List) {
                return (List<Map<String, Object>>) members;
            }
            if (members instanceof Map) {
                return Collections.singletonList((Map<String, Object>) members);
            }
        }
        return null;
    }

    private String extractFilterValue(String path) {
        // members[value eq "uuid"]
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("value\\s+eq\\s+\"([^\"]+)\"", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(path);
        return m.find() ? m.group(1) : null;
    }

}
