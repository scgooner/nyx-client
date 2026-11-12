package dev.nyx.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class NumberSetting extends Setting<Double> {
    private final double min, max, step;

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
        super(name, description, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public double min() { return min; }
    public double max() { return max; }
    public double step() { return step; }

    public double value() { return value; }
    public float f() { return value.floatValue(); }
    public int i() { return (int) Math.round(value); }

    /** 0..1 position of the value on the slider track. */
    public double fraction() { return (value - min) / (max - min); }

    public void setFraction(double frac) { set(min + Math.max(0, Math.min(1, frac)) * (max - min)); }

    @Override
    protected Double sanitize(Double v) {
        double d = v == null ? getDefault() : v;
        d = Math.max(min, Math.min(max, d));
        if (step > 0) d = Math.round((d - min) / step) * step + min;
        d = Math.max(min, Math.min(max, d));
        return Math.round(d * 1e6) / 1e6;
    }

    public String format() {
        if (step >= 1) return String.valueOf(Math.round(value));
        int decimals = step >= 0.1 ? 1 : 2;
        return String.format(java.util.Locale.ROOT, "%." + decimals + "f", value);
    }

    @Override public JsonElement toJson() { return new JsonPrimitive(value); }

    @Override public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) value = sanitize(json.getAsDouble());
    }
}
