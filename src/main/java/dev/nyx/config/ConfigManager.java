package dev.nyx.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nyx.NyxClient;
import dev.nyx.module.Module;
import dev.nyx.module.ModuleManager;
import dev.nyx.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Profiles live in config/nyx/profiles/&lt;name&gt;.json; the active profile name is kept in
 * config/nyx/client.json. Every change autosaves to the active profile about a second later,
 * and again when the game closes.
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path dir, profiles, stateFile;
    private static String active = "default";
    private static boolean dirty, loading = true;
    private static long dirtySince;

    private ConfigManager() {}

    public static void init() {
        dir = FabricLoader.getInstance().getConfigDir().resolve("nyx");
        profiles = dir.resolve("profiles");
        stateFile = dir.resolve("client.json");
        try {
            Files.createDirectories(profiles);
        } catch (IOException e) {
            NyxClient.LOG.error("Could not create config folder", e);
        }
        try {
            if (Files.exists(stateFile)) {
                JsonObject state = JsonParser.parseString(Files.readString(stateFile)).getAsJsonObject();
                if (state.has("profile")) active = sanitize(state.get("profile").getAsString());
            }
        } catch (Exception e) {
            NyxClient.LOG.warn("Could not read client.json, using default profile", e);
        }
        loading = false;
        if (Files.exists(profileFile(active))) load(active);
        else saveNow();
    }

    public static void markDirty() {
        if (loading) return;
        if (!dirty) dirtySince = System.currentTimeMillis();
        dirty = true;
    }

    public static void tick() {
        if (dirty && System.currentTimeMillis() - dirtySince > 1000) saveNow();
    }

    public static void saveNow() { save(active); }

    public static String active() { return active; }

    public static boolean save(String name) {
        name = sanitize(name);
        if (name.isEmpty() || profiles == null) return false;
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject mods = new JsonObject();
        for (Module m : ModuleManager.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.isEnabled());
            o.addProperty("key", m.getKey());
            JsonObject settings = new JsonObject();
            for (Setting<?> s : m.getSettings()) settings.add(s.getName(), s.toJson());
            o.add("settings", settings);
            mods.add(m.getName(), o);
        }
        root.add("modules", mods);
        try {
            Path target = profileFile(name);
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            if (name.equals(active)) dirty = false;
            return true;
        } catch (IOException e) {
            NyxClient.LOG.error("Could not save profile {}", name, e);
            return false;
        }
    }

    public static boolean load(String name) {
        name = sanitize(name);
        Path file = profileFile(name);
        if (!Files.exists(file)) return false;
        loading = true;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            JsonObject mods = root.has("modules") ? root.getAsJsonObject("modules") : new JsonObject();
            for (Module m : ModuleManager.all()) {
                m.resetToDefaults();
                if (!mods.has(m.getName())) continue;
                JsonObject o = mods.getAsJsonObject(m.getName());
                if (o.has("key")) m.setKey(o.get("key").getAsInt());
                if (o.has("settings")) {
                    JsonObject settings = o.getAsJsonObject("settings");
                    for (Setting<?> s : m.getSettings()) {
                        JsonElement e = settings.get(s.getName());
                        if (e != null) s.fromJson(e);
                    }
                }
                if (o.has("enabled") && !m.isAction()) m.setEnabledSilently(o.get("enabled").getAsBoolean());
            }
            active = name;
            writeState();
            dirty = false;
            return true;
        } catch (Exception e) {
            NyxClient.LOG.error("Could not load profile {}", name, e);
            return false;
        } finally {
            loading = false;
        }
    }

    /** Creates (or overwrites) a profile from the current settings and makes it active. */
    public static boolean saveAs(String name) {
        name = sanitize(name);
        if (name.isEmpty()) return false;
        active = name;
        writeState();
        return save(name);
    }

    public static boolean delete(String name) {
        name = sanitize(name);
        if (name.equals(active)) return false;
        try {
            return Files.deleteIfExists(profileFile(name));
        } catch (IOException e) {
            NyxClient.LOG.error("Could not delete profile {}", name, e);
            return false;
        }
    }

    public static List<String> list() {
        List<String> out = new ArrayList<>();
        if (profiles == null) return out;
        try (Stream<Path> s = Files.list(profiles)) {
            s.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> out.add(p.getFileName().toString().replaceFirst("\\.json$", "")));
        } catch (IOException e) {
            NyxClient.LOG.error("Could not list profiles", e);
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public static Path folder() { return profiles; }

    private static void writeState() {
        try {
            JsonObject state = new JsonObject();
            state.addProperty("profile", active);
            Files.writeString(stateFile, GSON.toJson(state), StandardCharsets.UTF_8);
        } catch (IOException e) {
            NyxClient.LOG.error("Could not write client.json", e);
        }
    }

    private static Path profileFile(String name) { return profiles.resolve(name + ".json"); }

    public static String sanitize(String name) {
        String s = name == null ? "" : name.trim().replaceAll("[^A-Za-z0-9 _.-]", "");
        s = s.replaceAll("^\\.+", "");
        return s.length() > 32 ? s.substring(0, 32) : s;
    }
}
