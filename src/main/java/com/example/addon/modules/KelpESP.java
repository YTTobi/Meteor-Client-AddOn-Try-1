package com.example.addon.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import com.example.addon.AddonTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashSet;
import java.util.Set;

public class KelpESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("scan-range")
        .description("How far around you to scan for fully grown kelp.")
        .defaultValue(10)
        .min(5)
        .sliderMax(50)
        .build()
    );

    private final Setting<Integer> areaSize = sgGeneral.add(new IntSetting.Builder()
        .name("area-size")
        .description("Size of the area to check for fully grown kelp.")
        .defaultValue(10)
        .min(5)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> renderEsp = sgGeneral.add(new BoolSetting.Builder()
        .name("render-esp")
        .description("Render a box around fully grown kelp columns.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> darkMode = sgGeneral.add(new BoolSetting.Builder()
        .name("Dark Mode")
        .description("Turns on dark mode")
        .defaultValue(false)
        .build()
    );


    private long lastScan = 0;
    private final Set<BlockPos> notifiedAreas = new HashSet<>();
    private final Set<BlockPos> kelpToRender = new HashSet<>();

    public KelpESP() {
        super(AddonTemplate.CATEGORY, "kelp-esp", "Highlights fully grown kelp areas and notifies you. Not very Optimised, very laggy and buggy.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null || !mc.world.isChunkLoaded(mc.player.getBlockPos())) return;

        long now = System.currentTimeMillis();
        if (now - lastScan < 1000) return; // scan once per second
        lastScan = now;

        // Only track positions for rendering if enabled
        kelpToRender.clear();

        int scan = range.get();
        int area = areaSize.get();
        BlockPos playerPos = mc.player.getBlockPos();

        // Scan blocks in radius
        Set<BlockPos> fullyGrownKelp = new HashSet<>();
        for (int x = -scan; x <= scan; x++) {
            for (int y = -scan; y <= scan; y++) {
                for (int z = -scan; z <= scan; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    var state = mc.world.getBlockState(pos);

                    if (state.getBlock() == Blocks.KELP) {
                        var above = mc.world.getBlockState(pos.up());
                        if (!above.isOf(Blocks.KELP)) {
                            fullyGrownKelp.add(pos);
                            if (renderEsp.get()) kelpToRender.add(pos); // only track if rendering is enabled
                        }
                    }
                }
            }
        }

        // Group kelp into areas
        Set<BlockPos> newNotified = new HashSet<>();
        for (BlockPos kelpPos : fullyGrownKelp) {
            int centerX = (kelpPos.getX() / area) * area + area / 2;
            int centerY = (kelpPos.getY() / area) * area + area / 2;
            int centerZ = (kelpPos.getZ() / area) * area + area / 2;
            BlockPos areaCenter = new BlockPos(centerX, centerY, centerZ);

            if (!notifiedAreas.contains(areaCenter)) {
                boolean allFullyGrown = true;

                outer:
                for (int dx = -area / 2; dx <= area / 2; dx++) {
                    for (int dy = -area / 2; dy <= area / 2; dy++) {
                        for (int dz = -area / 2; dz <= area / 2; dz++) {
                            BlockPos checkPos = areaCenter.add(dx, dy, dz);
                            var state = mc.world.getBlockState(checkPos);
                            var above = mc.world.getBlockState(checkPos.up());
                            if (state.getBlock() == Blocks.KELP && !above.isOf(Blocks.KELP)) {
                                // ok
                            } else if (state.getBlock() == Blocks.KELP) {
                                allFullyGrown = false;
                                break outer;
                            }
                        }
                    }
                }

                if (allFullyGrown) {
                    mc.player.sendMessage(
                        Text.literal("[")
                            .append(Text.literal("Meteor").styled(s -> s.withColor(Formatting.DARK_PURPLE)))
                            .append(Text.literal("] ["))
                            .append(Text.literal("KelpESP").styled(s -> s.withColor(Formatting.GREEN)))
                            .append(Text.literal("] "))
                            .append(Text.literal("Area: " + areaCenter.toShortString()).styled(s -> s.withColor(Formatting.GRAY))),
                        false
                    );
                    newNotified.add(areaCenter);
                }
            }
        }

        notifiedAreas.addAll(newNotified);

        // Render boxes if enabled
        if (renderEsp.get()) {
            for (BlockPos pos : kelpToRender) {
                event.renderer.box(
                    pos,
                    new Color(0, 255, 0, 100),
                    new Color(0, 255, 0, 150),
                    ShapeMode.Both,
                    0
                );
            }
        }
    }
}
