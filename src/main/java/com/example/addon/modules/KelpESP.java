package com.example.addon.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import com.example.addon.AddonTemplate;

public class KelpESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("scan-range")
        .description("How far around you to scan for kelp (in blocks).")
        .defaultValue(30)
        .min(5)
        .sliderMax(100)
        .build()
    );

    private final Setting<Boolean> notifyChat = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-notify")
        .description("Send a chat message when fully grown kelp is found.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderEsp = sgGeneral.add(new BoolSetting.Builder()
        .name("render-esp")
        .description("Render a box around fully grown kelp.")
        .defaultValue(true)
        .build()
    );

    // Kelp block property
    private static final IntProperty AGE = IntProperty.of("age", 0, 25);

    public KelpESP() {
        super(AddonTemplate.CATEGORY, "kelp-esp", "Highlights fully grown kelp and notifies you in chat.");
    }


    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        BlockPos playerPos = mc.player.getBlockPos();
        int scan = range.get();

        for (int x = -scan; x <= scan; x++) {
            for (int y = -scan; y <= scan; y++) {
                for (int z = -scan; z <= scan; z++) {
                    BlockPos pos = playerPos.add(x, y, z);

                    if (mc.world.getBlockState(pos).getBlock() == Blocks.KELP) {
                        int age = mc.world.getBlockState(pos).get(AGE);
                        if (age == 25) {
                            if (notifyChat.get()) {
                                mc.player.sendMessage(
                                    net.minecraft.text.Text.of("§aFully grown kelp found at " + pos.toShortString()),
                                    false
                                );
                            }

                            if (renderEsp.get()) {
                                event.renderer.box(
                                    pos,
                                    new Color(0, 255, 0, 100), // sides
                                    new Color(0, 255, 0, 150), // lines
                                    ShapeMode.Both,
                                    0
                                );
                            }
                        }
                    }
                }
            }
        }
    }
}
