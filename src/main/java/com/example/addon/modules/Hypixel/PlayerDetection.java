package com.example.addon.modules.Hypixel;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.systems.modules.Module;

public class PlayerDetection extends Module {

    public PlayerDetection() {
        super(AddonTemplate.HYPIXEL, "PlayerDetection", "Does actions when players are nearby. Could be paired with macros.");
    }
}
