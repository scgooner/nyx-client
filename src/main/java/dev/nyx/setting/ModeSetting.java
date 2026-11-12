package dev.nyx.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

public final class ModeSetting extends Setting<String> {
    private final List<String> modes;

    public ModeSetting(String name, String description, String defaultValue, String... modes) {
        super(name, description, defaultValue);
        this.modes = List.of(modes);
    }

    public List<String> modes() { return modes; }
    public boolean is(String mode) { return value.equals(mode); }

    @Override
    protected String sanitize(String v) { return modes.contains(v) ? v : getDefault(); }

    @Override public JsonElement toJson() { return new JsonPrimitive(value); }

    @Override public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) value = sanitize(json.getAsString());
    }
}
