package org.hyperlib.util;

import java.util.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Someone must have made this already?
 * <p>
 * Loads a JSONObject into a hashmap with member functions that allow you to navigate the tree
 * without constantly having to cast.
 */
public class SensibleHashMap extends HashMap<String, Object> {
    /*
     * https://stackoverflow.com/questions/21720759/convert-a-json-string-to-a-hashmap
     */
    public static SensibleHashMap fromJSON(JSONObject json) throws JSONException {
        SensibleHashMap retMap = new SensibleHashMap();

        if (json != JSONObject.NULL) retMap = toMap(json);
        return retMap;
    }

    @SuppressWarnings("unchecked")
    protected static SensibleHashMap toMap(JSONObject object) throws JSONException {
        SensibleHashMap map = new SensibleHashMap();

        Iterator<String> keysItr = object.keys();
        while (keysItr.hasNext()) {
            String key = keysItr.next();
            Object value = object.get(key);

            if (value instanceof JSONArray) {
                value = toList((JSONArray) value);
            } else if (value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            map.put(key, value);
        }
        return map;
    }

    protected static List<Object> toList(JSONArray array) throws JSONException {
        List<Object> list = new ArrayList<Object>();
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value instanceof JSONArray) {
                value = toList((JSONArray) value);
            } else if (value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            list.add(value);
        }
        return list;
    }
    // ----------------------------------------------------------------

    public boolean getBool(String key) {
        if (this.get(key) instanceof Boolean value) return value;
        return Boolean.parseBoolean(this.get(key).toString());
    }

    public boolean getBoolOrDefault(String key, boolean defaultValue) {
        if (!this.containsKey(key)) return defaultValue;
        return getBool(key);
    }

    public int getInt(String key) {
        if (this.get(key) instanceof Integer value) return value;
        return Integer.parseInt(this.get(key).toString());
    }

    public int getIntOrDefault(String key, int defaultValue) {
        if (!this.containsKey(key)) return defaultValue;
        return getInt(key);
    }

    public float getFloat(String key) {
        if (this.get(key) instanceof Float value) return value;
        return Float.parseFloat(this.get(key).toString());
    }

    public float getFloatOrDefault(String key, float defaultValue) {
        if (!this.containsKey(key)) return defaultValue;
        return getFloat(key);
    }

    public String getString(String key) {
        if (this.get(key) instanceof String value) return value;
        return this.get(key).toString();
    }

    public String getStringOrDefault(String key, String defaultValue) {
        if (!this.containsKey(key)) return defaultValue;
        return getString(key);
    }


    public List<Integer> getIntListOrDefault(String key, List<Integer> defaultValue) {
        if (!this.containsKey(key)) return defaultValue;
        return this.getIntList(key);
    }

    @SuppressWarnings("unchecked")
    public List<Integer> getIntList(String key) {
        List<Object> listObj = (List<Object>) this.get(key);
        List<Integer> listOut = new ArrayList<>();
        for (int i = 0; i < listObj.size(); i++) {
            if (listObj.get(i) instanceof Integer value) {
                listOut.add(value);
            } else {
                listOut.add(Integer.parseInt(listObj.get(i).toString()));
            }
        }
        return listOut;
    }

    public List<Float> getFloatListOrDefault(String key, List<Float> defaultValue) {
        if (!this.containsKey(key)) return defaultValue;
        return this.getFloatList(key);
    }

    @SuppressWarnings("unchecked")
    public List<Float> getFloatList(String key) {
        List<Object> listObj = (List<Object>) this.get(key);
        List<Float> listOut = new ArrayList<>();
        for (int i = 0; i < listObj.size(); i++) {
            if (listObj.get(i) instanceof Float value) {
                listOut.add(value);
            } else {
                listOut.add(Float.parseFloat(listObj.get(i).toString()));
            }
        }
        return listOut;
    }

    public List<String> getStringListOrDefault(String key, List<String> defaultValue) {
        if (!this.containsKey(key)) return defaultValue;
        return this.getStringList(key);
    }

    @SuppressWarnings("unchecked")
    public List<String> getStringList(String key) {
        List<Object> listObj = (List<Object>) this.get(key);
        List<String> listOut = new ArrayList<>();
        for (int i = 0; i < listObj.size(); i++) {
            listOut.add(listObj.get(i).toString());
        }
        return listOut;
    }

    public Set<String> getStringSetOrDefault(String key, Set<String> defaultValue) {
        if (!this.containsKey(key)) return defaultValue;
        return this.getStringSet(key);
    }

    @SuppressWarnings("unchecked")
    public HashSet<String> getStringSet(String key) {
        List<String> listString = getStringList(key);
        return new HashSet<>(listString);
    }

    public SensibleHashMap getMapOrDefault(String key, SensibleHashMap defaultValue) {
        if (!this.containsKey(key)) return defaultValue;
        return this.getMap(key);
    }

    public SensibleHashMap getMap(String key) {
        return (SensibleHashMap) this.get(key);
    }
}
