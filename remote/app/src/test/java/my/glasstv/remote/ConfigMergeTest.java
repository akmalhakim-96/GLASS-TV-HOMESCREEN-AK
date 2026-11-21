package my.glasstv.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.junit.Test;

public class ConfigMergeTest {
    @Test
    public void keepsUnknownKeys() throws Exception {
        JSONObject o = new JSONObject(ConfigMerge.merge("{\"vpn_app\":\"x\",\"columns\":4}", Map.of("columns", 5)));
        assertEquals("x", o.getString("vpn_app"));
        assertEquals(5, o.getInt("columns"));
    }

    @Test
    public void nullRemovesKey() throws Exception {
        Map<String, Object> c = new HashMap<>();
        c.put("places", null);
        assertFalse(new JSONObject(ConfigMerge.merge("{\"places\":false}", c)).has("places"));
    }

    @Test
    public void listsBecomeArrays() throws Exception {
        JSONObject o = new JSONObject(ConfigMerge.merge(null, Map.of("azan_prayers", List.of("subuh", "maghrib"))));
        assertEquals(2, o.getJSONArray("azan_prayers").length());
        assertEquals("maghrib", o.getJSONArray("azan_prayers").getString(1));
    }

    @Test
    public void missingConfigIsEmpty() {
        assertEquals(0, ConfigMerge.read(null).length());
        assertEquals(0, ConfigMerge.read("  ").length());
    }

    @Test
    public void bomIsStripped() throws Exception {
        assertEquals(4, ConfigMerge.read("﻿{\"columns\":4}").getInt("columns"));
    }

    @Test
    public void brokenConfigIsEmpty() {
        assertEquals(0, ConfigMerge.read("{columns:").length());
    }
}
