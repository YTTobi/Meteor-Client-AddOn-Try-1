package com.example.addon;

import com.example.addon.commands.CommandExample;
import com.example.addon.hud.HudExample;
import com.example.addon.modules.DonutSMP.ChunkFinder;
import com.example.addon.modules.DonutSMP.CrystalMacro;
import com.example.addon.modules.DonutSMP.KelpESP;
import com.example.addon.modules.DonutSMP.ModuleExample;
import com.example.addon.modules.Hypixel.CoordinatesToChat;
import com.example.addon.modules.Hypixel.DungeonRooms;
import com.example.addon.modules.Hypixel.MiningMacro;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category DONUTSMP = new Category("DonutSMP");
    public static final Category HYPIXEL = new Category("Hypixel");
    public static final HudGroup HUD_GROUP = new HudGroup("DonutSMP/Hypixel");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Meteor Addon Template");

        // Modules
        Modules.get().add(new ModuleExample());
        Modules.get().add(new KelpESP());
        Modules.get().add(new ChunkFinder());
        Modules.get().add(new CrystalMacro());
        Modules.get().add(new MiningMacro());
        Modules.get().add(new CoordinatesToChat());
        Modules.get().add(new DungeonRooms());

        // Commands
        Commands.add(new CommandExample());

        // HUD
        Hud.get().register(HudExample.INFO);

    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(DONUTSMP);
        Modules.registerCategory(HYPIXEL);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("MeteorDevelopment", "meteor-addon-template");
    }
}
