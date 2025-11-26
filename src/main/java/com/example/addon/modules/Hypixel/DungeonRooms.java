package com.example.addon.modules.Hypixel;

import com.example.addon.modules.Hypixel.DungeonRoomsHelper.Class.DungeonRoomsData;
import com.example.addon.modules.Hypixel.DungeonRoomsHelper.Class.RoomDetectionUtils;
import com.example.addon.modules.Hypixel.DungeonRoomsHelper.Class.Waypoints;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;

import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Replaces DungeonRooms.java (Forge 1.8.9)
 * Main Meteor module that controls Waypoints + RoomDetection + JSON loading.
 */
public class DungeonRooms extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> toggleWaypoints = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-waypoints")
        .description("Enable dungeon secret waypoints.")
        .defaultValue(true)
        .onChanged(val -> Waypoints.enabled = val)
        .build()
    );

    private final Setting<Boolean> togglePractice = sgGeneral.add(new BoolSetting.Builder()
        .name("practice-mode")
        .description("Only show waypoints when practice mode is active.")
        .defaultValue(false)
        .onChanged(val -> Waypoints.practiceModeOn = val)
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();

    public DungeonRooms() {
        super(
            com.example.addon.AddonTemplate.HYPIXEL,
            "DungeonRooms",
            "Secret waypoint helper for Hypixel Catacombs (Meteor/Fabric port)."
        );
    }

    @Override
    public void onActivate() {
        // 1) Load JSON (rooms + waypoints)
        loadJsonData();

        // 2) Register Listener classes
        meteordevelopment.meteorclient.MeteorClient.EVENT_BUS.subscribe(RoomDetectionUtils.INSTANCE);
        meteordevelopment.meteorclient.MeteorClient.EVENT_BUS.subscribe(Waypoints.INSTANCE);

        info("Dungeon Rooms Helper enabled.");
    }

    @Override
    public void onDeactivate() {
        meteordevelopment.meteorclient.MeteorClient.EVENT_BUS.unsubscribe(RoomDetectionUtils.INSTANCE);
        meteordevelopment.meteorclient.MeteorClient.EVENT_BUS.unsubscribe(Waypoints.INSTANCE);

        info("Dungeon Rooms Helper disabled.");
    }

    // ------------------------
    // JSON LOADING
    // ------------------------

    private void loadJsonData() {
        try {
            Gson gson = new Gson();

            InputStream roomsStream = getClass()
                .getResourceAsStream("dungeonrooms.json");
            InputStream waypointsStream = getClass()
                .getResourceAsStream("secretlocations.json");

            if (roomsStream == null) {
                error("rooms JSON missing!");
                return;
            }
            if (waypointsStream == null) {
                error("waypoints JSON missing!");
                return;
            }

            DungeonRoomsData.roomsJson = gson.fromJson(new InputStreamReader(roomsStream), JsonObject.class);
            DungeonRoomsData.waypointsJson = gson.fromJson(new InputStreamReader(waypointsStream), JsonObject.class);

            info("Loaded DungeonRooms JSON data.");
        }
        catch (Exception e) {
            error("Failed loading JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ------------------------
    // CONNECTION EVENTS
    // ------------------------

    @EventHandler
    private void onJoin(GameJoinedEvent event) {
        Waypoints.allSecretsMap.clear();
        Waypoints.allFound = false;
        DungeonRoomsData.resetPlayerState();

        info("Joined world – reset dungeon state.");
    }

    @EventHandler
    private void onLeave(GameLeftEvent event) {
        Waypoints.allSecretsMap.clear();
        Waypoints.allFound = false;
        DungeonRoomsData.resetPlayerState();

        info("Left world – cleared dungeon state.");
    }
}
