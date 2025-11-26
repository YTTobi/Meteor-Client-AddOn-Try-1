package com.example.addon.modules.Hypixel.DungeonRoomsHelper.Class;



import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import org.joml.Matrix4f;


import java.util.*;

/**
 * Port von Waypoints.java aus DungeonRooms (Forge 1.8.9) nach Meteor / Fabric 1.21.x.
 *
 * Abhängig von:
 * - DungeonRoomsData (roomsJson, waypointsJson, allSecretsMap, secretNum, etc.)
 * - RoomDetection (roomName, roomDirection, roomCorner)
 * - MapUtils.relativeToActual(...)
 * - Utils.inCatacombs / Utils.inSkyblock
 * - WaypointUtils (renderBoundingBox, renderBeacon, renderText) -> muss ebenfalls portiert werden
 *
 * Diese Klasse ist KEIN Meteor-Module, sondern ein Event-Listener.
 * Du musst sie z.B. mit MeteorClient.EVENT_BUS.subscribe(new Waypoints()) registrieren.
 */
public class Waypoints {
    // Settings / Flags (kannst du später in dein Module + Meteor Settings umziehen)
    public static boolean enabled = true;
    public static final Waypoints INSTANCE = new Waypoints();

    public static boolean showEntrance = true;
    public static boolean showSuperboom = true;
    public static boolean showSecrets = true;
    public static boolean showFairySouls = true;
    public static boolean showStonk = true;
    public static boolean sneakToDisable = true;

    public static boolean disableWhenAllFound = true;
    public static boolean allFound = false;

    public static boolean showWaypointText = true;
    public static boolean showBoundingBox = true;
    public static boolean showBeacon = true;

    public static boolean practiceModeOn = false;

    // Secret-Tracking
    public static int secretNum = 0;            // Gesamtzahl der Secrets im Raum (musst du bei RoomDetection setzen)
    public static int completedSecrets = 0;     // Completed Secrets laut Action Bar

    // roomName -> Liste von Boolean pro Secret (false = ausgeschaltet / gefunden)
    public static Map<String, List<Boolean>> allSecretsMap = new HashMap<>();
    public static List<Boolean> secretsList = new ArrayList<>(Arrays.asList(new Boolean[10]));

    private static long lastSneakTime = 0;
    private boolean prevSneakPressed = false;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private Frustum frustum;

    // ---------- RENDER 3D (WAYPOINTS) ----------

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!enabled) return;
        if (practiceModeOn && !isPracticeKeyDown()) return;

        String roomName = RoomDetection.roomName; // muss portiert werden
        if (roomName == null || roomName.equals("undefined")) return;
        if (DungeonRoomsData.roomsJson == null || DungeonRoomsData.roomsJson.get(roomName) == null) return;
        if (secretsList == null) return;
        if (DungeonRoomsData.waypointsJson == null || DungeonRoomsData.waypointsJson.get(roomName) == null) return;

        var secretsArray = DungeonRoomsData.waypointsJson.get(roomName).getAsJsonArray();
        int arraySize = secretsArray.size();

        Entity viewer = mc.getCameraEntity();
        if (viewer == null) return;

        Camera cam = mc.gameRenderer.getCamera();
        final Frustum frustum = new Frustum(new Matrix4f(), new Matrix4f());
        frustum.setPosition(cam.getPos().x, cam.getPos().y, cam.getPos().z);

        for (int i = 0; i < arraySize; i++) {
            var secretsObject = secretsArray.get(i).getAsJsonObject();

            // pro-Secret Ein-/Ausschalten (über secretsList)
            boolean display = true;
            for (int j = 1; j <= secretNum; j++) {
                if (!secretsList.get(j - 1)) {
                    String num = secretsObject.get("secretName").getAsString().substring(0, 2).replaceAll("\\D", "");
                    if (num.equals(String.valueOf(j))) {
                        display = false;
                        break;
                    }
                }
            }
            if (!display) continue;

            // Wenn alle Secrets gefunden und Option an: nur Fairy Souls weiter anzeigen
            if (disableWhenAllFound && allFound && !secretsObject.get("category").getAsString().equals("fairysoul"))
                continue;

            BlockPos relative = new BlockPos(
                secretsObject.get("x").getAsInt(),
                secretsObject.get("y").getAsInt(),
                secretsObject.get("z").getAsInt()
            );
            BlockPos pos = MapUtils.relativeToActual(
                relative,
                RoomDetection.roomDirection,
                RoomDetection.roomCorner
            );

            // Frustum Culling
            Box fullBox = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, 255, pos.getZ() + 1);
            if (!frustum.isVisible(fullBox)) continue;

            // Kategorie -> Farbe
            Color color;
            String category = secretsObject.get("category").getAsString();
            switch (category) {
                case "entrance" -> {
                    if (!showEntrance) continue;
                    color = new Color(0, 255, 0);
                }
                case "superboom" -> {
                    if (!showSuperboom) continue;
                    color = new Color(255, 0, 0);
                }
                case "chest" -> {
                    if (!showSecrets) continue;
                    color = new Color(2, 213, 250);
                }
                case "item" -> {
                    if (!showSecrets) continue;
                    color = new Color(2, 64, 250);
                }
                case "bat" -> {
                    if (!showSecrets) continue;
                    color = new Color(142, 66, 0);
                }
                case "wither" -> {
                    if (!showSecrets) continue;
                    color = new Color(30, 30, 30);
                }
                case "lever" -> {
                    if (!showSecrets) continue;
                    color = new Color(250, 217, 2);
                }
                case "fairysoul" -> {
                    if (!showFairySouls) continue;
                    color = new Color(255, 85, 255);
                }
                case "stonk" -> {
                    if (!showStonk) continue;
                    color = new Color(146, 52, 235);
                }
                default -> color = new Color(190, 255, 252);
            }

            // Distanz für Beacon-Entscheidung
            double distSq = viewer.squaredDistanceTo(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
            );

            // Bounding Box & Beacon & Text über WaypointUtils rendern
            if (showBoundingBox) {
                Box box = new Box(
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1
                );
                WaypointUtils.drawFilledBoundingBox(box, color, 0.4f, event);
            }

            if (showBeacon && distSq > 5 * 5) {
                WaypointUtils.renderBeaconBeam(event, pos, color, 0.25f);
            }

            if (showWaypointText) {
                String name = secretsObject.get("secretName").getAsString();
                WaypointUtils.renderWaypointText(event, pos.up(2), name, color);
            }
        }
    }

    // Dummy – ersetze mit deinem tatsächlichen Practice-Hotkey-System (z.B. Meteor KeybindSetting)
    private boolean isPracticeKeyDown() {
        // z.B. später: return DungeonRoomsModule.practiceKey.get().isPressed();
        return true;
    }

    // ---------- CHAT: Action-Bar für "Secrets x/y" ----------

    @EventHandler
    private void onPacketReceiveActionBar(PacketEvent.Receive event) {
        if (!Utils.inCatacombs || !enabled) return;

        if (!(event.packet instanceof GameMessageS2CPacket packet)) return;
        if (!packet.overlay()) return;   // overlay == true → Action Bar

        String raw = packet.content().getString();

        String[] sections = raw.split(" {3,}");
        for (String section : sections) {
            if (section.contains("Secrets") && section.contains("/")) {
                try {
                    String[] split = section.split("/");
                    completedSecrets = Integer.parseInt(split[0].replaceAll("\\D", ""));
                    int total = Integer.parseInt(split[1].replaceAll("\\D", ""));
                    allFound = (total == secretNum && completedSecrets == secretNum);
                } catch (Exception ignored) {}
                break;
            }
        }
    }


    // ---------- PACKETS: Item-Pickup Secret Auto-Disable ----------

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!Utils.inCatacombs || !enabled) return;
        if (disableWhenAllFound && allFound) return;
        if (mc.world == null || mc.player == null) return;

        if (!(event.packet instanceof ItemPickupAnimationS2CPacket packet)) return;

        int itemId = packet.getEntityId();
        int collectorId = packet.getCollectorEntityId();

        Entity entityItem = mc.world.getEntityById(itemId);
        Entity collector = mc.world.getEntityById(collectorId);

        if (!(entityItem instanceof ItemEntity itemEntity)) return;
        if (collector == null) return;
        if (!Objects.equals(collector.getUuid(), mc.player.getUuid())) {
            // Nur reagieren, wenn WIR den Secret looten (Hypixel ToS-konform)
            return;
        }

        ItemStack stack = itemEntity.getStack();
        String name = stack.getName().getString();

        if (!(name.contains("Decoy") ||
            name.contains("Defuse Kit") ||
            name.contains("Dungeon Chest Key") ||
            name.contains("Healing VIII") ||
            name.contains("Inflatable Jerry") ||
            name.contains("Spirit Leap") ||
            name.contains("Training Weights") ||
            name.contains("Trap") ||
            name.contains("Treasure Talisman"))) {
            return;
        }

        String roomName = RoomDetection.roomName;
        if (roomName == null || roomName.equals("undefined")) return;
        if (DungeonRoomsData.roomsJson == null || DungeonRoomsData.roomsJson.get(roomName) == null) return;
        if (DungeonRoomsData.waypointsJson == null || DungeonRoomsData.waypointsJson.get(roomName) == null) return;
        if (secretsList == null) return;

        var secretsArray = DungeonRoomsData.waypointsJson.get(roomName).getAsJsonArray();
        int arraySize = secretsArray.size();

        for (int i = 0; i < arraySize; i++) {
            var secretsObject = secretsArray.get(i).getAsJsonObject();
            String category = secretsObject.get("category").getAsString();
            if (!category.equals("item") && !category.equals("bat")) continue;

            BlockPos relative = new BlockPos(
                secretsObject.get("x").getAsInt(),
                secretsObject.get("y").getAsInt(),
                secretsObject.get("z").getAsInt()
            );
            BlockPos pos = MapUtils.relativeToActual(relative, RoomDetection.roomDirection, RoomDetection.roomCorner);

            double distSq = collector.squaredDistanceTo(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
            );
            if (distSq > 36.0) continue;

            // Secret-Nummer rausholen
            for (int j = 1; j <= secretNum; j++) {
                String num = secretsObject.get("secretName").getAsString().substring(0, 2).replaceAll("\\D", "");
                if (!num.equals(String.valueOf(j))) continue;

                if (!secretsList.get(j - 1)) continue; // schon ausgeschaltet

                secretsList.set(j - 1, false);
                allSecretsMap.put(roomName, secretsList);
                // optional: Logger
                // DungeonRoomsData.logger.info("Picked up " + name + " from " + category + " secret, disabling #" + j);
                return;
            }
        }
    }



    // ---------- DOUBLE-SNEAK: Secret in der Nähe deaktivieren ----------

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!Utils.inCatacombs || !enabled || !sneakToDisable) return;
        if (mc.player == null) return;

        ClientPlayerEntity player = mc.player;

        boolean sneakPressed = mc.options.sneakKey.isPressed();

        // Erster Sneak-Tap
        if (sneakPressed && !prevSneakPressed) {
            long now = System.currentTimeMillis();
            if (now - lastSneakTime < 500) {
                // Double-Tap erkannt
                handleSneakNearSecret(player);
            }
            lastSneakTime = now;
        }

        prevSneakPressed = sneakPressed;
    }

    private void handleSneakNearSecret(ClientPlayerEntity player) {
        if (disableWhenAllFound && allFound) return;

        String roomName = RoomDetection.roomName;
        if (roomName == null || roomName.equals("undefined")) return;
        if (DungeonRoomsData.roomsJson == null || DungeonRoomsData.roomsJson.get(roomName) == null) return;
        if (DungeonRoomsData.waypointsJson == null || DungeonRoomsData.waypointsJson.get(roomName) == null) return;
        if (secretsList == null) return;

        var secretsArray = DungeonRoomsData.waypointsJson.get(roomName).getAsJsonArray();
        int arraySize = secretsArray.size();

        for (int i = 0; i < arraySize; i++) {
            var secretsObject = secretsArray.get(i).getAsJsonObject();
            String category = secretsObject.get("category").getAsString();
            if (!(category.equals("chest") ||
                category.equals("wither") ||
                category.equals("item") ||
                category.equals("bat"))) {
                continue;
            }

            BlockPos relative = new BlockPos(
                secretsObject.get("x").getAsInt(),
                secretsObject.get("y").getAsInt(),
                secretsObject.get("z").getAsInt()
            );
            BlockPos pos = MapUtils.relativeToActual(relative, RoomDetection.roomDirection, RoomDetection.roomCorner);

            double distSq = player.squaredDistanceTo(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
            );
            if (distSq > 16.0) continue; // <= 4 Blöcke

            for (int j = 1; j <= secretNum; j++) {
                String num = secretsObject.get("secretName").getAsString().substring(0, 2).replaceAll("\\D", "");
                if (!num.equals(String.valueOf(j))) continue;
                if (!secretsList.get(j - 1)) continue;

                secretsList.set(j - 1, false);
                allSecretsMap.put(roomName, secretsList);
                // DungeonRoomsData.logger.info("Player sneaked near " + category + " secret, disabling #" + j);
                return;
            }
        }
    }
}
