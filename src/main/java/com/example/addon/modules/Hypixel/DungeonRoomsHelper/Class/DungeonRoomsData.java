package com.example.addon.modules.Hypixel.DungeonRoomsHelper.Class;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Zentrale Datenklasse:
 *  - Lädt JSON (room data + waypoint data)
 *  - Speichert Secret-Status pro Raum
 *  - Hält globalen "Dungeon state" ähnlich wie Forge 1.8.9
 */
public class DungeonRoomsData {

    // JSON-Dateien aus resources/assets/dungeonrooms/
    public static JsonObject roomsJson = new JsonObject();
    public static JsonObject waypointsJson = new JsonObject();

    // Cache: Anzahl der Secrets pro Raum
    private static final Map<String, Integer> secretsPerRoom = new HashMap<>();

    // Secret-Toggles: roomName -> List<Boolean>
    public static Map<String, List<Boolean>> allSecretsMap = new HashMap<>();

    // Aktuelle Dungeon-Infos (globaler state)
    public static String currentRoom = "undefined";
    public static int secretNum = 0;
    public static int completedSecrets = 0;
    public static boolean allFound = false;

    private static boolean loaded = false;

    // ----------------------------------------------------------
    // JSON LOADER
    // ----------------------------------------------------------
    public static void load() {
        if (loaded) return;

        roomsJson = loadJson("dungeonrooms.json");
        waypointsJson = loadJson("secretlocations.json");

        buildSecretsCache();
        loaded = true;

        System.out.println("[DungeonRooms] JSON loaded: "
            + roomsJson.size() + " rooms, "
            + waypointsJson.size() + " waypoint-rooms");
    }

    private static JsonObject loadJson(String path) {
        try (InputStream in = DungeonRoomsData.class.getClassLoader()
            .getResourceAsStream(path)) {

            if (in == null) {
                System.err.println("[DungeonRooms] Missing resource: " + path);
                return new JsonObject();
            }

            InputStreamReader reader =
                new InputStreamReader(in, StandardCharsets.UTF_8);
            return JsonParser.parseReader(reader).getAsJsonObject();

        } catch (IOException e) {
            e.printStackTrace();
            return new JsonObject();
        }
    }

    private static void buildSecretsCache() {
        secretsPerRoom.clear();

        // 1) secrets aus dungeonrooms.json → bevorzugt
        for (String key : roomsJson.keySet()) {
            JsonObject obj = roomsJson.getAsJsonObject(key);
            if (obj != null && obj.has("secrets")) {
                secretsPerRoom.put(key, obj.get("secrets").getAsInt());
            }
        }

        // 2) Fallback: Wenn nur secretlocations.json existiert
        for (String key : waypointsJson.keySet()) {
            if (!secretsPerRoom.containsKey(key)) {
                try {
                    JsonArray arr = waypointsJson.getAsJsonArray(key);
                    secretsPerRoom.put(key, arr.size());
                } catch (Exception ignored) {}
            }
        }
    }

    public static void ensureLoaded() {
        if (!loaded) load();
    }

    public static int getSecretCount(String roomName) {
        ensureLoaded();

        Integer i = secretsPerRoom.get(roomName);
        if (i != null) return i;

        if (waypointsJson.has(roomName)) {
            try {
                return waypointsJson.getAsJsonArray(roomName).size();
            } catch (Exception ignored) {}
        }
        return 0;
    }

    // ----------------------------------------------------------
    // RUNTIME STATE RESET (wird von DungeonRooms.java verwendet)
    // ----------------------------------------------------------
    public static void resetPlayerState() {
        System.out.println("[DungeonRooms] Resetting Dungeon state");

        currentRoom = "undefined";
        secretNum = 0;
        completedSecrets = 0;
        allFound = false;

        allSecretsMap.clear();

        // RoomDetection-Daten ebenfalls zurücksetzen
        RoomDetection.roomName = "undefined";
        RoomDetection.roomDirection = "NORTH";
        RoomDetection.roomCorner = null;
    }
}
