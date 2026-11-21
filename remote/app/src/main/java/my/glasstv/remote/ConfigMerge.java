package my.glasstv.remote;

import java.util.Collection;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Glass TV's config.json is shared with the computer tools and the TV owner's own edits, so the phone
 * changes only the keys it set and keeps everything else.
 */
final class ConfigMerge {
    private ConfigMerge() {}

    /** The TV's config, or {} when it is missing, empty or not valid JSON. Never null. */
    static JSONObject read(String text) {
        if (text == null) return new JSONObject();
        String t = text.startsWith("﻿") ? text.substring(1) : text;
        if (t.trim().isEmpty()) return new JSONObject();
        try {
            return new JSONObject(t);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    /** The existing config with the changes applied; a null value removes that key. */
    static String merge(String existing, Map<String, Object> changes) {
        JSONObject o = read(existing);
        try {
            for (Map.Entry<String, Object> e : changes.entrySet()) {
                Object v = e.getValue();
                if (v == null) o.remove(e.getKey());
                else if (v instanceof Collection) o.put(e.getKey(), new JSONArray((Collection<?>) v));
                else o.put(e.getKey(), v);
            }
            return o.toString(2);
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
