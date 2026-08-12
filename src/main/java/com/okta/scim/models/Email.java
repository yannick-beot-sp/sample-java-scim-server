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

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Database schema for a {@link User}'s "emails" multi-valued attribute.
 * RFC 7643 §4.1.2 / §8.7.1
 */
@Embeddable
public class Email {
    /**
     * The email address itself
     * RFC 7643 §4.1.2: sub-attribute "value" is required
     */
    @Column(length = 250)
    public String value;

    /**
     * A label indicating the attribute's function, e.g. "work", "home", "other"
     */
    @Column(length = 20)
    public String type;

    /**
     * Whether this instance is the primary or preferred email for the user
     * RFC 7643 §2.4: no more than one instance MAY be primary
     * Column is renamed since "primary" is a reserved SQL keyword.
     */
    @Column(name = "is_primary")
    public Boolean primary = false;

    public Email() {}

    public Email(Map<String, Object> resource) {
        if (resource.get("value") != null) {
            this.value = resource.get("value").toString();
        }
        if (resource.get("type") != null) {
            this.type = resource.get("type").toString();
        }
        if (resource.get("primary") != null) {
            this.primary = Boolean.valueOf(resource.get("primary").toString());
        }
    }

    /**
     * Parses a SCIM "emails" array into {@link Email} instances, enforcing
     * RFC 7643 §2.4: no more than one entry may be marked primary.
     * @param resources JSON list of email sub-resources
     * @return list of {@link Email}
     */
    public static List<Email> parseList(List<Map<String, Object>> resources) {
        List<Email> result = new ArrayList<>();
        if (resources == null) {
            return result;
        }
        boolean primarySeen = false;
        for (Map<String, Object> resource : resources) {
            if (resource == null || resource.get("value") == null) {
                continue;
            }
            Email email = new Email(resource);
            if (Boolean.TRUE.equals(email.primary)) {
                if (primarySeen) {
                    email.primary = false;
                } else {
                    primarySeen = true;
                }
            }
            result.add(email);
        }
        return result;
    }

    /**
     * Formats JSON {@link Map} response with {@link Email} attributes.
     * @return JSON {@link Map} of {@link Email}
     */
    public Map<String, Object> toScimResource() {
        Map<String, Object> returnValue = new LinkedHashMap<>();
        returnValue.put("value", this.value);
        if (this.type != null) {
            returnValue.put("type", this.type);
        }
        returnValue.put("primary", this.primary != null && this.primary);
        return returnValue;
    }
}
