/** Copyright © 2018, Okta, Inc.
 *
 *  Licensed under the MIT license, the "License";
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     https://opensource.org/licenses/MIT
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.okta.scim.controllers;

import com.okta.scim.database.DbUtils;
import com.okta.scim.database.GroupMembershipDatabase;
import com.okta.scim.database.UserDatabase;
import com.okta.scim.models.Email;
import com.okta.scim.models.GroupMembership;
import com.okta.scim.models.User;
import com.okta.scim.utils.ScimUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.*;

/**
 *  URL route (root)/scim/v2/Users/{id}
 */
@Controller
@RequestMapping("/scim/v2/Users/{id}")
public class SingleUserController {
    UserDatabase            db;
    GroupMembershipDatabase gmDb;

    @Autowired
    public SingleUserController(UserDatabase db, GroupMembershipDatabase gmDb) {
        this.db = db;
        this.gmDb = gmDb;
    }

    /**
     * Queries database for {@link User} with identifier
     * Updates response code with '404' if unable to locate {@link User}
     * @param id {@link User#id}
     * @param response HTTP Response
     * @return {@link #scimError(String, Optional)} / JSON {@link Map} of {@link User}
     */
    @RequestMapping(method = RequestMethod.GET)
    public @ResponseBody Map singeUserGet(@PathVariable String id,  HttpServletResponse response) {

        try {
            User user = db.findById(id).get(0);
            HashMap<String, Object> userMap = (HashMap<String, Object>) user.toScimResource();
            DbUtils.enrichWithGroups(userMap, gmDb);
            return userMap;

        } catch (Exception e) {
            response.setStatus(404);
            return ScimUtils.scimError("User not found", Optional.of(404));
        }
    }

    /**
     * Update via Put {@link User} attributes
     * @param payload Payload from HTTP request
     * @param id {@link User#id}
     * @return JSON {@link Map} of {@link User}
     */
    @RequestMapping(method = RequestMethod.PUT)
    public @ResponseBody Map singleUserPut(@RequestBody Map<String, Object> payload,
                                           @PathVariable String id) {
        User user = db.findById(id).get(0);
        user.update(payload);
        db.save(user);
        return user.toScimResource();
    }

    /**
     * Delete {@link User} by identifier, also removes all group memberships
     * @param id {@link User#id}
     * @param response HTTP Response
     * @return empty map with 204 status, or error map with 404
     */
    @RequestMapping(method = RequestMethod.DELETE)
    public @ResponseBody Map singleUserDelete(@PathVariable String id, HttpServletResponse response) {
        List<User> users = db.findById(id);
        if (users.isEmpty()) {
            response.setStatus(404);
            return ScimUtils.scimError("User not found", Optional.of(404));
        }
        PageRequest pageRequest = PageRequest.of(0, Integer.MAX_VALUE);
        Page<GroupMembership> gms = gmDb.findByUserId(id, pageRequest);
        gmDb.deleteAll(gms.getContent());
        db.delete(users.get(0));
        response.setStatus(204);
        return new HashMap<>();
    }

    /**
     * Update via Patch {@link User} attributes
     * @param payload Payload from HTTP request
     * @param id {@link User#id}
     * @return {@link #scimError(String, Optional)} / JSON {@link Map} of {@link User}
     */
    @RequestMapping(method = RequestMethod.PATCH)
    public @ResponseBody Map singleUserPatch(@RequestBody Map<String, Object> payload,
                                             @PathVariable String id,
                                             HttpServletResponse response) {
        List schema = (List)payload.get("schemas");
        List<Map> operations = (List)payload.get("Operations");

        // RFC 7644 §3.5.2 PatchOp
        if(schema == null){
            return ScimUtils.scimError("Payload must contain schema attribute.", 400, response);
        }
        if(operations == null){
            return ScimUtils.scimError("Payload must contain operations attribute.", 400, response);
        }

        String schemaPatchOp = "urn:ietf:params:scim:api:messages:2.0:PatchOp";
        if (!schema.contains(schemaPatchOp)){
            return ScimUtils.scimError("The 'schemas' type in this request is not supported.", 400, response);
        }

        List<User> byId = db.findById(id);
        if (byId.isEmpty()) {
            return ScimUtils.scimError("User '" + id + "' was not found.", 404, response);
        }

        User user = byId.get(0);

        for (Map map : operations) {
            // RFC 7644 §3.5.2: op is case-insensitive; invalid op → 400
            String op = map.get("op") != null ? map.get("op").toString().toLowerCase() : null;
            if (op == null || (!op.equals("add") && !op.equals("replace") && !op.equals("remove"))) {
                return ScimUtils.scimError("Unsupported PATCH op: " + map.get("op"), 400, response);
            }

            String path = map.get("path") != null ? map.get("path").toString() : null;
            Object value = map.get("value");

            if (path != null) {
                applyPatch(user, path, value, op);
            } else if (value instanceof Map) {
                // No path: value is a partial resource (RFC 7644 §3.5.2)
                for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                    applyPatch(user, entry.getKey(), entry.getValue(), op);
                }
            } else {
                return ScimUtils.scimError(
                        "PATCH operation must include 'path' or a 'value' object.", 400, response);
            }
        }
        db.save(user);
        HashMap<String, Object> userMap = (HashMap<String, Object>) user.toScimResource();
        DbUtils.enrichWithGroups(userMap, gmDb);
        return userMap;
    }

    @SuppressWarnings("unchecked")
    private void applyPatch(User user, String path, Object value, String op) {
        if (path.equals("emails")) {
            if ("remove".equals(op)) {
                user.emails.clear();
            } else if (value instanceof List) {
                List<Email> parsed = Email.parseList((List<Map<String, Object>>) value);
                if ("add".equals(op)) {
                    user.emails.addAll(parsed);
                } else {
                    user.emails = parsed;
                }
            }
            return;
        }
        if (path.startsWith("emails[")) {
            // RFC 7644 §3.5.2: e.g. emails[type eq "work"] or emails[value eq "x@y.com"]
            if ("remove".equals(op)) {
                removeEmailsByFilter(user, path);
            }
            return;
        }
        if (path.startsWith("name.")) {
            String subAttr = path.substring(5);
            String strVal = value != null ? value.toString() : null;
            switch (subAttr) {
                case "givenName":  user.givenName  = strVal; break;
                case "familyName": user.familyName = strVal; break;
                case "middleName": user.middleName = strVal; break;
            }
            return;
        }
        if (path.equals("name") && value instanceof Map) {
            Map<String, Object> nameMap = (Map<String, Object>) value;
            if (nameMap.containsKey("givenName"))  user.givenName  = nameMap.get("givenName")  != null ? nameMap.get("givenName").toString()  : null;
            if (nameMap.containsKey("familyName")) user.familyName = nameMap.get("familyName") != null ? nameMap.get("familyName").toString() : null;
            if (nameMap.containsKey("middleName")) user.middleName = nameMap.get("middleName") != null ? nameMap.get("middleName").toString() : null;
            return;
        }
        switch (path) {
            case "active":
                user.active = "remove".equals(op) ? false : Boolean.valueOf(value.toString());
                break;
            case "userName":
                user.userName = value != null ? value.toString() : null;
                break;
            case "displayName":
                user.displayName = value != null ? value.toString() : null;
                break;
            // Unknown attributes are silently ignored per RFC 7644 §3.5.2
        }
    }

    private void removeEmailsByFilter(User user, String path) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\w+)\\s+eq\\s+\"([^\"]+)\"", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(path);
        if (!m.find()) {
            return;
        }
        String attr = m.group(1);
        String val = m.group(2);
        user.emails.removeIf(e ->
                "value".equalsIgnoreCase(attr) ? val.equalsIgnoreCase(e.value)
                        : "type".equalsIgnoreCase(attr) && val.equalsIgnoreCase(e.type));
    }

}
