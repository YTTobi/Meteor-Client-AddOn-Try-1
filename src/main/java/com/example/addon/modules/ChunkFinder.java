package com.example.addon.modules;

import com.sun.jdi.Bootstrap;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import com.example.addon.AddonTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ChunkFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final SettingGroup sgBlocks = this.settings.createGroup("Blocks");

    private final Setting<Integer> BelowYLevel = sgGeneral.add(new IntSetting.Builder()
        .name("Y-Level")
        .description("On which or below Y-Level, it searches suspicious Blocks")
        .defaultValue(20)
        .min(0)
        .sliderMax(120)
        .build()
    );

    private final Setting<List<Block>> scanBlocks = sgBlocks.add(new BlockListSetting.Builder()
        .name("Scan Blocks")
        .description("Which blocks to scan for.")
        .defaultValue(
            Blocks.COBBLED_DEEPSLATE,
            Blocks.DEEPSLATE,
            Blocks.POLISHED_DEEPSLATE,
            Blocks.DEEPSLATE_BRICKS,
            Blocks.COBBLESTONE
        )
        .build()
    );

    private final Setting<Boolean> BlockOutlines = sgBlocks.add(new BoolSetting.Builder()
        .name("Block Outline")
        .description("Draws a box around blocks")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> BlockRotation = sgBlocks.add(new BoolSetting.Builder()
        .name("Check Rotation")
        .description("Checks if selected Blocks are Rotated.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> Tracers = sgRender.add(new BoolSetting.Builder()
        .name("Tracers")
        .description("Render a line to the suspicious Blocks, on your selected list.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> AdvancedMode = sgGeneral.add(new BoolSetting.Builder()
        .name("Advanced ChunkFinder")
        .description("A better version of ChunkFinder but more performance heavy.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> StoreFinds = sgGeneral.add(new BoolSetting.Builder()
        .name("Find Storage")
        .description("Stores the Coordinates of flagged chunks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> areaSize = sgGeneral.add(new IntSetting.Builder()
        .name("Area-size")
        .description("Size of the area to check for suspicious Blocks, in Chunks.")
        .defaultValue(8)
        .min(2)
        .sliderMax(32)
        .build()
    );

    private final Setting<SettingColor> TracerColor = sgRender.add(new ColorSetting.Builder()
        .name("Color")
        .description("The color of the Tracer, to flagged chunks.")
        .defaultValue(Color.MAGENTA)
        .build()
    );

    private final Setting<SettingColor> OutlineColor = sgBlocks.add(new ColorSetting.Builder()
        .name("Color")
        .description("The color of the Outline box.")
        .defaultValue(Color.RED)
        .build()
    );

    public ChunkFinder() {
        super(AddonTemplate.CATEGORY, "Chunk-finder", "Highlights suspicious chunks.");
    }

    private final Set<BlockPos> suspiciousBlocks = new CopyOnWriteArraySet<>();
    private int scanIndex = 0;

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        BlockPos playerPos = mc.player.getBlockPos();
        int radius = areaSize.get() * 16;
        int bottomY = mc.world.getBottomY();
        int topY = Math.min(BelowYLevel.get(), mc.world.getBottomY() + mc.world.getHeight());

        int checksPerTick = 500;
        int totalChecks = (radius * 2 + 1) * (radius * 2 + 1) * (topY - bottomY + 1);

        for (int i = 0; i < checksPerTick; i++) {
            if (scanIndex >= totalChecks) {
                scanIndex = 0;
                break;
            }

            int x = (scanIndex / ((topY - bottomY + 1) * (radius * 2 + 1))) - radius;
            int z = ((scanIndex / (topY - bottomY + 1)) % (radius * 2 + 1)) - radius;
            int y = bottomY + (scanIndex % (topY - bottomY + 1));
            scanIndex++;

            BlockPos pos = playerPos.add(x, y - playerPos.getY(), z);
            Block block = mc.world.getBlockState(pos).getBlock();
            if (scanBlocks.get().contains(block)) suspiciousBlocks.add(pos);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        BlockPos playerPos = mc.player.getBlockPos();
        int px = playerPos.getX();
        int pz = playerPos.getZ();
        int bottomY = mc.world.getBottomY();
        int topY = Math.min(BelowYLevel.get(), mc.world.getBottomY() + mc.world.getHeight());
        int blockRadius = areaSize.get() * 16;
        Set<BlockPos> scannedPositions = new HashSet<>();

        for (int x = -blockRadius; x <= blockRadius; x++) {
            for (int z = -blockRadius; z <= blockRadius; z++) {
                for (int y = bottomY; y <= topY; y++) {
                    BlockPos pos = new BlockPos(px + x, y, pz + z);
                    if (!scannedPositions.add(pos)) continue;

                    net.minecraft.block.BlockState state = mc.world.getBlockState(pos);
                    Block block = state.getBlock();
                    if (!scanBlocks.get().contains(block)) continue;

                    if (BlockRotation.get()) {
                        boolean hasRotation =
                            state.contains(net.minecraft.state.property.Properties.HORIZONTAL_FACING) ||
                                state.contains(net.minecraft.state.property.Properties.FACING) ||
                                state.contains(net.minecraft.state.property.Properties.ROTATION) ||
                                state.contains(net.minecraft.state.property.Properties.AXIS);
                        if (!hasRotation) continue;
                    }

                    if (BlockOutlines.get()) {
                        event.renderer.box(pos, OutlineColor.get(), OutlineColor.get(), ShapeMode.Both, 0);
                    }

                    if (Tracers.get()) {
                        Vec3d eye = mc.player.getEyePos();
                        double sx = eye.x;
                        double sy = eye.y;
                        double sz = eye.z;
                        double ex = pos.getX() + 0.5;
                        double ey = pos.getY() + 0.5;
                        double ez = pos.getZ() + 0.5;

                        SettingColor sc = TracerColor.get();
                        Color tracer = new Color(sc.r, sc.g, sc.b, sc.a);

                        event.renderer.line(sx, sy, sz, ex, ey, ez, tracer);
                    }

                    if (AdvancedMode.get()) {
                        mc.player.sendMessage(
                            Text.literal(Formatting.DARK_PURPLE + "[ChunkFinder] " + Formatting.GRAY +
                                "Suspicious block found: " + Formatting.RED + block.getName().getString() +
                                Formatting.GRAY + " at " + pos.toShortString()),
                            true
                        );
                    }
                }
            }
        }
    }
}
