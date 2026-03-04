package com.okta.scim.database;

import com.okta.scim.models.GroupMembership;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DbUtils {
    public static void enrichWithGroups(HashMap<String, Object> userMap, GroupMembershipDatabase gmDb) {
        Page<GroupMembership> pg = gmDb.findByUserId(userMap.get("id").toString(),
                PageRequest.of(0, Integer.MAX_VALUE));
        if (pg.hasContent()) {
            ArrayList<Map<String, Object>> gms = new ArrayList<>();
            for (GroupMembership gm : pg.getContent()) {
                gms.add(gm.toUserScimResource());
            }
            userMap.put("groups", gms);
        }
    }
}
