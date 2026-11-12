package dev.nyx.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nyx.config.ConfigManager;
import dev.nyx.gui.ClickGuiScreen;
import dev.nyx.module.Module;
import dev.nyx.module.ModuleManager;
import dev.nyx.modules.combat.AutoTotem;
import dev.nyx.modules.combat.KillAura;
import dev.nyx.modules.combat.TriggerBot;
import dev.nyx.modules.misc.AntiAFK;
import dev.nyx.modules.movement.Flight;
import dev.nyx.modules.movement.Speed;
import dev.nyx.modules.movement.Spider;
import dev.nyx.modules.movement.Sprint;
import dev.nyx.modules.movement.Step;
import dev.nyx.modules.player.AutoRespawn;
import dev.nyx.modules.player.AutoTool;
import dev.nyx.modules.player.FastPlace;
import dev.nyx.modules.render.ESP;
import dev.nyx.modules.render.Fullbright;
import dev.nyx.modules.render.Zoom;
import dev.nyx.modules.world.AutoFish;
import dev.nyx.modules.world.FastBreak;
import dev.nyx.modules.world.Nuker;
import dev.nyx.setting.BoolSetting;
import dev.nyx.setting.NumberSetting;
import dev.nyx.setting.Setting;
import dev.nyx.util.Mc;
import dev.nyx.util.Reflect;
import dev.nyx.util.Targets;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * End-to-end test that runs inside a real Minecraft 26.3 client: drives the click GUI with
 * simulated keyboard and mouse input, then checks that every module changes the game as intended.
 * Results go to nyx-test-report.txt in the run folder; screenshots go to the run's screenshots folder.
 */
public final class NyxClientGameTest implements FabricClientGameTest {
    private final List<String> report = new ArrayList<>();
    private int failures;
    private BlockPos home;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            ctx.waitFor(mc -> mc.player != null && mc.level != null);
            server.runCommand("time set day");
            server.runCommand("weather clear");
            server.runCommand("difficulty easy");
            server.runCommand("gamemode survival @a");
            ctx.waitTicks(20);
            home = ctx.computeOnClient(mc -> mc.player.blockPosition());
            resetAll(ctx);

            section("Click GUI");
            guiTests(ctx);
            section("Modules");
            moduleTests(ctx, server);
            section("Configs");
            configTests(ctx);
            section("Stability");
            int crashes = ctx.computeOnClient(mc -> ModuleManager.failures);
            check("No module threw an error during the run", crashes == 0, crashes + " errors");
        } catch (Throwable t) {
            failures++;
            report.add("FAIL  Test run aborted: " + t);
            writeReport();
            throw t;
        }
        writeReport();
        if (failures > 0) throw new AssertionError(failures + " check(s) failed:\n" + String.join("\n", report));
    }

    // ------------------------------------------------------------------ GUI

    private void guiTests(ClientGameTestContext ctx) {
        // Open with Right Shift through the client's own keybind handling.
        ctx.getInput().holdKeyFor(InputConstants.KEY_RSHIFT, 3);
        boolean opened = waitUntil(ctx, mc -> Mc.screen() instanceof ClickGuiScreen, 40);
        check("Right Shift opens the GUI", opened, null);
        if (!opened) ctx.setScreen(ClickGuiScreen::new);
        ctx.waitTicks(15);
        ctx.takeScreenshot("01-gui-open");

        // Performance: CPU time per GUI frame while it sits open.
        ctx.runOnClient(mc -> { ClickGuiScreen.perfNanos = 0; ClickGuiScreen.perfFrames = 0; });
        ctx.waitTicks(60);
        long[] perf = ctx.computeOnClient(mc -> new long[]{ClickGuiScreen.perfNanos, ClickGuiScreen.perfFrames});
        double avgMs = perf[1] == 0 ? -1 : perf[0] / 1e6 / perf[1];
        check("GUI renders frames", perf[1] > 0, perf[1] + " frames in 3 s");
        check("GUI costs under 2 ms of CPU per frame", avgMs >= 0 && avgMs < 2.0, String.format(Locale.ROOT, "%.3f ms/frame", avgMs));

        Layout l = layout(ctx);
        KillAura aura = ModuleManager.get(KillAura.class);
        click(ctx, l, l.cardX(0), l.cardY(0), InputConstants.MOUSE_BUTTON_LEFT);
        check("Left-click on a module card enables it", aura.isEnabled(), null);
        click(ctx, l, l.cardX(0), l.cardY(0), InputConstants.MOUSE_BUTTON_LEFT);
        check("Left-click again disables it", !aura.isEnabled(), null);

        // Settings panel + slider drag-to-position.
        click(ctx, l, l.cardX(0), l.cardY(0), InputConstants.MOUSE_BUTTON_RIGHT);
        ctx.waitTicks(10);
        ctx.takeScreenshot("02-settings-panel");
        NumberSetting range = num(aura, "Range");
        l = layout(ctx); // settings panel geometry is known once it has been drawn
        float rowY = l.settingsTop() + 18 + 24; // description line, then keybind row
        click(ctx, l, l.panelX() + 14 + 0.5f, rowY + 21.5f, InputConstants.MOUSE_BUTTON_LEFT);
        check("Clicking the start of a slider sets its minimum", range.value() <= 1.11, "Range = " + range.format());
        range.reset();
        ctx.getInput().holdKeyFor(InputConstants.KEY_ESCAPE, 2);
        ctx.waitTicks(10);

        // Middle-click a card, then press a key to bind it.
        TriggerBot trigger = ModuleManager.get(TriggerBot.class);
        click(ctx, l, l.cardX(1), l.cardY(1), InputConstants.MOUSE_BUTTON_MIDDLE);
        ctx.getInput().holdKeyFor(InputConstants.KEY_G, 2);
        ctx.waitTicks(3);
        check("Middle-click + key press binds a module", trigger.getKey() == InputConstants.KEY_G, "key = " + trigger.getKey());
        trigger.setKey(-1);

        // Type-to-search, then toggle the first result.
        ctx.getInput().typeChars("fly");
        ctx.waitTicks(10);
        ctx.takeScreenshot("03-search");
        Flight flight = ModuleManager.get(Flight.class);
        click(ctx, l, l.cardX(0), l.cardY(0), InputConstants.MOUSE_BUTTON_LEFT);
        check("Search \"fly\" lists Flight first and it can be toggled", flight.isEnabled(), null);
        ctx.runOnClient(mc -> flight.setEnabled(false));
        // Clicking the card took focus from the search box, so one Esc clears the search
        // and must leave the menu open (a held key's repeats must not also close it).
        ctx.getInput().holdKeyFor(InputConstants.KEY_ESCAPE, 4);
        ctx.waitTicks(5);
        check("Esc clears the search without closing the menu", ctx.computeOnClient(mc -> Mc.screen() instanceof ClickGuiScreen), null);

        // Category switch.
        l = layout(ctx);
        click(ctx, l, l.x0() + 40, l.categoryY(1), InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(10);
        Sprint sprint = ModuleManager.get(Sprint.class);
        click(ctx, l, l.cardX(0), l.cardY(0), InputConstants.MOUSE_BUTTON_LEFT);
        check("Movement category shows Sprint first", sprint.isEnabled(), null);
        ctx.runOnClient(mc -> sprint.setEnabled(false));
        ctx.takeScreenshot("04-movement");

        // Configs page: create a profile from the GUI.
        l = layout(ctx);
        click(ctx, l, l.x0() + 40, l.configsY(), InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(10);
        click(ctx, l, l.contentX() + 40, l.bodyY() + 2 + 48 + 10, InputConstants.MOUSE_BUTTON_LEFT);
        ctx.getInput().typeChars("pvp");
        ctx.getInput().holdKeyFor(InputConstants.KEY_RETURN, 2);
        ctx.waitTicks(5);
        ctx.takeScreenshot("05-configs");
        boolean created = ctx.computeOnClient(mc -> ConfigManager.list().contains("pvp") && ConfigManager.active().equals("pvp"));
        check("Configs page creates and activates a new profile", created, "active = " + ctx.computeOnClient(mc -> ConfigManager.active()));

        // Close with Right Shift (fade/scale out, then the screen is removed).
        ctx.getInput().holdKeyFor(InputConstants.KEY_RSHIFT, 2);
        check("Right Shift closes the GUI", waitUntil(ctx, mc -> Mc.screen() == null, 40), null);
        ctx.waitTicks(5);
        // Must not instantly reopen from the same key press.
        check("Closing does not reopen the GUI", ctx.computeOnClient(mc -> Mc.screen() == null), null);
    }

    // ------------------------------------------------------------------ modules

    private void moduleTests(ClientGameTestContext ctx, TestServerContext server) {
        int x = home.getX(), y = home.getY(), z = home.getZ();
        clearArena(server);

        // Render
        enable(ctx, Fullbright.class);
        ctx.waitTicks(5);
        check("Fullbright gives night vision", ctx.computeOnClient(mc -> mc.player.hasEffect(MobEffects.NIGHT_VISION)), null);
        disable(ctx, Fullbright.class);
        ctx.waitTicks(3);
        check("Fullbright removes it again when disabled", ctx.computeOnClient(mc -> !mc.player.hasEffect(MobEffects.NIGHT_VISION)), null);

        server.runCommand(String.format("summon pig %d %d %d {NoAI:1b}", x, y, z + 4));
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> bool(ModuleManager.get(ESP.class), "Passives").set(true));
        enable(ctx, ESP.class);
        ctx.waitTicks(5);
        check("ESP outlines a nearby pig", ctx.computeOnClient(mc -> find(mc, Pig.class) != null && find(mc, Pig.class).isCurrentlyGlowing()), null);
        ctx.takeScreenshot("06-esp-hud");
        disable(ctx, ESP.class);
        ctx.waitTicks(3);
        check("ESP clears the outline when disabled", ctx.computeOnClient(mc -> find(mc, Pig.class) != null && !find(mc, Pig.class).isCurrentlyGlowing()), null);
        clearArena(server);

        enable(ctx, Zoom.class);
        int fov0 = ctx.computeOnClient(mc -> mc.options.fov().get());
        ctx.getInput().holdKey(InputConstants.KEY_C);
        ctx.waitTicks(25);
        int zoomed = ctx.computeOnClient(mc -> mc.options.fov().get());
        ctx.getInput().releaseKey(InputConstants.KEY_C);
        ctx.waitTicks(40);
        int after = ctx.computeOnClient(mc -> mc.options.fov().get());
        check("Zoom narrows the FOV while C is held", zoomed <= 40, "FOV " + fov0 + " -> " + zoomed);
        check("Zoom restores the FOV on release", after == fov0, "FOV back to " + after);
        disable(ctx, Zoom.class);

        // Movement
        enable(ctx, Step.class);
        ctx.waitTicks(3);
        check("Step raises step height", attr(ctx) >= 0.99, "step = " + attr(ctx));
        disable(ctx, Step.class);
        ctx.waitTicks(2);
        check("Step restores vanilla step height", Math.abs(attr(ctx) - 0.6) < 0.01, "step = " + attr(ctx));

        enable(ctx, Flight.class);
        ctx.waitTicks(5);
        check("Flight makes you fly in survival", ctx.computeOnClient(mc -> mc.player.getAbilities().flying), null);
        disable(ctx, Flight.class);
        ctx.waitTicks(3);
        check("Flight turns survival flying off again", ctx.computeOnClient(mc -> !mc.player.getAbilities().mayfly), null);
        ctx.waitTicks(40);

        teleportHome(ctx, server);
        enable(ctx, Sprint.class);
        ctx.getInput().holdKey(o -> o.keyUp);
        ctx.waitTicks(10);
        boolean sprinting = ctx.computeOnClient(mc -> mc.player.isSprinting());
        ctx.getInput().releaseKey(o -> o.keyUp);
        check("Sprint sprints while walking forward", sprinting, null);
        disable(ctx, Sprint.class);
        ctx.waitTicks(10);

        teleportHome(ctx, server);
        enable(ctx, Speed.class);
        ctx.getInput().holdKey(o -> o.keyUp);
        ctx.waitTicks(8);
        double speed = ctx.computeOnClient(mc -> mc.player.getDeltaMovement().horizontalDistance());
        ctx.getInput().releaseKey(o -> o.keyUp);
        check("Speed moves faster than sprinting", speed > 0.3, String.format(Locale.ROOT, "%.3f blocks/tick", speed));
        disable(ctx, Speed.class);
        ctx.waitTicks(10);

        teleportHome(ctx, server);
        server.runCommand(String.format("fill %d %d %d %d %d %d stone", x - 1, y, z + 1, x + 1, y + 5, z + 1));
        ctx.waitTicks(5);
        double y0 = ctx.computeOnClient(mc -> mc.player.getY());
        enable(ctx, Spider.class);
        ctx.getInput().holdKey(o -> o.keyUp);
        ctx.waitTicks(20);
        double y1 = ctx.computeOnClient(mc -> mc.player.getY());
        ctx.getInput().releaseKey(o -> o.keyUp);
        check("Spider climbs a wall", y1 - y0 > 1.5, String.format(Locale.ROOT, "climbed %.2f blocks", y1 - y0));
        disable(ctx, Spider.class);
        clearArena(server);
        ctx.waitTicks(30);

        // Combat
        teleportHome(ctx, server);
        server.runCommand(String.format("summon husk %d %d %d {NoAI:1b}", x, y, z + 2));
        ctx.waitTicks(10);
        enable(ctx, KillAura.class);
        ctx.waitTicks(50);
        float auraHp = ctx.computeOnClient(mc -> health(mc, Husk.class));
        check("KillAura attacks a nearby husk", auraHp < 20f, "husk health " + auraHp);
        disable(ctx, KillAura.class);
        clearArena(server);

        teleportHome(ctx, server);
        server.runCommand(String.format("summon husk %d %d %d {NoAI:1b}", x, y, z + 2));
        ctx.waitTicks(10);
        ctx.runOnClient(mc -> {
            Entity husk = find(mc, Husk.class);
            if (husk != null) Targets.face(mc.player, husk);
        });
        ctx.waitTicks(2);
        enable(ctx, TriggerBot.class);
        ctx.waitTicks(50);
        float trigHp = ctx.computeOnClient(mc -> health(mc, Husk.class));
        check("TriggerBot hits the husk under the crosshair", trigHp < 20f, "husk health " + trigHp);
        disable(ctx, TriggerBot.class);
        clearArena(server);

        server.runCommand("clear @a");
        server.runCommand("give @a minecraft:totem_of_undying");
        ctx.waitTicks(5);
        enable(ctx, AutoTotem.class);
        ctx.waitTicks(10);
        check("AutoTotem moves a totem to the offhand", ctx.computeOnClient(mc -> mc.player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING), null);
        disable(ctx, AutoTotem.class);
        server.runCommand("clear @a");

        // Player
        teleportHome(ctx, server);
        server.runCommand("give @a minecraft:diamond_pickaxe");
        server.runCommand(String.format("setblock %d %d %d stone", x, y, z + 1));
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(4));
        ctx.getInput().lookAt(new BlockPos(x, y, z + 1));
        ctx.waitTicks(2);
        enable(ctx, AutoTool.class);
        ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(4);
        int mining = ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
        ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(4);
        int afterMining = ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
        check("AutoTool switches to the pickaxe while mining", mining == 0, "slot " + mining);
        check("AutoTool switches back afterwards", afterMining == 4, "slot " + afterMining);
        disable(ctx, AutoTool.class);
        server.runCommand("clear @a");
        clearArena(server);

        Field rightClickDelay = Reflect.field(Minecraft.class, "rightClickDelay");
        ctx.runOnClient(mc -> Reflect.setInt(rightClickDelay, mc, 4));
        enable(ctx, FastPlace.class);
        ctx.waitTicks(2);
        check("FastPlace removes the place delay", ctx.computeOnClient(mc -> Reflect.getInt(rightClickDelay, mc, -1)) == 0, null);
        disable(ctx, FastPlace.class);

        enable(ctx, AutoRespawn.class);
        server.runCommand("kill @a");
        boolean respawned = waitUntil(ctx, mc -> mc.player != null && !mc.player.isDeadOrDying() && !(Mc.screen() instanceof DeathScreen), 100);
        check("AutoRespawn respawns after death", respawned, null);
        disable(ctx, AutoRespawn.class);
        ctx.waitTicks(20);
        home = ctx.computeOnClient(mc -> mc.player.blockPosition());
        x = home.getX();
        y = home.getY();
        z = home.getZ();
        clearArena(server);

        // World
        Field destroyDelay = Reflect.field(MultiPlayerGameMode.class, "destroyDelay");
        ctx.runOnClient(mc -> Reflect.setInt(destroyDelay, mc.gameMode, 5));
        enable(ctx, FastBreak.class);
        ctx.waitTicks(2);
        check("FastBreak removes the break delay", ctx.computeOnClient(mc -> Reflect.getInt(destroyDelay, mc.gameMode, -1)) == 0, null);
        disable(ctx, FastBreak.class);

        server.runCommand("gamemode creative @a");
        teleportHome(ctx, server);
        BlockPos np = ctx.computeOnClient(mc -> mc.player.blockPosition());
        final int fx = np.getX(), fy = np.getY(), fz = np.getZ();
        server.runCommand(String.format("fill %d %d %d %d %d %d stone", fx + 1, fy, fz + 1, fx + 3, fy + 1, fz + 3));
        ctx.waitTicks(5);
        int before = server.computeOnServer(s -> countStone(s.overworld(), fx, fy, fz));
        enable(ctx, Nuker.class);
        ctx.waitTicks(40);
        int left = server.computeOnServer(s -> countStone(s.overworld(), fx, fy, fz));
        check("Nuker breaks nearby blocks", before > 0 && left < before / 2, before + " -> " + left + " stone");
        disable(ctx, Nuker.class);
        server.runCommand("gamemode survival @a");
        clearArena(server);

        teleportHome(ctx, server);
        server.runCommand("clear @a");
        server.runCommand("give @a minecraft:fishing_rod");
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
        ctx.getInput().lookAt(0, -30);
        enable(ctx, AutoFish.class);
        boolean cast = waitUntil(ctx, mc -> mc.player.fishing != null, 40);
        check("AutoFish casts the rod", cast, null);
        if (cast) {
            Field biting = Reflect.field(FishingHook.class, "biting");
            ctx.runOnClient(mc -> {
                try { biting.setBoolean(mc.player.fishing, true); } catch (Exception ignored) {}
            });
            ctx.waitTicks(3);
            String caught = ctx.computeOnClient(mc -> ModuleManager.get(AutoFish.class).suffix());
            check("AutoFish reels in on a bite", "1".equals(caught), "catches = " + caught);
        }
        disable(ctx, AutoFish.class);
        server.runCommand("clear @a");

        // Misc
        ctx.runOnClient(mc -> num(ModuleManager.get(AntiAFK.class), "Interval").set(5.0));
        float yaw0 = ctx.computeOnClient(mc -> mc.player.getYRot());
        enable(ctx, AntiAFK.class);
        ctx.waitTicks(110);
        float yaw1 = ctx.computeOnClient(mc -> mc.player.getYRot());
        check("AntiAFK turns the player after its interval", Math.abs(yaw1 - yaw0) > 0.01f, String.format(Locale.ROOT, "yaw %.1f -> %.1f", yaw0, yaw1));
        disable(ctx, AntiAFK.class);
    }

    // ------------------------------------------------------------------ configs

    private void configTests(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            ModuleManager.get(Sprint.class).setEnabled(true);
            num(ModuleManager.get(KillAura.class), "Range").set(5.0);
            ModuleManager.get(TriggerBot.class).setKey(InputConstants.KEY_H);
            ConfigManager.saveNow();
        });
        String json = ctx.computeOnClient(mc -> {
            try {
                return Files.readString(ConfigManager.folder().resolve(ConfigManager.active() + ".json"));
            } catch (Exception e) {
                return "";
            }
        });
        check("Profile JSON is written to disk", json.contains("\"Sprint\"") && json.contains("\"Range\": 5.0"), json.length() + " bytes");

        ctx.runOnClient(mc -> {
            for (Module m : ModuleManager.all()) m.resetToDefaults();
            ConfigManager.load(ConfigManager.active());
        });
        boolean restored = ctx.computeOnClient(mc -> ModuleManager.get(Sprint.class).isEnabled()
                && num(ModuleManager.get(KillAura.class), "Range").value() == 5.0
                && ModuleManager.get(TriggerBot.class).getKey() == InputConstants.KEY_H);
        check("Loading the profile restores modules, settings and keybinds", restored, null);

        boolean switched = ctx.computeOnClient(mc -> ConfigManager.load("default") && ConfigManager.active().equals("default")
                && !ModuleManager.get(Sprint.class).isEnabled());
        check("Switching profiles applies the other profile", switched, null);
        resetAll(ctx);
    }

    // ------------------------------------------------------------------ helpers

    /** The GUI's own last-frame layout, so clicks land exactly where things were drawn. */
    private record Layout(double scale, ClickGuiScreen.Geometry g) {
        float x0() { return g.x0(); }
        float contentX() { return g.contentX(); }
        float bodyY() { return g.bodyY(); }
        float cardX(int i) { return g.cardCenterX(i); }
        float cardY(int i) { return g.cardCenterY(i); }
        float categoryY(int i) { return g.categoryCenterY(i); }
        float configsY() { return g.configsCenterY(); }
        float panelX() { return g.panelX(); }
        float settingsTop() { return g.settingsTop(); }
    }

    private Layout layout(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> {
            Screen s = Mc.screen();
            if (!(s instanceof ClickGuiScreen gui)) throw new AssertionError("Click GUI is not open (screen: " + s + ")");
            return new Layout(mc.getWindow().getGuiScale(), gui.geometry());
        });
    }

    private void click(ClientGameTestContext ctx, Layout l, float guiX, float guiY, int button) {
        ctx.getInput().setCursorPos(guiX * l.scale(), guiY * l.scale());
        ctx.waitTick();
        ctx.getInput().pressMouse(button);
        ctx.waitTicks(2);
    }

    private boolean waitUntil(ClientGameTestContext ctx, java.util.function.Predicate<Minecraft> condition, int ticks) {
        for (int i = 0; i < ticks; i++) {
            if (ctx.computeOnClient(condition::test)) return true;
            ctx.waitTick();
        }
        return ctx.computeOnClient(condition::test);
    }

    private void enable(ClientGameTestContext ctx, Class<? extends Module> type) {
        ctx.runOnClient(mc -> ModuleManager.get(type).setEnabled(true));
    }

    private void disable(ClientGameTestContext ctx, Class<? extends Module> type) {
        ctx.runOnClient(mc -> ModuleManager.get(type).setEnabled(false));
    }

    private void resetAll(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> { for (Module m : ModuleManager.all()) m.resetToDefaults(); });
    }

    private void teleportHome(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand(String.format("tp @a %d %d %d 0 0", home.getX(), home.getY(), home.getZ()));
        ctx.waitTicks(5);
    }

    private void clearArena(TestServerContext server) {
        int x = home.getX(), y = home.getY(), z = home.getZ();
        server.runCommand("kill @e[type=!player]");
        server.runCommand(String.format("fill %d %d %d %d %d %d air", x - 5, y, z - 5, x + 5, y + 6, z + 5));
        server.runCommand(String.format("fill %d %d %d %d %d %d stone", x - 5, y - 1, z - 5, x + 5, y - 1, z + 5));
    }

    private static int countStone(ServerLevel level, int x, int y, int z) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(x + 1, y, z + 1, x + 3, y + 1, z + 3)) {
            if (level.getBlockState(p).is(Blocks.STONE)) n++;
        }
        return n;
    }

    private double attr(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> mc.player.getAttribute(Attributes.STEP_HEIGHT).getBaseValue());
    }

    private static Entity find(Minecraft mc, Class<? extends Entity> type) {
        Entity best = null;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (type.isInstance(e) && (best == null || e.distanceToSqr(mc.player) < best.distanceToSqr(mc.player))) best = e;
        }
        return best;
    }

    private static float health(Minecraft mc, Class<? extends Entity> type) {
        Entity e = find(mc, type);
        return e instanceof LivingEntity le ? (le.isAlive() ? le.getHealth() : 0f) : 0f;
    }

    private static NumberSetting num(Module m, String name) { return (NumberSetting) setting(m, name); }

    private static BoolSetting bool(Module m, String name) { return (BoolSetting) setting(m, name); }

    private static Setting<?> setting(Module m, String name) {
        for (Setting<?> s : m.getSettings()) if (s.getName().equals(name)) return s;
        throw new IllegalArgumentException(m.getName() + " has no setting " + name);
    }

    private void section(String title) { report.add("\n## " + title); }

    private void check(String name, boolean ok, String detail) {
        if (!ok) failures++;
        report.add((ok ? "PASS  " : "FAIL  ") + name + (detail == null ? "" : "  (" + detail + ")"));
    }

    private void writeReport() {
        List<String> out = new ArrayList<>();
        int passed = (int) report.stream().filter(s -> s.startsWith("PASS")).count();
        out.add("Nyx Client in-game test: " + passed + " passed, " + failures + " failed");
        out.addAll(report);
        try {
            Files.write(Path.of("nyx-test-report.txt"), out);
        } catch (Exception ignored) {
        }
    }
}
