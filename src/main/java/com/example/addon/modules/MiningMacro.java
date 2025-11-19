package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.util.math.BlockPos;


import java.util.ArrayList;
import java.util.List;

public class MiningMacro extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgAim = settings.createGroup("Aim Settings");
    private final SettingGroup sgGem = settings.createGroup("Gemstone Actions");


    // ---------------- Settings ----------------
    private enum Mode { MITHRIL, GEMSTONE, CUSTOM }

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Mithril preset, Gemstone preset, or custom block list.")
        .defaultValue(Mode.MITHRIL)
        .build());

    private final Setting<List<Block>> customBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("custom-blocks")
        .description("Blocks mined when in CUSTOM mode.")
        .defaultValue(new Block[]{Blocks.BEDROCK})
        .visible(() -> mode.get() == Mode.CUSTOM)
        .build());

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("scan-range")
        .description("How far to scan for mineable blocks.")
        .defaultValue(5)
        .min(1)
        .sliderMax(10)
        .build());

    private final Setting<Double> smoothTime = sgGeneral.add(new DoubleSetting.Builder()
        .name("look-smooth-time")
        .defaultValue(0.45)
        .sliderMax(1.0)
        .build());

    private final Setting<Double> maxTurnSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-turn-speed")
        .defaultValue(240.0)
        .sliderMax(720.0)
        .build());

    private final Setting<Integer> reactionDelay = sgGeneral.add(new IntSetting.Builder()
        .name("reaction-delay")
        .description("Time in milliseconds to wait after looking at a block before mining starts.")
        .defaultValue(20)
        .range(0, 150)
        .sliderRange(0, 150)
        .build());


    // ---- render settings ----
    private final Setting<Boolean> renderTracer = sgRender.add(new BoolSetting.Builder()
        .name("render-tracer")
        .description("Draws a line to the block being mined.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> renderBox = sgRender.add(new BoolSetting.Builder()
        .name("render-box")
        .description("Renders a box around the target block.")
        .defaultValue(true)
        .build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the box is rendered.")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<SettingColor> tracerColor = sgRender.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Color of the tracer line.")
        .defaultValue(new SettingColor(0, 0, 255, 255))
        .build());

    private final Setting<SettingColor> boxSideColor = sgRender.add(new ColorSetting.Builder()
        .name("box-side-color")
        .description("Color of the box sides (fill).")
        .defaultValue(new SettingColor(0, 0, 255, 40))
        .build());

    private final Setting<SettingColor> boxLineColor = sgRender.add(new ColorSetting.Builder()
        .name("box-line-color")
        .description("Color of the box outline.")
        .defaultValue(new SettingColor(0, 0, 255, 255))
        .build());

    private final Setting<Boolean> autoSneak = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-sneak")
        .description("Makes the player sneak while mining.")
        .defaultValue(false)
        .build());

    private final Setting<Double> aimJitter = sgAim.add(new DoubleSetting.Builder()
        .name("aim-jitter")
        .description("How much random offset is applied to the aim point.")
        .defaultValue(0.01)
        .range(0.0, 0.2) // min/max
        .sliderRange(0.0, 0.05)
        .build()
    );

    private final Setting<Double> aimTolerance = sgAim.add(new DoubleSetting.Builder()
        .name("aim-tolerance")
        .description("How close your crosshair must be to the block before starting to mine.")
        .defaultValue(3.0)   // degrees
        .range(0.0, 15.0)
        .sliderRange(0.5, 10.0)
        .build()
    );

    private final Setting<Integer> drillSlot = sgGeneral.add(new IntSetting.Builder()
        .name("drill-slot")
        .description("Hotbar slot that contains your drill.")
        .defaultValue(1)
        .range(1, 9)
        .sliderRange(1, 9)
        .build()
    );

    private final Setting<Integer> teleportSlot = sgGeneral.add(new IntSetting.Builder()
        .name("teleport-slot")
        .description("Hotbar slot that contains teleport ability.")
        .defaultValue(2)
        .range(1, 9)
        .sliderRange(1, 9)
        .build()
    );

    private final Setting<List<String>> teleportPoints = sgGeneral.add(
        new StringListSetting.Builder()
            .name("teleport-points")
            .description("Format: Name|x y z|delayMs")
            .defaultValue(new ArrayList<>())
            .build()
    );

    private List<TeleportPoint> getParsedTeleportPoints() {
        List<TeleportPoint> list = new ArrayList<>();
        for (String s : teleportPoints.get()) {
            TeleportPoint tp = parseTeleportPoint(s);
            if (tp != null) list.add(tp);
        }
        return list;
    }


    private final Setting<Boolean> doGemAction = sgGem.add(new BoolSetting.Builder()
        .name("enable-gem-action")
        .description("Runs the special action when all gemstones are gone.")
        .defaultValue(true)
        .build());


    // ---------------- Runtime ----------------
    private List<Block> targetList;
    private BlockPos target;
    private boolean breaking;
    private Direction breakSide = Direction.UP;
    private long sessionStart = 0;
    private boolean ranGemAction = false;
    private int currentTeleportIndex = 0;
    private int tpTimer = 0;
    private long lastTeleportUsed = 0;
    // Gemstone macro state
    private int gemActionTicks = 0;  // countdown for teleport action
    private boolean doingTeleport = false;
    private int teleportTicks = 0;
    private int tpStage = 0;

    private TeleportPoint tpTarget = null;
    private float targetYaw, targetPitch;
    private final FloatRef yawVel = new FloatRef(0);
    private final FloatRef pitchVel = new FloatRef(0);
    private float renderYaw, renderPitch;

    private BlockState lastState;
    private int sameTicks, retryCount;
    private long lookStartTime;
    private BlockPos lastBroken;
    private int ignoreMiningTicks = 0;
    private int lastTeleportDelay = 0;




    // --- Stats ---
    private int brokenBlocks = 0;
    private long lastBreakTime = 0;
    private static final int SPREAD_RADIUS = 2; // how far spread mining can affect


    public MiningMacro() {
        super(AddonTemplate.HYPIXEL, "Smooth Auto Miner",
            "Smoothly mines visible blocks with smart retargeting and render options.");
    }

    // ---------------- Lifecycle ----------------
    @Override
    public void onActivate() {
        if (mc.player == null) return;
        /*sessionStart = System.currentTimeMillis();*/
        ranGemAction = false;
        reset();
        loadPreset();
    }

    @Override
    public void onDeactivate() {
        releaseAttack();
        /*sendStatsToChat();*/
        reset();
        brokenBlocks = 0;
    }


    private void reset() {
        target = null;
        breaking = false;
        sameTicks = retryCount = 0;
        yawVel.v = pitchVel.v = 0;
        if (mc.player != null) {
            renderYaw = targetYaw = mc.player.getYaw();
            renderPitch = targetPitch = mc.player.getPitch();
        }
        if (mc.options.attackKey != null) mc.options.attackKey.setPressed(false);
    }

    private void loadPreset() {
        targetList = new ArrayList<>();
        if (mode.get() == Mode.MITHRIL) {
            targetList.addAll(List.of(
                Blocks.POLISHED_DIORITE, Blocks.LIGHT_BLUE_WOOL,
                Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE,
                Blocks.GRAY_CONCRETE, Blocks.GRAY_WOOL
            ));
        } else if (mode.get() == Mode.GEMSTONE) {
            targetList.addAll(List.of(
                Blocks.GLASS, Blocks.GLASS_PANE,
                Blocks.WHITE_STAINED_GLASS,Blocks.WHITE_STAINED_GLASS_PANE , Blocks.BLUE_STAINED_GLASS, Blocks.BLUE_STAINED_GLASS_PANE ,
                Blocks.GREEN_STAINED_GLASS, Blocks.GREEN_STAINED_GLASS_PANE, Blocks.RED_STAINED_GLASS, Blocks.RED_STAINED_GLASS_PANE,
                Blocks.ORANGE_STAINED_GLASS, Blocks.ORANGE_STAINED_GLASS_PANE, Blocks.BLACK_STAINED_GLASS, Blocks.BLACK_STAINED_GLASS_PANE,
                Blocks.CYAN_STAINED_GLASS, Blocks.CYAN_STAINED_GLASS_PANE, Blocks.PURPLE_STAINED_GLASS, Blocks.PURPLE_STAINED_GLASS_PANE,
                Blocks.MAGENTA_STAINED_GLASS, Blocks.MAGENTA_STAINED_GLASS_PANE, Blocks.LIME_STAINED_GLASS, Blocks.LIME_STAINED_GLASS_PANE,
                Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE
            ));
        } else {
            targetList.addAll(customBlocks.get());
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post e) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        ClientPlayerEntity p = mc.player;

        // ==========================================================
        //   POST TELEPORT WAIT → PREVENT MINING WRONG BLOCKS
        // ==========================================================
        // ======== COOLDOWN after teleport ========
        if (ignoreMiningTicks > 0) {
            ignoreMiningTicks--;
        }

        // ======== FIRST: If teleport is running, handle it and STOP ========
        if (doingTeleport) {
            handleTeleport(p);
            return;        // absolutely NO mining during ANY teleport stage
        }

        loadPreset();
        // ==========================================================
        //   AUTO-SNEAK DURING MINING
        // ==========================================================
        mc.options.sneakKey.setPressed(autoSneak.get());

        // ==========================================================
        //   VALIDATE TARGET OR FIND NEW ONE
        // ==========================================================
        if (target == null || !shouldMine(mc.world.getBlockState(target))) {

            // Find new mining target
            target = findNearest(lastBroken != null ? lastBroken : p.getBlockPos(), range.get());

            // 🟩 If no target exists → TELEPORT
            if (target == null
                && mode.get() == Mode.GEMSTONE
                && teleportPointsExist()
                && ignoreMiningTicks <= 0) {

                TeleportPoint next = getNextTeleportPoint();
                startTeleportAction(next);
                return;
            }

            // Reset mining state
            breaking = false;
            sameTicks = 0;
            lookStartTime = 0;
            return;
        }

        BlockState state = mc.world.getBlockState(target);

        // ==========================================================
        //  BLOCK FINISHED → COUNT + RETARGET
        // ==========================================================
        if (state.isAir() || state.isOf(Blocks.BEDROCK)) {
            brokenBlocks++;
            detectSpreadBreaks(target);

            lastBroken = target;

            // Find next block
            target = findNearest(lastBroken, range.get());

            // 🟩 No more gemstones → TELEPORT
            // No gemstone found -> teleport
            if (target == null
                && mode.get() == Mode.GEMSTONE
                && teleportPointsExist()
                && ignoreMiningTicks <= 0) {

                TeleportPoint next = getNextTeleportPoint();
                startTeleportAction(next);
                return;
            }

            breaking = false;
            releaseAttack();
            return;
        }

        // ==========================================================
        //              AIMING TOWARD TARGET BLOCK
        // ==========================================================
        Vec3d eye = p.getEyePos();
        double j = aimJitter.get();

        Vec3d aim = Vec3d.ofCenter(target).add(
            (p.getRandom().nextDouble() - 0.5) * j,
            (p.getRandom().nextDouble() - 0.5) * j,
            (p.getRandom().nextDouble() - 0.5) * j
        );

        targetYaw = calcYaw(eye, aim);
        targetPitch = calcPitch(eye, aim);
        double dist = eye.distanceTo(aim);

        float yawDiff = Math.abs(normalizeAngle(p.getYaw() - targetYaw));
        float pitchDiff = Math.abs(p.getPitch() - targetPitch);

        boolean lookingAtBlock = yawDiff < 5f && pitchDiff < 5f;

        // ==========================================================
        //         REACTION DELAY BEFORE STARTING MINING
        // ==========================================================
        if (!breaking) {
            if ((isLookingCloseEnough(aim) || lookingAtBlock) && dist <= 5.5 && shouldMine(state)) {
                if (lookStartTime == 0) lookStartTime = System.currentTimeMillis();
                if (System.currentTimeMillis() - lookStartTime < reactionDelay.get()) return;
            } else lookStartTime = 0;
        }

        // ==========================================================
        //                   ACTUAL MINING LOGIC
        // ==========================================================
        if ((isLookingCloseEnough(aim) || breaking) && dist <= 5.5 && shouldMine(state)) {

            breakSide = visibleSide(p, target);

            if (!breaking) {
                mc.interactionManager.attackBlock(target, breakSide);
                p.swingHand(Hand.MAIN_HAND);

                breaking = true;
                lastState = state;
                sameTicks = 0;

                mc.options.attackKey.setPressed(true);
            }
            else {
                mc.interactionManager.updateBlockBreakingProgress(target, breakSide);
                p.swingHand(Hand.MAIN_HAND);
                mc.options.attackKey.setPressed(true);

                if (state.equals(lastState)) sameTicks++;
                else {
                    sameTicks = 0;
                    lastState = state;
                }

                if (sameTicks > 40) {
                    sameTicks = 0;
                    mc.interactionManager.attackBlock(target, breakSide);
                }
            }
        }
        else {
            releaseAttack();
            breaking = false;
        }

        // ==========================================================
        //   RETARGET IF BLOCK TURNS TO AIR
        // ==========================================================
        if (mc.world.getBlockState(target).isAir()) {
            releaseAttack();
            lastBroken = target;
            target = findNearest(lastBroken, range.get());
            breaking = false;
            lookStartTime = 0;
        }
    }


    private boolean teleportPointsExist() {
        return !getParsedTeleportPoints().isEmpty();
    }

    private TeleportPoint getNextTeleportPoint() {
        List<TeleportPoint> list = getParsedTeleportPoints();
        currentTeleportIndex %= list.size();
        return list.get(currentTeleportIndex++);
    }



    // ---------------- Render ----------------
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;
        ClientPlayerEntity p = mc.player;

        float dt = 1f / 20f;
        float smooth = smoothTime.get().floatValue();

        // Slightly increase smoothness when close — but stop exactly on center
        if (target != null) {
            double dist = p.getEyePos().distanceTo(Vec3d.ofCenter(target));
            smooth += (float) Math.min(0.15, 0.6 / (dist + 0.2));
        }

        float desiredYaw = smoothDampAngle(renderYaw, targetYaw, yawVel, smooth,
            maxTurnSpeed.get().floatValue(), dt);
        float desiredPitch = smoothDamp(renderPitch, targetPitch, pitchVel, smooth,
            maxTurnSpeed.get().floatValue(), dt);

        renderYaw = lerpAngle(renderYaw, desiredYaw, event.tickDelta * 0.3f);
        renderPitch = lerp(renderPitch, desiredPitch, event.tickDelta * 0.3f);

        p.setYaw(renderYaw);
        p.setPitch(renderPitch);

        // Tracer / Box rendering
        if (target == null || mc.world == null) return;
        Vec3d eye = mc.player.getEyePos();
        Vec3d center = Vec3d.ofCenter(target);

        if (renderTracer.get()) {
            event.renderer.line(
                eye.x, eye.y, eye.z,
                center.x, center.y, center.z,
                new Color(tracerColor.get())
            );
        }

        if (renderBox.get()) {
            event.renderer.box(
                target,
                new Color(boxSideColor.get()),
                new Color(boxLineColor.get()),
                shapeMode.get(),
                0
            );
        }
    }

    private void handleTeleport(ClientPlayerEntity p) {
        if (tpTarget == null) {
            doingTeleport = false;
            return;
        }

        Vec3d eye = p.getEyePos();

        // Target block center
        Vec3d center = new Vec3d(
            tpTarget.pos.getX() + 0.5,
            tpTarget.pos.getY() + 0.5,
            tpTarget.pos.getZ() + 0.5
        );

        // Calculate desired yaw and pitch
        float desiredYaw = calcYaw(eye, center);
        float desiredPitch = calcPitch(eye, center);

        // Tell render() what to rotate toward
        targetYaw = desiredYaw;
        targetPitch = desiredPitch;

        // How close the camera is to target rotation
        float yawDiff = Math.abs(normalizeAngle(p.getYaw() - desiredYaw));
        float pitchDiff = Math.abs(p.getPitch() - desiredPitch);
        boolean aligned = yawDiff < 2 && pitchDiff < 2;

        // =====================================================
        //                      STAGE 0
        //         Smooth turning (handled in onRender)
        // =====================================================
        if (tpStage == 0) {
            // Wait until smooth turn has completed
            if (aligned) {
                tpStage = 1;
                tpTimer = 6;       // Hold sneak for ~300 ms
            }
            return;
        }

        // =====================================================
        //                      STAGE 1
        //                Hold sneak before teleport
        // =====================================================
        if (tpStage == 1) {
            mc.options.sneakKey.setPressed(true);
            tpTimer--;

            if (tpTimer <= 0) {
                tpStage = 2;
            }
            return;
        }

        // =====================================================
        //                      STAGE 2
        //             Switch to teleport item slot
        // =====================================================
        if (tpStage == 2) {
            p.getInventory().setSelectedSlot(teleportSlot.get() - 1);
            tpStage = 3;
            tpTimer = 4; // small delay before click
            return;
        }

        // =====================================================
        //                      STAGE 3
        //               Perform teleport (right click)
        // =====================================================
        if (tpStage == 3) {
            mc.options.useKey.setPressed(true);
            tpTimer--;

            if (tpTimer <= 0) {
                mc.options.useKey.setPressed(false);
                tpStage = 4;
                tpTimer = 3;
            }
            return;
        }

        // =====================================================
        //                      STAGE 4
        //             Switch back to drill after teleport
        // =====================================================
        if (tpStage == 4) {
            p.getInventory().setSelectedSlot(drillSlot.get() - 1);
            tpStage = 5;
            return;
        }

        // =====================================================
        //                      STAGE 5
        //                Unsneak & finish teleport
        // =====================================================
        if (tpStage == 5) {
            mc.options.sneakKey.setPressed(false);
            doingTeleport = false;

            // apply wait before next teleport or mining
            ignoreMiningTicks = lastTeleportDelay / 50;
            if (ignoreMiningTicks < 10) ignoreMiningTicks = 10;

            tpTarget = null;
        }
    }


    // ---------------- Helpers ----------------
    private boolean shouldMine(BlockState s) {
        if (s == null) return false;
        for (Block b : targetList) if (s.isOf(b)) return true;
        return false;
    }

    private boolean isRoughlyVisible(ClientPlayerEntity player, BlockPos pos) {
        if (player == null || mc.world == null) return false;

        Vec3d from = player.getEyePos();
        Vec3d to = Vec3d.ofCenter(pos);

        var result = mc.world.raycast(new net.minecraft.world.RaycastContext(
            from, to,
            net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
            net.minecraft.world.RaycastContext.FluidHandling.NONE,
            player
        ));

        // true if the ray hit nothing or hit the same block
        return result.getType() == net.minecraft.util.hit.HitResult.Type.MISS
            || (result.getBlockPos() != null && result.getBlockPos().equals(pos));
    }


    private float rotateToward(float current, float target, float speed) {
        float diff = target - current;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;

        float change = Math.max(-speed, Math.min(speed, diff));
        return current + change;
    }

    private void startTeleportAction(TeleportPoint tp) {
        doingTeleport = true;
        tpStage = 0;
        tpTimer = 0;
        tpTarget = tp;

        lastTeleportDelay = tp.delay;
        ignoreMiningTicks = 5;   // prevent instant retrigger
    }


    private void applySneakState(boolean mining) {
        if (autoSneak.get()) {
            mc.options.sneakKey.setPressed(mining); // sneak ONLY while mining
        } else {
            mc.options.sneakKey.setPressed(!mining); // sneak ONLY while teleporting
        }
    }



    private BlockPos findNearest(BlockPos origin, int r) {
        if (mc.world == null) return null;
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVecClient().normalize();

        boolean useLast = lastBroken != null;

        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z > r * r) continue;

                    BlockPos pos = origin.add(x, y, z);
                    BlockState s = mc.world.getBlockState(pos);
                    if (s.isAir() || s.isOf(Blocks.BEDROCK) || !shouldMine(s)) continue;
                    if (!isRoughlyVisible(mc.player, pos)) continue;

                    double score;

                    if (!useLast) {
                        // first target — prefer visible & in front
                        Vec3d blockCenter = Vec3d.ofCenter(pos);
                        double dist = eye.distanceTo(blockCenter);
                        Vec3d dir = blockCenter.subtract(eye).normalize();
                        double facing = Math.max(0.3, look.dotProduct(dir)); // 1 front, 0 behind
                        score = dist / facing;
                    } else {
                        // next target — prefer blocks close to the last one
                        double dist = pos.getSquaredDistance(lastBroken);
                        score = dist;
                    }

                    if (score < bestScore) {
                        bestScore = score;
                        best = pos;
                    }
                }

        return best;
    }

    private static float calcYaw(Vec3d from, Vec3d to) {
        Vec3d diff = to.subtract(from);
        return (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
    }

    private static float calcPitch(Vec3d from, Vec3d to) {
        Vec3d diff = to.subtract(from);
        double dist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        return (float) -Math.toDegrees(Math.atan2(diff.y, dist));
    }

    private static Direction visibleSide(ClientPlayerEntity player, BlockPos pos) {
        Vec3d eye = player.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        Vec3d dir = center.subtract(eye).normalize();

        Direction best = Direction.UP;
        double maxDot = -Double.MAX_VALUE;

        for (Direction d : Direction.values()) {
            Vec3d normal = Vec3d.of(d.getVector());
            double dot = normal.dotProduct(dir);
            if (dot > maxDot) { maxDot = dot; best = d; }
        }
        return best;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float lerpAngle(float a, float b, float t) {
        float delta = (float) (((b - a + 540.0f) % 360.0f) - 180.0f);
        return a + delta * t;
    }

    private static float smoothDamp(float c, float t, FloatRef v, float st, float ms, float dt) {
        if (st < 1e-4f) st = 1e-4f;
        float w = 2f / st;
        float x = w * dt;
        float exp = 1f / (1f + x + 0.48f * x * x + 0.235f * x * x * x);
        float ch = c - t;
        float maxCh = (float) (ms * st);
        ch = clamp(ch, -maxCh, maxCh);
        float tt = c - ch;
        float temp = (v.v + w * ch) * dt;
        v.v = (v.v - w * temp) * exp;
        float o = tt + (ch + temp) * exp;
        if ((t - c > 0) == (o > t)) { o = t; v.v = 0; }
        return o;
    }

    private static float smoothDampAngle(float current, float target, FloatRef velocity,
                                         float smoothTime, float maxSpeed, float deltaTime) {
        float delta = normalizeAngle(target - current);
        float newTarget = current + delta;
        return smoothDamp(current, newTarget, velocity, smoothTime, maxSpeed, deltaTime);
    }

    private static float normalizeAngle(float angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }

    // Detects other blocks nearby that were also broken by mining spread.
    private void detectSpreadBreaks(BlockPos center) {
        if (mc.world == null) return;

        for (int x = -SPREAD_RADIUS; x <= SPREAD_RADIUS; x++) {
            for (int y = -SPREAD_RADIUS; y <= SPREAD_RADIUS; y++) {
                for (int z = -SPREAD_RADIUS; z <= SPREAD_RADIUS; z++) {

                    BlockPos pos = center.add(x, y, z);

                    BlockState s = mc.world.getBlockState(pos);

                    // ⭐ Only count spread if:
                    // 1. It is now AIR
                    // 2. It WAS a target block (shouldMine)
                    if (s.isAir() && shouldMine(s)) {
                        brokenBlocks++;
                    }
                }
            }
        }
    }


    private boolean isLookingCloseEnough(Vec3d target) {
        Vec3d eyes = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVecClient().normalize();

        Vec3d dirToTarget = target.subtract(eyes).normalize();

        double dot = look.dotProduct(dirToTarget);
        double angle = Math.toDegrees(Math.acos(dot));

        return angle <= aimTolerance.get();
    }

    private String getSessionDuration() {
        long ms = System.currentTimeMillis() - sessionStart;

        long sec = (ms / 1000) % 60;
        long min = (ms / (1000 * 60)) % 60;
        long hr  = (ms / (1000 * 60 * 60));

        return String.format("%02dh %02dm %02ds", hr, min, sec);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static class TeleportPoint {
        String name;
        BlockPos pos;
        int delay;

        TeleportPoint(String name, BlockPos pos, int delay) {
            this.name = name;
            this.pos = pos;
            this.delay = delay;
        }
    }

    private TeleportPoint parseTeleportPoint(String entry) {
        try {
            // Format: Name|x y z|delay
            String[] parts = entry.split("\\|");
            if (parts.length < 2) return null;

            String name = parts[0];

            String[] xyz = parts[1].trim().split(" ");
            if (xyz.length != 3) return null;

            int x = Integer.parseInt(xyz[0]);
            int y = Integer.parseInt(xyz[1]);
            int z = Integer.parseInt(xyz[2]);

            int delay = 0;
            if (parts.length == 3) {
                delay = Integer.parseInt(parts[2].trim());
            }

            return new TeleportPoint(name, new BlockPos(x, y, z), delay);

        } catch (Exception e) {
            return null; // ignore invalid entry
        }
    }

    private void releaseAttack() {
        if (mc.options != null && mc.options.attackKey != null)
            mc.options.attackKey.setPressed(false);
    }

    private static final class FloatRef {
        float v;
        FloatRef(float v) { this.v = v; }
    }
}
