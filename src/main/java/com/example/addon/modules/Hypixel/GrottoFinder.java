package com.example.addon.modules.Hypixel;

import com.example.addon.AddonTemplate;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GrottoFinder extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ---------------- Settings ----------------
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgScan = settings.createGroup("Scan");
    private final SettingGroup sgLobbyId = settings.createGroup("Lobby ID");
    private final SettingGroup sgAccuracy = settings.createGroup("Accuracy");

    private final Setting<Boolean> chatNotify = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-notify")
        .description("Show module messages in your client chat (only you see it).")
        .defaultValue(true)
        .build()
    );

    public enum DebugLevel { OFF, BASIC, VERBOSE }

    private final Setting<DebugLevel> debugLevel = sgGeneral.add(new EnumSetting.Builder<DebugLevel>()
        .name("debug-level")
        .description("OFF = no debug, BASIC = state/events, VERBOSE = state/events + dumps + feature breakdown.")
        .defaultValue(DebugLevel.BASIC)
        .build()
    );

    private final Setting<Integer> nucleusIgnoreRadius = sgGeneral.add(new IntSetting.Builder()
        .name("nucleus-ignore-radius")
        .description("Ignore grotto detection within this XZ radius around (0,0).")
        .defaultValue(130)
        .min(0)
        .sliderMax(400)
        .build()
    );

    private final Setting<Integer> chunkRadius = sgScan.add(new IntSetting.Builder()
        .name("chunk-radius")
        .description("How many chunks around you to scan (loaded chunks only).")
        .defaultValue(8)
        .min(2)
        .sliderMax(16)
        .build()
    );

    private final Setting<Integer> chunksPerTick = sgScan.add(new IntSetting.Builder()
        .name("chunks-per-tick")
        .description("How many loaded chunks to scan per tick.")
        .defaultValue(2)
        .min(1)
        .sliderMax(8)
        .build()
    );

    private final Setting<Integer> rescanCooldownMs = sgScan.add(new IntSetting.Builder()
        .name("chunk-rescan-cooldown-ms")
        .description("Minimum time before scanning the same chunk again.")
        .defaultValue(3000)
        .min(250)
        .sliderMax(15000)
        .build()
    );

    private final Setting<Integer> analysisCooldownMs = sgScan.add(new IntSetting.Builder()
        .name("analysis-cooldown-ms")
        .description("How often we attempt recognition (ms).")
        .defaultValue(2000)
        .min(250)
        .sliderMax(10000)
        .build()
    );

    private final Setting<Integer> minClusterSize = sgAccuracy.add(new IntSetting.Builder()
        .name("min-cluster-size")
        .description("Ignore tiny clusters of magenta (noise).")
        .defaultValue(120)
        .min(20)
        .sliderMax(800)
        .build()
    );

    private final Setting<Double> importantConfidence = sgAccuracy.add(new DoubleSetting.Builder()
        .name("important-confidence")
        .description("Minimum confidence to announce Mansion/Palace/Shrine/Overgrown.")
        .defaultValue(0.86)
        .min(0.50)
        .sliderMax(0.99)
        .build()
    );

    private final Setting<Double> otherConfidence = sgAccuracy.add(new DoubleSetting.Builder()
        .name("other-confidence")
        .description("Minimum confidence to announce non-important variants.")
        .defaultValue(0.70)
        .min(0.40)
        .sliderMax(0.95)
        .build()
    );

    public enum LobbyIdSource { AUTO, CHAT_ONLY, SCOREBOARD_ONLY }

    private final Setting<LobbyIdSource> lobbyIdSource = sgLobbyId.add(new EnumSetting.Builder<LobbyIdSource>()
        .name("lobby-id-source")
        .description("AUTO = scoreboard first then chat. SCOREBOARD_ONLY = uses scoreboard line after date.")
        .defaultValue(LobbyIdSource.AUTO)
        .build()
    );

    private final Setting<Integer> lobbyIdWaitSeconds = sgLobbyId.add(new IntSetting.Builder()
        .name("lobby-id-wait-seconds")
        .description("After arming via /warp nuc|hollows, how long to wait for lobby id.")
        .defaultValue(15)
        .min(3)
        .sliderMax(60)
        .build()
    );

    // NEW: Save mode + duplicate notify
    public enum SaveMode { AUTO, MANUAL }

    private final Setting<SaveMode> saveMode = sgLobbyId.add(new EnumSetting.Builder<SaveMode>()
        .name("save-mode")
        .description("AUTO saves lobby IDs on detection. MANUAL only saves when you run a command.")
        .defaultValue(SaveMode.AUTO)
        .build()
    );

    private final Setting<Boolean> notifyDuplicate = sgLobbyId.add(new BoolSetting.Builder()
        .name("notify-duplicate")
        .description("If a detected lobby ID is already known, notify in chat/log.")
        .defaultValue(true)
        .build()
    );

    // ---------------- Persistence ----------------
    private final Path savePath;
    private final Set<String> knownLobbyIds = new HashSet<>();
    private int uniqueLobbyCount = 0;

    // ---------------- Runtime state ----------------
    private boolean inCrystalHollows = false;
    private boolean awaitingLobbyId = false;
    private long awaitLobbyIdUntilMs = 0L;
    private String currentLobbyId = null;
    private String lastAnnouncedLobbyId = null;

    // NEW: warp-driven gating
    private boolean lobbyCheckArmed = false; // only capture when armed
    private boolean rearmAllowed = true;     // becomes true only after /warp != nuc/hollows

    private final Map<String, LinkedHashSet<String>> lobbyToFoundVariants = new HashMap<>();

    // Chunk scanning state
    private final ArrayDeque<ChunkPos> chunkQueue = new ArrayDeque<>();
    private final HashMap<Long, Long> lastChunkScan = new HashMap<>();
    private long lastChunkQueueBuildMs = 0L;

    // Magenta hits
    private final HashSet<BlockPos> magentaSet = new HashSet<>();
    private long lastAnalysisMs = 0L;

    // Debug throttling
    private long lastLobbyStatusMs = 0L;
    private long lastGrottoStatusMs = 0L;

    // Patterns
    private static final Pattern P_GENERIC_ID = Pattern.compile("(?i)\\b(server id|lobby id|server|id)\\b\\s*[:=]\\s*([A-Z0-9\\-]{3,}|[0-9a-fA-F\\-]{6,})");
    private static final Pattern P_LOBBY_PURE = Pattern.compile("(?i)^[A-Z0-9]{1,6}$");

    // Blocks
    private static final Block MAG_PANE  = Blocks.MAGENTA_STAINED_GLASS_PANE;
    private static final Block MAG_GLASS = Blocks.MAGENTA_STAINED_GLASS;

    // ---------------- Logging ----------------
    private boolean dbg(DebugLevel level) {
        return debugLevel.get().ordinal() >= level.ordinal();
    }

    private void hardLog(String s) {
        MeteorClient.LOG.info("[GrottoFinder] " + stripFormatting(s));
        if (chatNotify.get()) info(s); // client-only chat
    }

    private void dbgMsg(DebugLevel level, String title, String body) {
        if (!dbg(level)) return;
        hardLog("§7[§b" + title + "§7]§r " + body);
    }

    private void dbgKeyVal(DebugLevel level, String title, String... kv) {
        if (!dbg(level)) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) sb.append(" §8|§r ");
            sb.append("§f").append(kv[i]).append("§7=").append("§a").append(kv[i + 1]).append("§r");
        }
        dbgMsg(level, title, sb.toString());
    }

    public GrottoFinder() {
        super(AddonTemplate.HYPIXEL, "grotto-finder",
            "Stores Crystal Hollows lobby IDs and recognizes Fairy Grottos (through walls, loaded chunks).");

        Path base = mc.runDirectory.toPath().resolve("meteor-client");
        this.savePath = base.resolve("grotto-finder.json");

        hardLog("Constructed. savePath=" + savePath.toAbsolutePath());
        load();
    }

    @Override
    public void onActivate() {
        load();
        resetRuntime();
        hardLog("§aON-ACTIVATE fired§r | debug=" + debugLevel.get() + " | chatNotify=" + chatNotify.get());
        dbgKeyVal(DebugLevel.BASIC, "STATE",
            "knownLobbyIds", String.valueOf(knownLobbyIds.size()),
            "uniqueLobbyCount", String.valueOf(uniqueLobbyCount),
            "saveMode", saveMode.get().name()
        );
    }

    @Override
    public void onDeactivate() {
        save();
        resetRuntime();
        hardLog("§cON-DEACTIVATE fired§r");
    }

    private void resetRuntime() {
        inCrystalHollows = false;
        awaitingLobbyId = false;
        awaitLobbyIdUntilMs = 0L;
        currentLobbyId = null;
        lastAnnouncedLobbyId = null;

        lobbyCheckArmed = false;
        rearmAllowed = true;

        chunkQueue.clear();
        magentaSet.clear();
        lastChunkScan.clear();
        lastChunkQueueBuildMs = 0L;
        lastAnalysisMs = 0L;

        lastLobbyStatusMs = 0L;
        lastGrottoStatusMs = 0L;

        lobbyToFoundVariants.clear();
    }

    // ---------------- Commands hooks (for .grotto save / clear etc.) ----------------
    public void commandClearLobbyIds() {
        knownLobbyIds.clear();
        uniqueLobbyCount = 0;
        save();
        hardLog("§cCleared§r all saved lobby IDs.");
    }

    public void commandSaveCurrentLobbyId() {
        if (currentLobbyId == null) {
            hardLog("§cNo lobby id§r to save right now.");
            return;
        }

        boolean isNew = knownLobbyIds.add(currentLobbyId);
        if (isNew) {
            uniqueLobbyCount = Math.max(uniqueLobbyCount + 1, knownLobbyIds.size());
            save();
            hardLog("§aManually saved§r lobby: §b" + currentLobbyId + "§r (unique: §e" + uniqueLobbyCount + "§r)");
        } else {
            if (notifyDuplicate.get()) hardLog("§eAlready saved§r: §b" + currentLobbyId + "§r");
            else hardLog("Lobby already saved: §b" + currentLobbyId + "§r");
        }
    }

    // ---------------- Events ----------------
    @EventHandler
    private void onSendChat(SendMessageEvent event) {
        if (mc.player == null) return;
        if (event.message == null) return;

        String m = event.message.trim();
        if (!m.toLowerCase(Locale.ROOT).startsWith("/warp ")) return;

        String arg = m.substring(6).trim();
        if (arg.isEmpty()) return;

        boolean isNuc = arg.equalsIgnoreCase("nuc");
        boolean isHollows = arg.equalsIgnoreCase("hollows");

        // Any other warp enables next capture
        if (!isNuc && !isHollows) {
            rearmAllowed = true;
            lobbyCheckArmed = false;
            dbgKeyVal(DebugLevel.BASIC, "WARP", "arg", arg, "rearmAllowed", "true");
            return;
        }

        // /warp nuc or /warp hollows: arm ONLY if allowed
        if (!rearmAllowed) {
            dbgKeyVal(DebugLevel.BASIC, "WARP", "arg", arg, "armed", "ignored (need other /warp first)");
            return;
        }

        rearmAllowed = false;
        lobbyCheckArmed = true;

        awaitingLobbyId = true;
        awaitLobbyIdUntilMs = System.currentTimeMillis() + lobbyIdWaitSeconds.get() * 1000L;
        currentLobbyId = null;

        hardLog("§bWarp detected§r (" + arg + "). Will capture lobby id once (CH only).");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        boolean nowInCH = isInCrystalHollows();

        if (!inCrystalHollows && nowInCH) {
            inCrystalHollows = true;

            // Only check lobby id when armed via /warp nuc|hollows
            if (lobbyCheckArmed) {
                hardLog("Entered §bCrystal Hollows§r. Armed -> searching lobby id...");

                // Try scoreboard immediately
                if (lobbyIdSource.get() != LobbyIdSource.CHAT_ONLY) {
                    String sbId = tryExtractLobbyIdFromScoreboardCrystalHollows();
                    dbgKeyVal(DebugLevel.BASIC, "LOBBY-ID TRY", "from", "scoreboard", "result", String.valueOf(sbId));
                    if (sbId != null) {
                        awaitingLobbyId = false;
                        lobbyCheckArmed = false; // 1x per arm
                        setCurrentLobbyId(sbId);
                    }
                }

                if (dbg(DebugLevel.VERBOSE)) debugDumpScoreboard();
            } else {
                dbgMsg(DebugLevel.BASIC, "CH", "Entered CH but not armed (no /warp nuc|hollows trigger).");
            }
        }

        if (inCrystalHollows && !nowInCH) {
            inCrystalHollows = false;
            awaitingLobbyId = false;
            currentLobbyId = null;

            chunkQueue.clear();
            magentaSet.clear();

            hardLog("Left Crystal Hollows. Cleared state.");
        }

        // Keep checking scoreboard if still no lobby id (ONLY if armed)
        if (inCrystalHollows && lobbyCheckArmed && currentLobbyId == null && lobbyIdSource.get() != LobbyIdSource.CHAT_ONLY) {
            String sbId = tryExtractLobbyIdFromScoreboardCrystalHollows();
            if (sbId != null) {
                dbgKeyVal(DebugLevel.BASIC, "LOBBY-ID FOUND", "from", "scoreboard", "id", sbId);
                awaitingLobbyId = false;
                lobbyCheckArmed = false; // 1x per arm
                setCurrentLobbyId(sbId);
            }
        }

        if (awaitingLobbyId && System.currentTimeMillis() > awaitLobbyIdUntilMs) {
            awaitingLobbyId = false;
            lobbyCheckArmed = false; // stop trying after timeout
            dbgMsg(DebugLevel.BASIC, "LOBBY", "Wait timed out (no lobby id matched)");
        }

        // Always show lobby-id status while in CH (debug)
        debugLobbyStatus();

        // Without lobby id, no scanning
        if (!inCrystalHollows || currentLobbyId == null) return;
        if (isInCrystalNucleus()) return;

        long now = System.currentTimeMillis();

        if (now - lastChunkQueueBuildMs > 1000) {
            rebuildChunkQueue(chunkRadius.get());
            lastChunkQueueBuildMs = now;
            dbgKeyVal(DebugLevel.VERBOSE, "CHUNK QUEUE", "queued", String.valueOf(chunkQueue.size()));
        }

        scanChunksStep(chunksPerTick.get(), rescanCooldownMs.get());

        if (now - lastAnalysisMs > analysisCooldownMs.get()) {
            lastAnalysisMs = now;

            dbgKeyVal(DebugLevel.VERBOSE, "ANALYZE",
                "magentaHits", String.valueOf(magentaSet.size()),
                "minClusterSize", String.valueOf(minClusterSize.get())
            );

            ClusterInfo cluster = extractLargestCluster(magentaSet, minClusterSize.get());
            if (cluster == null) {
                dbgKeyVal(DebugLevel.BASIC, "CLUSTER",
                    "status", "ID_NOT_FOUND",
                    "magentaHits", String.valueOf(magentaSet.size()),
                    "minClusterSize", String.valueOf(minClusterSize.get())
                );
                return;
            }

            Recognition r = recognize(cluster);
            double required = r.variant.isImportant ? importantConfidence.get() : otherConfidence.get();

            dbgKeyVal(DebugLevel.BASIC, "RECOGNITION RESULT",
                "picked", r.variant.displayName,
                "conf", String.format(Locale.ROOT, "%.3f", r.confidence),
                "required", String.format(Locale.ROOT, "%.3f", required),
                "lobbyId", currentLobbyId != null ? currentLobbyId : "ID_NOT_FOUND",
                "magentaHits", String.valueOf(magentaSet.size()),
                "clusterTotal", String.valueOf(cluster.total),
                "panes", String.valueOf(cluster.panes),
                "glass", String.valueOf(cluster.glass),
                "dx", String.valueOf(cluster.dx),
                "dy", String.valueOf(cluster.dy),
                "dz", String.valueOf(cluster.dz)
            );

            // Always say if we found something or not (throttled)
            if (dbg(DebugLevel.BASIC) && now - lastGrottoStatusMs > 1500) {
                lastGrottoStatusMs = now;

                if (r.variant != Variant.OTHER) {
                    dbgMsg(DebugLevel.BASIC, "GROTTO CANDIDATE",
                        "FOUND candidate=" + r.variant.displayName +
                            " conf=" + String.format(Locale.ROOT, "%.3f", r.confidence) +
                            " required=" + String.format(Locale.ROOT, "%.3f", required) +
                            " lobby=" + (currentLobbyId == null ? "ID_NOT_FOUND" : currentLobbyId)
                    );
                } else {
                    dbgMsg(DebugLevel.BASIC, "GROTTO CANDIDATE",
                        "NO MATCH (candidate=Unknown) lobby=" + (currentLobbyId == null ? "ID_NOT_FOUND" : currentLobbyId)
                    );
                }
            }

            // Only announce if confident
            if (r.variant != Variant.OTHER && r.confidence >= required) {
                announceVariant(r.variant.displayName, r.confidence);
            }
        }
    }

    @EventHandler
    private void onChat(ReceiveMessageEvent event) {
        if (mc.player == null) return;
        if (!awaitingLobbyId) return;
        if (!lobbyCheckArmed) return; // NEW: only while armed
        if (lobbyIdSource.get() == LobbyIdSource.SCOREBOARD_ONLY) return;
        if (!inCrystalHollows) return; // safety

        String raw = stripFormatting(event.getMessage().getString());
        Matcher m = P_GENERIC_ID.matcher(raw);
        if (!m.find()) return;

        String id = m.group(2);
        dbgKeyVal(DebugLevel.BASIC, "LOBBY-ID FOUND", "from", "chat", "id", id, "raw", raw);

        awaitingLobbyId = false;
        lobbyCheckArmed = false; // 1x per arm
        setCurrentLobbyId(id);
    }

    // ---------------- Lobby status debug ----------------
    private void debugLobbyStatus() {
        if (!dbg(DebugLevel.BASIC)) return;
        if (!inCrystalHollows) return;

        long now = System.currentTimeMillis();
        if (now - lastLobbyStatusMs < 1000) return; // once per second
        lastLobbyStatusMs = now;

        String id = currentLobbyId;
        if (id == null && lobbyCheckArmed) {
            String sb = tryExtractLobbyIdFromScoreboardCrystalHollows();
            id = (sb != null) ? sb : "ID_NOT_FOUND";
        }
        if (id == null) id = "ID_NOT_FOUND";

        dbgKeyVal(DebugLevel.BASIC, "LOBBY STATUS",
            "LobbyId", id,
            "awaiting", String.valueOf(awaitingLobbyId),
            "armed", String.valueOf(lobbyCheckArmed),
            "rearmAllowed", String.valueOf(rearmAllowed),
            "saveMode", saveMode.get().name(),
            "sourceMode", lobbyIdSource.get().name()
        );
    }

    private void setCurrentLobbyId(String id) {
        currentLobbyId = id;

        boolean alreadyKnown = knownLobbyIds.contains(id);

        // Duplicate notify (on detection)
        if (alreadyKnown && notifyDuplicate.get()) {
            hardLog("§eDuplicate lobby§r detected: §b" + id + "§r (already saved)");
        }

        // Save depending on mode
        if (saveMode.get() == SaveMode.AUTO) {
            boolean isNew = knownLobbyIds.add(id);
            if (isNew) {
                uniqueLobbyCount = Math.max(uniqueLobbyCount + 1, knownLobbyIds.size());
                save();
                hardLog("New lobby saved: §b" + id + "§r (unique: §e" + uniqueLobbyCount + "§r)");
            } else {
                hardLog("Re-joined known lobby: §b" + id + "§r (unique: §e" + uniqueLobbyCount + "§r)");
            }

            dbgKeyVal(DebugLevel.BASIC, "SET LOBBY",
                "lobbyId", id,
                "isNew", String.valueOf(isNew),
                "saveMode", saveMode.get().name()
            );
        } else {
            hardLog("Lobby detected (manual-save mode): §b" + id + "§r (not saved yet)");
            dbgKeyVal(DebugLevel.BASIC, "SET LOBBY",
                "lobbyId", id,
                "saveMode", saveMode.get().name()
            );
        }

        lastAnnouncedLobbyId = id;
        lobbyToFoundVariants.computeIfAbsent(id, k -> new LinkedHashSet<>());
    }

    // ---------------- Scoreboard lobby id (Crystal Hollows only, SKYBLOCK line layout) ----------------
    private String tryExtractLobbyIdFromScoreboardCrystalHollows() {
        // Must be in CH
        if (!isInCrystalHollows()) return null;

        // Must be layout:
        // 0: SKYBLOCK (title)
        // 1: date (ignored)
        // 2: lobby id (pure A-Z0-9 1..6)
        List<String> lines = getScoreboardLinesOrderedWithTitleFirst();
        if (lines.size() < 3) return null;

        String title = stripFormatting(lines.get(0)).trim();
        if (!title.equalsIgnoreCase("SKYBLOCK")) return null;

        String candidate = stripFormatting(lines.get(2)).trim();
        if (!P_LOBBY_PURE.matcher(candidate).matches()) return null;

        if (lastAnnouncedLobbyId != null && candidate.equalsIgnoreCase(lastAnnouncedLobbyId)) return null;
        return candidate;
    }

    private void debugDumpScoreboard() {
        List<String> lines = getScoreboardLinesOrderedWithTitleFirst();
        hardLog("§d[SCOREBOARD DUMP]§r lines=" + lines.size());
        for (int i = 0; i < lines.size(); i++) {
            hardLog("§7#" + i + "§r " + lines.get(i));
        }
    }

    private boolean isInCrystalHollows() {
        for (String s : getScoreboardLinesOrderedWithTitleFirst()) {
            String t = stripFormatting(s).toLowerCase(Locale.ROOT);
            if (t.contains("crystal hollows")) return true;
        }
        return false;
    }

    private boolean isInCrystalNucleus() {
        for (String s : getScoreboardLinesOrderedWithTitleFirst()) {
            String t = stripFormatting(s).toLowerCase(Locale.ROOT);
            if (t.contains("crystal nucleus")) return true;
        }

        Vec3d p = mc.player.getPos();
        double dist2 = p.x * p.x + p.z * p.z;
        int r = nucleusIgnoreRadius.get();
        return dist2 <= (double) r * (double) r;
    }

    /**
     * Returns scoreboard as the player sees it:
     * index 0 = objective title (e.g. SKYBLOCK),
     * then ordered lines (by score desc) best-effort.
     *
     * We use reflection for entry score/value to survive mapping changes.
     */
    private List<String> getScoreboardLinesOrderedWithTitleFirst() {
        if (mc.world == null) return Collections.emptyList();

        Scoreboard sb = mc.world.getScoreboard();
        ScoreboardObjective obj = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (obj == null) return Collections.emptyList();

        ArrayList<String> out = new ArrayList<>();

        // Title first (this is the "SKYBLOCK" top line)
        out.add(obj.getDisplayName().getString());

        Collection<ScoreboardEntry> entries = sb.getScoreboardEntries(obj);
        if (entries == null || entries.isEmpty()) return out;

        ArrayList<ScoreboardEntry> list = new ArrayList<>(entries);

        Method mValue = null;
        for (String name : new String[] {"value", "score", "getScore", "getValue"}) {
            try {
                mValue = ScoreboardEntry.class.getMethod(name);
                break;
            } catch (NoSuchMethodException ignored) {}
        }

        if (mValue != null) {
            Method finalMValue = mValue;
            list.sort((a, b) -> {
                int va = 0, vb = 0;
                try { va = ((Number) finalMValue.invoke(a)).intValue(); } catch (Exception ignored) {}
                try { vb = ((Number) finalMValue.invoke(b)).intValue(); } catch (Exception ignored) {}
                return Integer.compare(vb, va); // desc
            });
        }

        for (ScoreboardEntry e : list) {
            Text name = e.name();
            if (name != null) out.add(name.getString());
        }

        return out;
    }

    private String stripFormatting(String s) {
        return s.replaceAll("§.", "");
    }

    // ---------------- Chunk scanning ----------------
    private void rebuildChunkQueue(int radiusChunks) {
        chunkQueue.clear();

        BlockPos p = mc.player.getBlockPos();
        int pcx = p.getX() >> 4;
        int pcz = p.getZ() >> 4;

        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                if (mc.world.getChunkManager().isChunkLoaded(cx, cz)) {
                    chunkQueue.add(new ChunkPos(cx, cz));
                }
            }
        }
    }

    private void scanChunksStep(int chunksToScan, int cooldownMs) {
        if (mc.world == null) return;

        int n = chunksToScan;
        long now = System.currentTimeMillis();

        int minY = mc.world.getDimension().minY();
        int maxY = minY + mc.world.getDimension().height();

        while (n-- > 0 && !chunkQueue.isEmpty()) {
            ChunkPos cp = chunkQueue.pollFirst();
            long key = cp.toLong();

            Long last = lastChunkScan.get(key);
            if (last != null && now - last < cooldownMs) continue;
            lastChunkScan.put(key, now);

            var chunk = mc.world.getChunk(cp.x, cp.z);

            int hitsBefore = magentaSet.size();

            for (int y = minY; y < maxY; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockPos pos = new BlockPos((cp.x << 4) + x, y, (cp.z << 4) + z);
                        BlockState st = chunk.getBlockState(pos);
                        Block b = st.getBlock();
                        if (b == MAG_PANE || b == MAG_GLASS) magentaSet.add(pos);
                    }
                }
            }

            if (dbg(DebugLevel.VERBOSE)) {
                dbgKeyVal(DebugLevel.VERBOSE, "SCAN CHUNK",
                    "chunk", cp.x + "," + cp.z,
                    "hitsAdded", String.valueOf(magentaSet.size() - hitsBefore),
                    "hitsTotal", String.valueOf(magentaSet.size())
                );
            }
        }
    }

    // ---------------- Cluster + Recognition ----------------
    private static class ClusterInfo {
        int panes;
        int glass;
        int total;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        double planeXStrength;
        double planeZStrength;
        int dx, dy, dz;
    }

    private ClusterInfo extractLargestCluster(Set<BlockPos> hits, int minSize) {
        if (hits.isEmpty() || mc.world == null) return null;

        HashSet<BlockPos> remaining = new HashSet<>(hits);

        ClusterInfo best = null;
        int bestSize = 0;

        ArrayDeque<BlockPos> q = new ArrayDeque<>();

        while (!remaining.isEmpty()) {
            Iterator<BlockPos> it = remaining.iterator();
            BlockPos start = it.next();
            it.remove();

            ClusterInfo ci = new ClusterInfo();
            q.clear();
            q.add(start);

            HashMap<Integer, Integer> countByX = new HashMap<>();
            HashMap<Integer, Integer> countByZ = new HashMap<>();

            while (!q.isEmpty()) {
                BlockPos p = q.pollFirst();
                Block b = mc.world.getBlockState(p).getBlock();
                boolean isPane = (b == MAG_PANE);
                boolean isGlass = (b == MAG_GLASS);
                if (!isPane && !isGlass) continue;

                ci.total++;
                if (isPane) ci.panes++; else ci.glass++;

                ci.minX = Math.min(ci.minX, p.getX());
                ci.minY = Math.min(ci.minY, p.getY());
                ci.minZ = Math.min(ci.minZ, p.getZ());
                ci.maxX = Math.max(ci.maxX, p.getX());
                ci.maxY = Math.max(ci.maxY, p.getY());
                ci.maxZ = Math.max(ci.maxZ, p.getZ());

                countByX.put(p.getX(), countByX.getOrDefault(p.getX(), 0) + 1);
                countByZ.put(p.getZ(), countByZ.getOrDefault(p.getZ(), 0) + 1);

                BlockPos n1 = p.north(), n2 = p.south(), n3 = p.east(), n4 = p.west(), n5 = p.up(), n6 = p.down();
                if (remaining.remove(n1)) q.add(n1);
                if (remaining.remove(n2)) q.add(n2);
                if (remaining.remove(n3)) q.add(n3);
                if (remaining.remove(n4)) q.add(n4);
                if (remaining.remove(n5)) q.add(n5);
                if (remaining.remove(n6)) q.add(n6);
            }

            if (ci.total < minSize) continue;

            ci.dx = (ci.maxX - ci.minX) + 1;
            ci.dy = (ci.maxY - ci.minY) + 1;
            ci.dz = (ci.maxZ - ci.minZ) + 1;

            int maxPlaneX = 0;
            for (int v : countByX.values()) maxPlaneX = Math.max(maxPlaneX, v);
            int maxPlaneZ = 0;
            for (int v : countByZ.values()) maxPlaneZ = Math.max(maxPlaneZ, v);

            ci.planeXStrength = ci.total == 0 ? 0 : (double) maxPlaneX / (double) ci.total;
            ci.planeZStrength = ci.total == 0 ? 0 : (double) maxPlaneZ / (double) ci.total;

            if (ci.total > bestSize) {
                bestSize = ci.total;
                best = ci;
            }
        }

        return best;
    }

    private enum Variant {
        MANSION("Mansion", true),
        PALACE("Palace", true),
        SHRINE("Shrine", true),
        OVERGROWN("Overgrown", true),
        RUINS("Ruins", false),
        ARCH("Arch", false),
        WATERFALLS("Waterfalls", false),
        SPIRAL("Spiral", false),
        OTHER("Unknown", false);

        final String displayName;
        final boolean isImportant;

        Variant(String name, boolean important) {
            this.displayName = name;
            this.isImportant = important;
        }
    }

    private static class Recognition {
        final Variant variant;
        final double confidence;

        Recognition(Variant v, double c) {
            this.variant = v;
            this.confidence = c;
        }
    }

    private Recognition recognize(ClusterInfo c) {
        int panes = c.panes, glass = c.glass, total = c.total;
        int dx = c.dx, dy = c.dy, dz = c.dz;
        int footprint = dx * dz;

        double paneFrac = total == 0 ? 0 : (double) panes / total;
        double glassFrac = total == 0 ? 0 : (double) glass / total;
        double tallness = (double) dy / (double) Math.max(dx, dz);
        double plane = Math.max(c.planeXStrength, c.planeZStrength);

        if (dbg(DebugLevel.VERBOSE)) {
            dbgKeyVal(DebugLevel.VERBOSE, "FEATURES",
                "total", String.valueOf(total),
                "panes", String.valueOf(panes),
                "glass", String.valueOf(glass),
                "dx", String.valueOf(dx),
                "dy", String.valueOf(dy),
                "dz", String.valueOf(dz),
                "footprint", String.valueOf(footprint),
                "paneFrac", String.format(Locale.ROOT, "%.3f", paneFrac),
                "glassFrac", String.format(Locale.ROOT, "%.3f", glassFrac),
                "tallness", String.format(Locale.ROOT, "%.3f", tallness),
                "plane", String.format(Locale.ROOT, "%.3f", plane)
            );
        }

        Recognition mansion = scoreMansion(footprint, dy, panes, glass, paneFrac, plane);
        Recognition palace  = scorePalace(footprint, dy, panes, glass, glassFrac, plane);
        Recognition shrine  = scoreShrine(footprint, dy, panes, glass, glassFrac, tallness);
        Recognition over    = scoreOvergrown(footprint, dy, panes, glass, paneFrac, plane);

        Recognition bestImportant = bestOf(mansion, palace, shrine, over);
        Recognition secondImportant = secondBestOf(mansion, palace, shrine, over);

        Recognition ruins = scoreRuins(footprint, dy, panes, glass, paneFrac);
        Recognition arch = scoreArch(footprint, dy, panes, glass, paneFrac);
        Recognition waterfalls = scoreWaterfalls(footprint, dy, panes, glass, tallness);
        Recognition spiral = scoreSpiral(footprint, dy, panes, glass, tallness);

        if (bestImportant.confidence >= importantConfidence.get() && (bestImportant.confidence - secondImportant.confidence) >= 0.08) {
            return bestImportant;
        }

        Recognition bestOther = bestOf(ruins, arch, waterfalls, spiral);
        if (bestOther.confidence >= otherConfidence.get()) return bestOther;

        return new Recognition(Variant.OTHER, 0.0);
    }

    private Recognition bestOf(Recognition... rs) {
        Recognition best = rs[0];
        for (Recognition r : rs) if (r.confidence > best.confidence) best = r;
        return best;
    }

    private Recognition secondBestOf(Recognition... rs) {
        Recognition best = rs[0];
        Recognition second = rs[0];
        for (Recognition r : rs) {
            if (r.confidence > best.confidence) { second = best; best = r; }
            else if (r != best && r.confidence > second.confidence) { second = r; }
        }
        return second;
    }

    private double clamp01(double x) { return Math.max(0, Math.min(1, x)); }
    private double inRangeScore(double x, double lo, double hi) {
        if (x < lo || x > hi) return 0;
        double mid = (lo + hi) / 2.0;
        double span = (hi - lo) / 2.0;
        double d = Math.abs(x - mid) / span;
        return clamp01(1.0 - d);
    }
    private double minScore(double x, double min) { return x >= min ? 1.0 : clamp01(x / min); }
    private double maxScore(double x, double max) { return x <= max ? 1.0 : clamp01(max / x); }

    private Recognition scoreMansion(int footprint, int dy, int panes, int glass, double paneFrac, double plane) {
        double s1 = minScore(footprint, 900);
        double s2 = minScore(panes, 180);
        double s3 = maxScore(glass, 10);
        double s4 = maxScore(dy, 26);
        double s5 = minScore(paneFrac, 0.72);
        double s6 = minScore(plane, 0.16);
        double conf = 0.22*s1 + 0.20*s2 + 0.18*s3 + 0.12*s4 + 0.14*s5 + 0.14*s6;
        return new Recognition(Variant.MANSION, clamp01(conf));
    }

    private Recognition scorePalace(int footprint, int dy, int panes, int glass, double glassFrac, double plane) {
        double s1 = minScore(footprint, 650);
        double s2 = minScore(panes, 120);
        double s3 = minScore(glass, 70);
        double s4 = inRangeScore(dy, 18, 38);
        double s5 = minScore(glassFrac, 0.28);
        double s6 = minScore(plane, 0.10);
        double conf = 0.22*s1 + 0.18*s2 + 0.20*s3 + 0.14*s4 + 0.14*s5 + 0.12*s6;
        return new Recognition(Variant.PALACE, clamp01(conf));
    }

    private Recognition scoreShrine(int footprint, int dy, int panes, int glass, double glassFrac, double tallness) {
        double s1 = inRangeScore(footprint, 220, 700);
        double s2 = minScore(glass, 85);
        double s3 = inRangeScore(panes, 35, 160);
        double s4 = inRangeScore(dy, 18, 45);
        double s5 = minScore(glassFrac, 0.45);
        double s6 = inRangeScore(tallness, 0.45, 1.50);
        double conf = 0.18*s1 + 0.22*s2 + 0.12*s3 + 0.18*s4 + 0.18*s5 + 0.12*s6;
        return new Recognition(Variant.SHRINE, clamp01(conf));
    }

    private Recognition scoreOvergrown(int footprint, int dy, int panes, int glass, double paneFrac, double plane) {
        double s1 = minScore(footprint, 520);
        double s2 = minScore(panes, 120);
        double s3 = inRangeScore(glass, 25, 110);
        double s4 = maxScore(dy, 30);
        double s5 = inRangeScore(paneFrac, 0.55, 0.78);
        double s6 = maxScore(plane, 0.20);
        double conf = 0.22*s1 + 0.20*s2 + 0.16*s3 + 0.14*s4 + 0.16*s5 + 0.12*s6;
        return new Recognition(Variant.OVERGROWN, clamp01(conf));
    }

    private Recognition scoreRuins(int footprint, int dy, int panes, int glass, double paneFrac) {
        double s1 = inRangeScore(footprint, 260, 900);
        double s2 = inRangeScore(dy, 10, 28);
        double s3 = minScore(panes, 70);
        double s4 = inRangeScore(glass, 6, 60);
        double s5 = inRangeScore(paneFrac, 0.45, 0.75);
        double conf = 0.22*s1 + 0.18*s2 + 0.22*s3 + 0.18*s4 + 0.20*s5;
        return new Recognition(Variant.RUINS, clamp01(conf));
    }

    private Recognition scoreArch(int footprint, int dy, int panes, int glass, double paneFrac) {
        double s1 = inRangeScore(footprint, 120, 380);
        double s2 = inRangeScore(dy, 10, 26);
        double s3 = minScore(panes, 40);
        double s4 = maxScore(glass, 90);
        double s5 = minScore(paneFrac, 0.60);
        double conf = 0.24*s1 + 0.18*s2 + 0.22*s3 + 0.14*s4 + 0.22*s5;
        return new Recognition(Variant.ARCH, clamp01(conf));
    }

    private Recognition scoreWaterfalls(int footprint, int dy, int panes, int glass, double tallness) {
        double s1 = inRangeScore(footprint, 180, 650);
        double s2 = minScore(dy, 26);
        double s3 = minScore(glass, 55);
        double s4 = inRangeScore(tallness, 0.90, 2.50);
        double conf = 0.18*s1 + 0.26*s2 + 0.28*s3 + 0.28*s4;
        return new Recognition(Variant.WATERFALLS, clamp01(conf));
    }

    private Recognition scoreSpiral(int footprint, int dy, int panes, int glass, double tallness) {
        double s1 = inRangeScore(footprint, 140, 520);
        double s2 = minScore(dy, 34);
        double s3 = minScore(glass, 60);
        double s4 = minScore(tallness, 1.10);
        double conf = 0.18*s1 + 0.30*s2 + 0.28*s3 + 0.24*s4;
        return new Recognition(Variant.SPIRAL, clamp01(conf));
    }

    private void announceVariant(String variantName, double confidence) {
        if (currentLobbyId == null) return;

        LinkedHashSet<String> found = lobbyToFoundVariants.computeIfAbsent(currentLobbyId, k -> new LinkedHashSet<>());
        if (!found.add(variantName)) return;

        hardLog("§dGROTTO FOUND§r | lobby=" + currentLobbyId + " | variant=§b" + variantName + "§r | conf=" + String.format(Locale.ROOT, "%.2f", confidence));

        List<String> list = new ArrayList<>(found);
        if (list.size() == 1) hardLog("Grotto 1/2: §b" + list.get(0) + "§r");
        else hardLog("Grotto 1: §b" + list.get(0) + "§r, Grotto 2: §b" + list.get(1) + "§r");
    }

    // ---------------- Save / Load ----------------
    private void load() {
        knownLobbyIds.clear();
        uniqueLobbyCount = 0;

        try {
            Files.createDirectories(savePath.getParent());
            if (!Files.exists(savePath)) {
                save();
                return;
            }

            String json = Files.readString(savePath, StandardCharsets.UTF_8);
            if (json == null || json.isBlank()) return;

            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj == null) return;

            if (obj.has("uniqueLobbyCount")) uniqueLobbyCount = obj.get("uniqueLobbyCount").getAsInt();
            if (obj.has("knownLobbyIds") && obj.get("knownLobbyIds").isJsonArray()) {
                obj.getAsJsonArray("knownLobbyIds").forEach(e -> knownLobbyIds.add(e.getAsString()));
            }

            uniqueLobbyCount = Math.max(uniqueLobbyCount, knownLobbyIds.size());

            dbgKeyVal(DebugLevel.BASIC, "LOAD",
                "path", savePath.toAbsolutePath().toString(),
                "uniqueLobbyCount", String.valueOf(uniqueLobbyCount),
                "knownLobbyIds", String.valueOf(knownLobbyIds.size())
            );
        } catch (Exception e) {
            hardLog("§cLoad failed§r: " + e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(savePath.getParent());

            JsonObject obj = new JsonObject();
            obj.addProperty("uniqueLobbyCount", uniqueLobbyCount);

            var arr = new com.google.gson.JsonArray();
            ArrayList<String> ids = new ArrayList<>(knownLobbyIds);
            Collections.sort(ids);
            for (String id : ids) arr.add(id);
            obj.add("knownLobbyIds", arr);

            Files.writeString(savePath, GSON.toJson(obj), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

            dbgKeyVal(DebugLevel.BASIC, "SAVE",
                "path", savePath.toAbsolutePath().toString(),
                "uniqueLobbyCount", String.valueOf(uniqueLobbyCount),
                "knownLobbyIds", String.valueOf(knownLobbyIds.size())
            );
        } catch (IOException e) {
            hardLog("§cSave failed§r: " + e.getMessage());
        }
    }
}
