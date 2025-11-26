package com.example.addon.modules.Hypixel;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import meteordevelopment.meteorclient.utils.misc.Keybind;

public class CoordinatesToChat extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Keybind
    private final Setting<Keybind> keybind = sgGeneral.add(new KeybindSetting.Builder()
        .name("send-keybind")
        .description("Keybind to send your current coordinates in chat.")
        .defaultValue(Keybind.none())
        .build()
    );

    // User customizable prefix
    private final Setting<String> prefix = sgGeneral.add(new StringSetting.Builder()
        .name("message-prefix")
        .description("Text before the coordinates.")
        .defaultValue("My coordinates are:")
        .build()
    );

    // User customizable format for coordinates
    private final Setting<String> format = sgGeneral.add(new StringSetting.Builder()
        .name("coordinate-format")
        .description("Format of the coordinates message. Use %d for X, Y, Z.")
        .defaultValue("X=%d, Y=%d, Z=%d")
        .build()
    );

    public CoordinatesToChat() {
        super(AddonTemplate.HYPIXEL, "CoordinatesToChat", "Sends your current player position in chat.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (keybind.get().isPressed()) sendCoords();
    }

    private void sendCoords() {
        BlockPos pos = mc.player.getBlockPos();

        String coords;
        try {
            coords = String.format(
                format.get(),
                pos.getX(),
                pos.getY(),
                pos.getZ()
            );
        } catch (Exception e) {
            coords = "INVALID FORMAT STRING (use %d %d %d)";
        }

        String fullMessage = prefix.get() + " " + coords;
        mc.player.networkHandler.sendChatMessage(fullMessage);
    }
}
