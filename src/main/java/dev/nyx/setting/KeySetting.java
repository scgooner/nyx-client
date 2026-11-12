package dev.nyx.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A key chosen in the GUI, stored as the game's key value (-1 = unbound). */
public final class KeySetting extends Setting<Integer> {
    public KeySetting(String name, String description, int defaultKey) {
        super(name, description, defaultKey);
    }

    public int key() { return value; }

    @Override public JsonElement toJson() { return new JsonPrimitive(value); }

    @Override public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) value = json.getAsInt();
    }
}
