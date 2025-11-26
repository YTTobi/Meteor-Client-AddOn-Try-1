package com.example.addon.modules.Hypixel.DungeonRoomsHelper.Class;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Simplified Map/Room utility for Meteor 1.21.x.
 *
 * - KEIN echtes Hypixel-Map-Parsing
 * - Nur Grid-Logik (32x32 Rooms) + Rotation
 * - Wird von DungeonRooms-Waypoints benutzt, um
 *   relative Secret-Koordinaten -> Weltkoordinaten zu mappen.
 */
public class MapUtils {
    // Annahme: Dungeon-Rooms sind 32x32 Blöcke groß
    // und die relative Secret-Koordinate (x,z) ist in diesem 32x32-Raum.
    public static final int ROOM_SIZE = 32;

    /**
     * Grobe Bestimmung der NW-Raumecke basierend auf Spielerposition.
     * Kann benutzt werden, falls du irgendwo eine Ecke brauchst.
     */
    public static BlockPos getClosestRoomCorner(Vec3d worldPos) {
        int baseX = (int) Math.floor(worldPos.x) / ROOM_SIZE * ROOM_SIZE;
        int baseZ = (int) Math.floor(worldPos.z) / ROOM_SIZE * ROOM_SIZE;
        int y = (int) Math.floor(worldPos.y);

        return new BlockPos(baseX, y, baseZ);
    }

    /**
     * Wandelt eine Weltposition in eine relative Position (0..ROOM_SIZE) um
     * basierend auf Richtung + Raumecke.
     *
     * direction:
     *  - "NORTH": Standard, +X = Osten, +Z = Süden (keine Rotation)
     *  - "SOUTH": 180° gedreht
     *  - "EAST":  +90°
     *  - "WEST":  -90°
     */
    public static BlockPos actualToRelative(BlockPos actual, String direction, BlockPos roomCorner) {
        int dx = actual.getX() - roomCorner.getX();
        int dz = actual.getZ() - roomCorner.getZ();
        int y  = actual.getY() - roomCorner.getY();

        int rx, rz;

        switch (direction.toUpperCase()) {
            case "SOUTH":
                // 180° Drehung um die Mitte des Raums
                rx = ROOM_SIZE - 1 - dx;
                rz = ROOM_SIZE - 1 - dz;
                break;

            case "EAST":
                // +90° Drehung (x,z) -> (z, ROOM_SIZE-1-x)
                rx = dz;
                rz = ROOM_SIZE - 1 - dx;
                break;

            case "WEST":
                // -90° Drehung (x,z) -> (ROOM_SIZE-1-z, x)
                rx = ROOM_SIZE - 1 - dz;
                rz = dx;
                break;

            case "NORTH":
            default:
                // Keine Rotation
                rx = dx;
                rz = dz;
                break;
        }

        return new BlockPos(rx, y, rz);
    }

    /**
     * Wandelt eine relative Secret-Koordinate (aus JSON) in eine Weltposition um.
     *
     * relative: Koordinate im Raum (z.B. x/z 0..31)
     * direction: "NORTH", "SOUTH", "EAST", "WEST"
     * roomCorner: Weltkoordinate der NW-Raumecke (Block oben links)
     */
    public static BlockPos relativeToActual(BlockPos relative, String direction, BlockPos roomCorner) {
        int rx = relative.getX();
        int rz = relative.getZ();
        int ry = relative.getY();

        int wx, wz;

        switch (direction.toUpperCase()) {
            case "SOUTH":
                // 180° Drehung um die Mitte des Raums
                wx = roomCorner.getX() + (ROOM_SIZE - 1 - rx);
                wz = roomCorner.getZ() + (ROOM_SIZE - 1 - rz);
                break;

            case "EAST":
                // +90°: (rx,rz) -> (rz, ROOM_SIZE-1-rx)
                wx = roomCorner.getX() + rz;
                wz = roomCorner.getZ() + (ROOM_SIZE - 1 - rx);
                break;

            case "WEST":
                // -90°: (rx,rz) -> (ROOM_SIZE-1-rz, rx)
                wx = roomCorner.getX() + (ROOM_SIZE - 1 - rz);
                wz = roomCorner.getZ() + rx;
                break;

            case "NORTH":
            default:
                // Keine Rotation
                wx = roomCorner.getX() + rx;
                wz = roomCorner.getZ() + rz;
                break;
        }

        int wy = roomCorner.getY() + ry;
        return new BlockPos(wx, wy, wz);
    }
}
