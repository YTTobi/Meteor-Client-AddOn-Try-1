package com.example.addon.modules.Hypixel;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;



public class ChatLogger extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> savePath = sgGeneral.add(new StringSetting.Builder()
        .name("save-path")
        .description("Where logs are saved.")
        .defaultValue("C:/ChatLogs/")
        .build()
    );

    private final DateTimeFormatter timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ChatLogger() {
        super(AddonTemplate.HYPIXEL, "chat-logger", "Logs player chat, abilities and sacks.");
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString();
        if (!shouldLog(msg)) return;

        writeToFile(clean(msg));
    }

    private boolean shouldLog(String msg) {
        return isPlayerChat(msg) || isAbility(msg) || isSacks(msg);
    }

    private boolean isPlayerChat(String msg) {
        if (!msg.contains(":")) return false;
        if (!msg.startsWith("[")) return false;
        String before = msg.split(":", 2)[0];
        return before.matches(".*[A-Za-z0-9_§]+");
    }

    private boolean isAbility(String msg) {
        return msg.contains("You used")
            || msg.contains("You activated")
            || msg.contains("You summoned")
            || msg.contains("has expired")
            || msg.startsWith("Your");
    }

    private boolean isSacks(String msg) {
        return msg.contains("[Sacks]");
    }

    private String clean(String msg) {
        String noColor = msg.replaceAll("§.", "");
        return "[" + LocalDateTime.now().format(timestamp) + "] " + noColor;
    }

    private void writeToFile(String line) {
        String base = savePath.get();
        if (!base.endsWith("/") && !base.endsWith("\\")) base += "/";

        new File(base).mkdirs();

        String file = "ChatLog-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".txt";

        try (BufferedWriter w = new BufferedWriter(new FileWriter(base + file, true))) {
            w.write(line);
            w.newLine();
        } catch (IOException e) {
            System.err.println("[ChatLogger] Failed writing file: " + e.getMessage());
        }
    }
}
