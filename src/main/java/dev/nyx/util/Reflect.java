package dev.nyx.util;

import dev.nyx.NyxClient;

import java.lang.reflect.Field;

/**
 * Access to a few private game fields. 26.3 ships unobfuscated, so field names are stable; the
 * names used here are also checked against the game jar in CI (tools/verify-reflection.sh).
 * A missing field degrades to a no-op instead of a crash.
 */
public final class Reflect {
    private Reflect() {}

    public static Field field(Class<?> owner, String name) {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) {
                break;
            }
        }
        NyxClient.LOG.warn("Field {}.{} not found", owner.getSimpleName(), name);
        return null;
    }

    public static int getInt(Field f, Object target, int fallback) {
        if (f == null || target == null) return fallback;
        try { return f.getInt(target); } catch (Throwable t) { return fallback; }
    }

    public static void setInt(Field f, Object target, int value) {
        if (f == null || target == null) return;
        try { f.setInt(target, value); } catch (Throwable ignored) {}
    }

    public static boolean getBool(Field f, Object target, boolean fallback) {
        if (f == null || target == null) return fallback;
        try { return f.getBoolean(target); } catch (Throwable t) { return fallback; }
    }
}
