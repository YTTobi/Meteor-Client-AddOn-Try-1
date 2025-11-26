package com.example.addon.modules.Hypixel.DungeonRoomsHelper.Class;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Fully updated Meteor Client version of DungeonManager.
 */
public class DungeonManager {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    /**
     * 0 = Not in run
     * 1 = Dungeon started, entrance
     * 2 = Room clear phase
     * 3 = Boss fight
     * 4 = Run done
     */
    public static int gameStage = 0;

    private long bloodTime = Long.MAX_VALUE;
    private static boolean firstMessageShown = false;

    @EventHandler
    private void onChat(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString();

        // Mort gives the map -> start room clear stage
        if (msg.startsWith("§e[NPC] §bMort§f: §rHere, I found this map")) {
            gameStage = 2;
            ChatUtils.info("DungeonRooms → Stage 2 (Room Clear)");
        }

        // Watcher done
        else if (msg.contains("§r§c[BOSS] The Watcher§r§f: You have proven yourself.")) {
            bloodTime = System.currentTimeMillis() + 5000;
        }

        // A new boss message signals Stage 3
        else if (System.currentTimeMillis() > bloodTime
            && msg.startsWith("§r§c[BOSS] ") && !msg.contains("Watcher")) {

            if (gameStage != 3) {
                gameStage = 3;
                ChatUtils.info("DungeonRooms → Stage 3 (Boss)");

                RoomDetection.resetCurrentRoom();
                RoomDetection.roomName = "Boss Room";
                RoomDetection.roomCategory = "General";
            }
        }

        // Run completed
        else if (msg.contains("§r§c☠ §r§eDefeated §r")) {
            gameStage = 4;
            ChatUtils.info("DungeonRooms → Stage 4 (Run Finished)");
            RoomDetection.resetCurrentRoom();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (gameStage == 0) {
            gameStage = 1;
            ChatUtils.info("DungeonRooms → Stage 1 (Started)");

            if (!firstMessageShown) {
                firstMessageShown = true;

                mc.player.sendMessage(Text.literal(
                    "§dDungeonRooms§7 active. Waypoints ready."
                ), false);
            }
        }
    }

    @EventHandler
    private void onLeave(GameLeftEvent event) {
        gameStage = 0;
        bloodTime = Long.MAX_VALUE;

        RoomDetection.resetCurrentRoom();
        Waypoints.allSecretsMap.clear();
        Waypoints.secretNum = 0;

        ChatUtils.info("DungeonRooms → Reset due to world leave");
    }
}
