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
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Field;
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
     * Update via Patch {@link Group} attributes
     *
     * @param payload Payload from HTTP request
     * @param id      {@link Group#id}
     * @return {@link #scimError(String, Optional)} / JSON {@link Map} of {@link Group}
     */
    @RequestMapping(method = RequestMethod.PATCH)
    public @ResponseBody Map singleGroupPatch(@RequestBody Map<String, Object> payload,
                                              @PathVariable String id) {
        List schema = (List) payload.get("schemas");
        List<Map> operations = (List) payload.get("Operations");

        if (schema == null) {
            return ScimUtils.scimError("Payload must contain schema attribute.", Optional.of(400));
        }
        if (operations == null) {
            return ScimUtils.scimError("Payload must contain operations attribute.", Optional.of(400));
        }

        //Verify schema
        String schemaPatchOp = "urn:ietf:params:scim:api:messages:2.0:PatchOp";
        if (!schema.contains(schemaPatchOp)) {
            return ScimUtils.scimError("The 'schemas' type in this request is not supported.", Optional.of(501));
        }

        List<Group> byId = db.findById(id);
        int found = byId.size();

        if (found == 0) {
            return ScimUtils.scimError("Group '" + id + "' was not found.", Optional.of(404));
        }

        //Find user for update
        Group group = byId.get(0);

        HashMap res = group.toScimResource();

        for (Map map : operations) {
            if (map.get("op") == null) {
                continue;
            }

            if (map.get("op").equals("replace")) {
                Map<String, Object> value = (Map) map.get("userId");

                // Use Java reflection to find and set User attribute
                if (value != null) {
                    for (Map.Entry key : value.entrySet()) {
                        try {
                            Field field = group.getClass().getDeclaredField(key.getKey().toString());
                            field.set(group, key.getValue());
                        } catch (NoSuchFieldException | IllegalAccessException e) {
                            // Error - Do not update field
                        }
                    }

                    db.save(group);
                }
            } else if (map.get("op").equals("add")) {
                List<Map<String, Object>> value = null;
                if ("members".equals(map.get("path"))) {
                    value = (List) map.get("value");
                } else if ((map.get("value") instanceof Map) && ((Map<?, ?>) map.get("value")).containsKey("members")) {
                    value = Arrays.asList((Map<String, Object>) ((Map<String, Object>) map.get("value")).get("members"));
                }

                if (!CollectionUtils.isEmpty(value)) {
                    for (Map val : value) {
                        PageRequest pageRequest = PageRequest.of(0, Integer.MAX_VALUE);
                        String userId = val.get("value").toString();
                        if (!StringUtils.hasText(userId)) {
                            continue;
                        }
                        Page<GroupMembership> gmPage = gmDb.findByGroupIdAndUserId(id, userId, pageRequest);
                        // Already present?
                        if (gmPage.hasContent()) {
                            continue;
                        }
                        List<User> users = userDb.findById(userId);
                        if (users == null || users.size() != 1) {
                            return ScimUtils.scimError("User not found", Optional.of(400));
                        }
                        User user = users.get(0);


                        GroupMembership gm = new GroupMembership(val);
                        gm.id = UUID.randomUUID().toString();
                        gm.groupId = id;
                        gm.groupDisplay = group.displayName;
                        gm.userDisplay = user.displayName;
                        gmDb.save(gm);
                    }
                }
            } else if (map.get("op").equals("remove")) {
                List<Map<String, Object>> value = null;
                if ((map.get("value") instanceof Map) && ((Map<?, ?>) map.get("value")).containsKey("members")) {
                    value = Arrays.asList((Map<String, Object>) ((Map<String, Object>) map.get("value")).get("members"));
                }
                if (value != null && !value.isEmpty()) {
                    for (Map val : value) {
                        PageRequest pageRequest = PageRequest.of(0, Integer.MAX_VALUE);
                        Page<GroupMembership> gmPage = gmDb.findByGroupIdAndUserId(id, val.get("value").toString(), pageRequest);

                        if (!gmPage.hasContent()) {
                            continue;
                        }
                        gmDb.deleteAll(gmPage.getContent());
                    }
                }
            }
        }

        PageRequest pageRequest = PageRequest.of(0, Integer.MAX_VALUE);
        Page<GroupMembership> gms = gmDb.findByGroupId(id, pageRequest);
        List<GroupMembership> gmList = gms.getContent();
        ArrayList<Map<String, Object>> gmAL = new ArrayList<>();

        for (GroupMembership gm : gmList) {
            gmAL.add(gm.toScimResource());
        }

        res.put("members", gmAL);

        return res;
    }

}
