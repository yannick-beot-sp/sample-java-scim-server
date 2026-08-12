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

package com.okta.scim.models;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.Table;

import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Database schema for {@link User}
 */
@Entity
@Table(name = "users")
public class User extends BaseModel {
    /**
     * The unique identifier of the user
     * UUID4 following the RFC 7643 requirement
     */
    @Column(length = 36)
    @Id
    public String id;

    /**
     * The active status of the user
     * Default: False
     */
    @Column(columnDefinition = "boolean default false")
    public Boolean active = false;

    /**
     * The username of the user
     * Non-nullable, unique
     * Max length: 250
     */
    @Column(unique=true, nullable=false, length=250)
    public String userName;

    /**
     * The last name (family name) of the user
     * Max length: 250
     */
    @Column(length=250)
    public String familyName;

    /**
     * The middle name of the user
     * Max length: 250
     */
    @Column(length=250)
    public String middleName;

    /**
     * The first name (given name) of the user
     * Max length: 250
     */
    @Column(length=250)
    public String givenName;

    /**
     * The display name of the user
     * Max length: 250
     */
    @Column(length=250)
    public String displayName;

    /**
     * The email addresses of the user
     * RFC 7643 §4.1.2 — multi-valued, at most one MAY be marked primary
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_emails", joinColumns = @JoinColumn(name = "user_id"))
    public List<Email> emails = new ArrayList<>();

    public User() {}

    public User(Map<String, Object> resource){
        this.update(resource);
    }

    /**
     * Updates {@link User} object from JSON {@link Map}
     * Each attribute is applied independently so that a payload omitting one
     * (e.g. "name") does not prevent the others (e.g. "emails") from being saved.
     * @param resource JSON {@link Map} of {@link User}
     */
    @SuppressWarnings("unchecked")
    public void update(Map<String, Object> resource) {
        Object nameObj = resource.get("name");
        if (nameObj instanceof Map) {
            Map<String, Object> names = (Map<String, Object>) nameObj;
            if (names.get("givenName") != null) {
                this.givenName = names.get("givenName").toString();
            }
            if (names.get("familyName") != null) {
                this.familyName = names.get("familyName").toString();
            }
            if (names.get("middleName") != null) {
                this.middleName = names.get("middleName").toString();
            }
        }
        if (resource.get("userName") != null) {
            this.userName = resource.get("userName").toString();
        }
        if (resource.get("active") != null) {
            this.active = Boolean.valueOf(resource.get("active").toString());
        }
        if (resource.get("displayName") != null) {
            this.displayName = resource.get("displayName").toString();
        }
        if (resource.get("emails") instanceof List) {
            this.emails = Email.parseList((List<Map<String, Object>>) resource.get("emails"));
        }
    }

    /**
     * Formats JSON {@link Map} response with {@link User} attributes
     * @return JSON {@link Map} of {@link User}
     */
    @Override
    public Map toScimResource(){
        Map<String, Object> returnValue = new HashMap<>();
        List<String> schemas = new ArrayList<>();
        schemas.add("urn:ietf:params:scim:schemas:core:2.0:User");
        returnValue.put("schemas", schemas);
        returnValue.put("id", this.id);
        returnValue.put("active", this.active);
        returnValue.put("userName", this.userName);
        returnValue.put("displayName", this.displayName);

        // Name
        Map<String, Object> names = new HashMap<>();
        names.put("familyName", this.familyName);
        names.put("givenName", this.givenName);
        names.put("middleName", this.middleName);
        returnValue.put("name", names);

        // Meta information
        Map<String, Object> meta = new HashMap<>();
        meta.put("resourceType", "User");
        meta.put("location", ("/scim/v2/Users/" + this.id));
        returnValue.put("meta", meta);

        if (this.emails != null && !this.emails.isEmpty()) {
            List<Map<String, Object>> emails = new ArrayList<>();
            for (Email email : this.emails) {
                emails.add(email.toScimResource());
            }
            returnValue.put("emails", emails);
        }

        return returnValue;
    }

    /**
     * JSON array of emails for the web UI edit form ({@code data-emails} attribute).
     */
    public String getEmailsJson() {
        StringBuilder sb = new StringBuilder("[");
        if (this.emails != null) {
            boolean first = true;
            for (Email email : this.emails) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('{')
                        .append("\"value\":").append(quoteJson(email.value))
                        .append(",\"type\":").append(quoteJson(email.type != null ? email.type : "work"))
                        .append(",\"primary\":").append(email.primary != null && email.primary)
                        .append('}');
            }
        }
        sb.append(']');
        return sb.toString();
    }

    private static String quoteJson(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }
}
