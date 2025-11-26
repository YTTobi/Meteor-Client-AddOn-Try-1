package com.example.addon.modules.Hypixel.DungeonRoomsHelper.Class;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Simplified Meteor-compatible room detection.
 */
public class RoomDetection {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static String roomName = "undefined";
    public static String roomDirection = "NORTH";
    public static String roomCategory = "normal";
    public static BlockPos roomCorner = null;

    // placeholder values
    public static String roomColor = "unknown";
    public static List<Vec3d> currentRoomSegments = new ArrayList<>();

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        BlockPos pos = mc.player.getBlockPos();

        // Floor grid system (Hypixel Dungeons are based on 32x32 room tiles)
        BlockPos corner = new BlockPos(
            (pos.getX() / 32) * 32,
            pos.getY(),
            (pos.getZ() / 32) * 32
        );

        roomCorner = corner;

        // Determine simple direction
        double dx = pos.getX() - (corner.getX() + 16);
        double dz = pos.getZ() - (corner.getZ() + 16);

        if (Math.abs(dx) > Math.abs(dz)) {
            roomDirection = dx > 0 ? "EAST" : "WEST";
        } else {
            roomDirection = dz > 0 ? "SOUTH" : "NORTH";
        }

        // Assign first available room from JSON
        if (!DungeonRoomsData.roomsJson.isEmpty()) {
            roomName = DungeonRoomsData.roomsJson.keySet().iterator().next();
        }
    }

    public static void resetCurrentRoom() {
        roomName = "undefined";
        roomDirection = "NORTH";
        roomCategory = "normal";
        roomCorner = null;

        roomColor = "unknown";
        currentRoomSegments.clear();
    }
}
