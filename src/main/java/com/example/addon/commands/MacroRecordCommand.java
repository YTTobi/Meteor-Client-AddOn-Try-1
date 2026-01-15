package com.example.addon.commands;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.command.CommandSource;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class MacroRecordCommand extends Command {
    private static class MacroFrame {
        public float yaw;
        public float pitch;

        public boolean forward, back, left, right;
        public boolean jump, sneak, sprint;

        public boolean attack, use;
        public int selectedSlot;
    }

    private static final List<MacroFrame> frames = new ArrayList<>();

    private static boolean recording = false;
    private static boolean playing = false;
    private static boolean loop = false;

    private static int playIndex = 0;
    private static int lastFrameIndex = 0;

    private static double startX, startY, startZ;
    private static float startYaw, startPitch;

    // Seamless loop smooth transition
    private static boolean smoothLoopReset = false;
    private static float loopStartYaw, loopStartPitch;
    private static float loopEndYaw, loopEndPitch;
    private static int loopSmoothTicks = 0;
    private static final int LOOP_SMOOTH_DURATION = 10;

    private static final Gson GSON = new Gson();
    private static final File macroFolder = new File("macros");

    public MacroRecordCommand() {
        super("macrorecord", "Tick-synced macro recorder with seamless loop smoothing.");
        if (!macroFolder.exists()) macroFolder.mkdirs();
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {

        builder.then(literal("start").executes(ctx -> {
            if (mc.player == null) {
                info("Join a world first.");
                return SINGLE_SUCCESS;
            }

            frames.clear();
            recording = true;
            playing = false;
            playIndex = 0;

            startX = mc.player.getX();
            startY = mc.player.getY();
            startZ = mc.player.getZ();

            startYaw = mc.player.getYaw();
            startPitch = mc.player.getPitch();

            info("Recording macro...");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("stop").executes(ctx -> {
            recording = false;
            info("Stopped. Frames: " + frames.size());
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("play").executes(ctx -> {
            startPlayback();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("loop").then(argument("mode", StringArgumentType.word()).executes(ctx -> {
            loop = StringArgumentType.getString(ctx, "mode").equalsIgnoreCase("on");
            info("Loop mode: " + (loop ? "ON" : "OFF"));
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("save").then(argument("name", StringArgumentType.word()).executes(ctx -> {
            if (frames.isEmpty()) {
                info("Nothing to save.");
                return SINGLE_SUCCESS;
            }
            String name = StringArgumentType.getString(ctx, "name");
            File f = new File(macroFolder, name + ".json");
            try (FileWriter w = new FileWriter(f)) {
                w.write(GSON.toJson(frames));
                info("Saved macro as \"" + name + "\"");
            } catch (Exception e) {
                info("Failed to save macro.");
            }
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("load").then(argument("name", StringArgumentType.word()).executes(ctx -> {
            String name = StringArgumentType.getString(ctx, "name");
            File f = new File(macroFolder, name + ".json");

            if (!f.exists()) {
                info("Macro not found.");
                return SINGLE_SUCCESS;
            }

            try (FileReader r = new FileReader(f)) {
                Type type = new TypeToken<ArrayList<MacroFrame>>() {}.getType();
                List<MacroFrame> loaded = GSON.fromJson(r, type);
                frames.clear();
                if (loaded != null) frames.addAll(loaded);
                info("Loaded \"" + name + "\" (" + frames.size() + " frames)");
            } catch (Exception e) {
                info("Failed to load macro.");
            }

            return SINGLE_SUCCESS;
        })));

        builder.then(literal("list").executes(ctx -> {
            String[] files = macroFolder.list((d, n) -> n.endsWith(".json"));
            if (files == null || files.length == 0) {
                info("No saved macros.");
                return SINGLE_SUCCESS;
            }
            info("Saved macros:");
            for (String f : files) info("- " + f.replace(".json", ""));
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("clear").executes(ctx -> {
            frames.clear();
            recording = false;
            playing = false;
            playIndex = 0;
            clearKeys();
            info("Macro cleared.");
            return SINGLE_SUCCESS;
        }));
    }

    @EventHandler
    private void onTick(TickEvent.Pre e) {
        if (mc.player == null || mc.options == null) return;

        ClientPlayerEntity p = mc.player;
        GameOptions o = mc.options;

        // ======================================================
        //   SMOOTH LOOP RESET (SEAMLESS TRANSITION)
        // ======================================================
        if (smoothLoopReset) {
            loopSmoothTicks++;

            float t = loopSmoothTicks / (float) LOOP_SMOOTH_DURATION;
            t = Math.max(0, Math.min(1, t));

            float yawDiff = normalizeYaw(loopEndYaw - loopStartYaw);
            float smoothYaw = normalizeYaw(loopStartYaw + yawDiff * t);
            float smoothPitch = clampPitch(loopStartPitch + (loopEndPitch - loopStartPitch) * t);

            p.setYaw(smoothYaw);
            p.setPitch(smoothPitch);

            clearKeys(); // no movement during transition

            if (t >= 1) {
                // finished smoothing → now reset position & restart loop
                p.setPosition(startX, startY, startZ);
                playIndex = 0;
                lastFrameIndex = 0;
                smoothLoopReset = false;
            }
            return;
        }

        // ======================================================
        //   RECORD
        // ======================================================
        if (recording) {
            if (mc.currentScreen != null) return;

            MacroFrame f = new MacroFrame();

            f.yaw = p.getYaw();
            f.pitch = p.getPitch();

            f.forward = o.forwardKey.isPressed();
            f.back = o.backKey.isPressed();
            f.left = o.leftKey.isPressed();
            f.right = o.rightKey.isPressed();

            f.jump = o.jumpKey.isPressed();
            f.sneak = o.sneakKey.isPressed();
            f.sprint = o.sprintKey.isPressed();

            f.attack = o.attackKey.isPressed();
            f.use = o.useKey.isPressed();

            f.selectedSlot = p.getInventory().getSelectedSlot();

            frames.add(f);
        }

        // ======================================================
        //   PLAYBACK
        // ======================================================
        if (playing) {
            if (playIndex >= frames.size()) {

                if (loop) {
                    // Prepare seamless head motion back to start
                    MacroFrame last = frames.get(frames.size() - 1);
                    MacroFrame first = frames.get(0);

                    loopStartYaw = last.yaw;
                    loopStartPitch = last.pitch;

                    loopEndYaw = first.yaw;
                    loopEndPitch = first.pitch;

                    loopSmoothTicks = 0;
                    smoothLoopReset = true;

                    clearKeys();
                    return;
                }

                playing = false;
                clearKeys();
                info("Playback finished.");
                return;
            }

            MacroFrame f = frames.get(playIndex);

            clearKeys();

            o.forwardKey.setPressed(f.forward);
            o.backKey.setPressed(f.back);
            o.leftKey.setPressed(f.left);
            o.rightKey.setPressed(f.right);

            o.jumpKey.setPressed(f.jump);
            o.sneakKey.setPressed(f.sneak);
            o.sprintKey.setPressed(f.sprint);

            o.attackKey.setPressed(f.attack);
            o.useKey.setPressed(f.use);

            p.getInventory().setSelectedSlot(f.selectedSlot);

            lastFrameIndex = playIndex;
            playIndex++;
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!playing || smoothLoopReset) return;
        if (mc.player == null || frames.isEmpty()) return;

        ClientPlayerEntity p = mc.player;

        int i = lastFrameIndex;
        int next = Math.min(i + 1, frames.size() - 1);

        MacroFrame a = frames.get(i);
        MacroFrame b = frames.get(next);

        float t = event.tickDelta;
        t = Math.max(0, Math.min(1, t));

        float dyaw = normalizeYaw(b.yaw - a.yaw);
        float yaw = normalizeYaw(a.yaw + dyaw * t);

        float pitch = clampPitch(a.pitch + (b.pitch - a.pitch) * t);

        p.setYaw(yaw);
        p.setPitch(pitch);
    }

    private static float normalizeYaw(float yaw) {
        yaw %= 360f;
        if (yaw < -180f) yaw += 360f;
        if (yaw > 180f) yaw -= 360f;
        return yaw;
    }

    private static float clampPitch(float pitch) {
        return Math.max(-89.9f, Math.min(89.9f, pitch));
    }

    private void clearKeys() {
        if (mc.options == null) return;

        GameOptions o = mc.options;

        o.forwardKey.setPressed(false);
        o.backKey.setPressed(false);
        o.leftKey.setPressed(false);
        o.rightKey.setPressed(false);

        o.jumpKey.setPressed(false);
        o.sneakKey.setPressed(false);
        o.sprintKey.setPressed(false);

        o.attackKey.setPressed(false);
        o.useKey.setPressed(false);
    }

    private void startPlayback() {
        if (frames.isEmpty()) {
            info("No macro recorded or loaded.");
            return;
        }

        if (mc.player == null) {
            info("Join a world first.");
            return;
        }

        playing = true;
        recording = false;
        playIndex = 0;

        mc.player.setPosition(startX, startY, startZ);
        mc.player.setYaw(startYaw);
        mc.player.setPitch(startPitch);

        clearKeys();
        info("Playback started.");
    }
}
