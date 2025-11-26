package com.example.addon.modules.Hypixel;

import com.example.addon.AddonTemplate;
import com.example.addon.modules.Hypixel.MiningMacro;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.util.InputUtil;

import java.util.*;

public class PlayerDetection extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // ---------- SETTINGS ----------
    private final Setting<Double> radius1 = sgGeneral.add(new DoubleSetting.Builder()
        .name("radius-1")
        .description("Main scan radius.")
        .defaultValue(10)
        .min(1)
        .max(100)
        .build());

    private final Setting<Double> radius2 = sgGeneral.add(new DoubleSetting.Builder()
        .name("radius-2")
        .description("Large scan radius.")
        .defaultValue(50)
        .min(5)
        .max(200)
        .build());

    private final Setting<Double> time1 = sgGeneral.add(new DoubleSetting.Builder()
        .name("stay-time-radius-1")
        .description("Seconds a player must stay in radius 1 before triggering.")
        .defaultValue(3)
        .min(0.1)
        .max(30)
        .build());

    private final Setting<Double> time2 = sgGeneral.add(new DoubleSetting.Builder()
        .name("stay-time-radius-2")
        .description("Seconds a player must stay in radius 2 before triggering.")
        .defaultValue(5)
        .min(0.1)
        .max(60)
        .build());

    private final Setting<Double> cooldown = sgGeneral.add(new DoubleSetting.Builder()
        .name("cooldown-seconds")
        .description("Cooldown per player per radius.")
        .defaultValue(30)
        .min(1)
        .max(600)
        .build());

    private final Setting<Boolean> stopMacro = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-macro")
        .description("Stop MiningMacro when trigger happens.")
        .defaultValue(true)
        .build());

    private final Setting<String> cmd1 = sgGeneral.add(new StringSetting.Builder()
        .name("command-radius-1")
        .defaultValue("")
        .build());

    private final Setting<String> cmd2 = sgGeneral.add(new StringSetting.Builder()
        .name("command-radius-2")
        .defaultValue("")
        .build());

    private final Setting<List<String>> keybinds1 = sgGeneral.add(new StringListSetting.Builder()
        .name("keybinds-radius-1")
        .description("Key codes OR key names for R1 trigger.")
        .defaultValue(new ArrayList<>())
        .build());

    private final Setting<List<String>> keybinds2 = sgGeneral.add(new StringListSetting.Builder()
        .name("keybinds-radius-2")
        .description("Key codes OR key names for R2 trigger.")
        .defaultValue(new ArrayList<>())
        .build());

    // ---------- TRACKING ----------
    private static class PlayerTrack {
        long enteredR1 = 0;
        long enteredR2 = 0;
        long lastTriggerR1 = 0;
        long lastTriggerR2 = 0;
        boolean insideR1 = false;
        boolean insideR2 = false;
    }

    private final Map<String, PlayerTrack> tracker = new HashMap<>();


    public PlayerDetection() {
        super(AddonTemplate.HYPIXEL,
            "PlayerDetection",
            "Advanced 2-Radius detection with timers, cooldown and macro control.");
    }


    // ---------- KEY PARSING ----------
    private int parseKeycode(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException ignore) {}

        try {
            return InputUtil.fromTranslationKey("key.keyboard." + input.toLowerCase()).getCode();
        } catch (Exception e) {
            return -1;
        }
    }

    private void pressKey(int keyCode) {
        if (keyCode <= 0) return;
        long window = mc.getWindow().getHandle();
        mc.keyboard.onKey(window, keyCode, 0, 1, 0);
        mc.keyboard.onKey(window, keyCode, 0, 0, 0);
    }


    // ---------- MAIN LOGIC ----------
    @EventHandler
    private void onTick(TickEvent.Post e) {
        if (mc.player == null || mc.world == null) return;
        ClientPlayerEntity me = mc.player;

        long now = System.currentTimeMillis();

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == me) continue;

            String name = p.getName().getString();
            double dist = p.distanceTo(me);

            tracker.putIfAbsent(name, new PlayerTrack());
            PlayerTrack t = tracker.get(name);

            // ---------- RADIUS 1 ----------
            boolean inR1 = dist <= radius1.get();

            if (inR1) {
                if (!t.insideR1) {
                    t.enteredR1 = now;
                    t.insideR1 = true;
                }

                boolean enough = now - t.enteredR1 >= time1.get() * 1000;
                boolean ready = now - t.lastTriggerR1 >= cooldown.get() * 1000;

                if (enough && ready) {
                    t.lastTriggerR1 = now;

                    if (!cmd1.get().isEmpty())
                        mc.player.networkHandler.sendChatCommand(cmd1.get());

                    for (String key : keybinds1.get())
                        pressKey(parseKeycode(key));

                    if (stopMacro.get())
                        Modules.get().get(MiningMacro.class).toggle();

                    info("Radius 1 triggered by " + name + " (" + Math.round(dist) + "m)");
                }
            } else t.insideR1 = false;


            // ---------- RADIUS 2 ----------
            boolean inR2 = dist <= radius2.get();

            if (inR2) {
                if (!t.insideR2) {
                    t.enteredR2 = now;
                    t.insideR2 = true;
                }

                boolean enough = now - t.enteredR2 >= time2.get() * 1000;
                boolean ready = now - t.lastTriggerR2 >= cooldown.get() * 1000;

                if (enough && ready) {
                    t.lastTriggerR2 = now;

                    if (!cmd2.get().isEmpty())
                        mc.player.networkHandler.sendChatCommand(cmd2.get());

                    for (String key : keybinds2.get())
                        pressKey(parseKeycode(key));

                    if (stopMacro.get())
                        Modules.get().get(MiningMacro.class).toggle();

                    info("Radius 2 triggered by " + name + " (" + Math.round(dist) + "m)");
                }
            } else t.insideR2 = false;
        }
    }
}
