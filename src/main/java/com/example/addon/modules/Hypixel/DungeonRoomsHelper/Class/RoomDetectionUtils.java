package com.example.addon.modules.Hypixel.DungeonRoomsHelper.Class;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class RoomDetectionUtils {
    public static final double DEG_TO_RAD = Math.PI / 180.0;
    public static final RoomDetection INSTANCE = new RoomDetection();


    /**
     * 1.21.x equivalent of old getVectorFromRotation()
     * Uses Vec3d instead of old Vec3.
     */
    public static Vec3d getVectorFromRotation(float yaw, float pitch) {
        float yawRad = (-yaw) * (float) DEG_TO_RAD - (float) Math.PI;
        float pitchRad = (-pitch) * (float) DEG_TO_RAD;

        float f = (float) Math.cos(yawRad);
        float f1 = (float) Math.sin(yawRad);
        float f2 = (float) Math.cos(pitchRad);
        float f3 = (float) Math.sin(pitchRad);

        return new Vec3d(f1 * f2, f3, f * f2);
    }

    /**
     * Simplified FOV ray generation.
     * We do NOT attempt to match the original mod exactly.
     * This version is stable & works in Meteor 1.21.x.
     */
    public static List<Vec3d> vectorsToRaytrace(int quality) {
        MinecraftClient mc = MinecraftClient.getInstance();

        List<Vec3d> vectors = new ArrayList<>();
        if (mc.player == null) return vectors;

        Vec3d eyePos = mc.player.getEyePos();

        float baseFov = mc.options.getFov().getValue().floatValue();
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();

        float verticalStep = baseFov / quality;
        float horizontalStep = (baseFov * 1.4f) / quality;  // approximated horizontal FOV

        for (int h = -(quality / 2); h <= quality / 2; h++) {
            for (int v = -(quality / 2); v <= quality / 2; v++) {
                float dyaw = yaw + h * horizontalStep;
                float dpitch = pitch + v * verticalStep;

                Vec3d dir = getVectorFromRotation(dyaw, dpitch).normalize();
                vectors.add(eyePos.add(dir.multiply(64))); // max 64 blocks
            }
        }

        return vectors;
    }

    /**
     * Whitelisted block IDs from original mod.
     * Only relevant if you ever want to port skeleton scans.
     */
    public static final HashSet<Integer> whitelistedBlocks = new HashSet<>(List.of(
        100, 103, 104, 105, 106,
        200, 300, 301, 400, 700,
        1800, 3507, 4300, 4800, 8200,
        9800, 9801, 9803,
        15907, 15909, 15915
    ));

    /**
     * Doorway detection from original mod
     * (kept 1:1 because it still works mathematically).
     */
    public static boolean blockPartOfDoorway(BlockPos block) {
        int y = block.getY();
        if (y < 66 || y > 73) return false;

        int relX = Math.floorMod(block.getX() - 8, 32);
        int relZ = Math.floorMod(block.getZ() - 8, 32);

        if (relX >= 13 && relX <= 17) {
            if (relZ <= 2 || relZ >= 28) return true;
        }
        if (relZ >= 13 && relZ <= 17) {
            if (relX <= 2 || relX >= 28) return true;
        }

        return false;
    }
}
