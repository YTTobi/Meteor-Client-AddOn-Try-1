package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import com.example.addon.modules.Hypixel.MiningMacro;
import meteordevelopment.meteorclient.systems.hud.*;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class MiningHud extends HudElement {
    public static final HudElementInfo<MiningHud> INFO =
        new HudElementInfo<>(
            AddonTemplate.HUD_GROUP,
            "mining-hud",
            "Shows mining macro session stats.",
            MiningHud::new
        );

    public MiningHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        MiningMacro macro = MiningMacro.getInstance();

        int line = 0;
        int lineHeight = (int) renderer.textHeight(true) + 2;

        // === STATUS LINE ===
        boolean active = macro.isMacroActive();
        renderer.text(
            "Mining Macro: " + (active ? "ON" : "OFF"),
            x,
            y + lineHeight * line,
            active ? macro.getHudOnColor() : macro.getHudOffColor(),
            true
        );
        line++;

        // === SESSION TIME ===
        renderer.text(
            "Session: " + macro.getSessionDuration(),
            x,
            y + lineHeight * line,
            Color.WHITE,
            true
        );
        line++;

        // === BLOCKS MINED ===
        renderer.text(
            "Blocks Mined: " + macro.getBlocksMined(),
            x,
            y + lineHeight * line,
            Color.WHITE,
            true
        );
    }
}
