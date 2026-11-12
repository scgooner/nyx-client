package dev.nyx.util;

import java.util.HashMap;
import java.util.Map;

/** Frame-rate independent animation helpers. */
public final class Anim {
    private Anim() {}

    /** Exponential approach: same feel at any FPS. speed ~ 10-20 for UI. */
    public static float approach(float current, float target, float speed, float dt) {
        float k = 1f - (float) Math.exp(-speed * dt);
        float next = current + (target - current) * k;
        return Math.abs(target - next) < 0.001f ? target : next;
    }

    public static float easeOutCubic(float t) {
        t = clamp01(t);
        float u = 1 - t;
        return 1 - u * u * u;
    }

    public static float easeInCubic(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    public static float easeOutBack(float t) {
        t = clamp01(t);
        float c1 = 1.4f, c3 = c1 + 1;
        float u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    public static float clamp01(float t) { return t < 0 ? 0 : (t > 1 ? 1 : t); }

    /** Per-key animated 0..1 values for hover / toggle transitions. */
    public static final class Map01 {
        private final Map<Object, Float> values = new HashMap<>();

        public float get(Object key, boolean on, float dt, float speed) {
            float cur = values.getOrDefault(key, on ? 1f : 0f);
            cur = approach(cur, on ? 1f : 0f, speed, dt);
            values.put(key, cur);
            return cur;
        }

        public float peek(Object key) { return values.getOrDefault(key, 0f); }
    }
}
