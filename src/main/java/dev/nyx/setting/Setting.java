package dev.nyx.setting;

import com.google.gson.JsonElement;
import dev.nyx.config.ConfigManager;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** A single configurable value on a module. Every change marks the config dirty so it autosaves. */
public abstract class Setting<T> {
    private final String name;
    private final String description;
    private final T defaultValue;
    protected T value;
    private BooleanSupplier visible = () -> true;

    protected Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public T get() { return value; }
    public T getDefault() { return defaultValue; }

    public void set(T v) {
        T clean = sanitize(v);
        if (!Objects.equals(value, clean)) {
            value = clean;
            ConfigManager.markDirty();
        }
    }

    public void reset() { value = defaultValue; }

    protected T sanitize(T v) { return v; }

    /** Only show this setting in the GUI while the condition holds (e.g. a mode-specific option). */
    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S when(BooleanSupplier condition) {
        this.visible = condition;
        return (S) this;
    }

    public boolean isVisible() { return visible.getAsBoolean(); }

    public abstract JsonElement toJson();
    public abstract void fromJson(JsonElement json);
}
