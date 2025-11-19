package com.example.addon.modules.DonutSMP;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.entity.EntityType;

import java.util.Set;

public class CrystalMacro extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();


    private final Setting<Boolean> placeObsidian = sgGeneral.add(new BoolSetting.Builder()
        .name("autoObi")
        .description("Automatically places obi obsidian")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> CrystalDelay = sgGeneral.add(new IntSetting.Builder()
        .name("CrystalDelay")
        .description("The delay in wich the crystal explode until placing")
        .defaultValue(0)
        .min(0)
        .max(10)
        .sliderMax(10)
        .build()
    );

    private final Setting<Boolean> stopOnKill = sgGeneral.add(new BoolSetting.Builder()
        .name("StopOnKill")
        .description("Stops on Kill")
        .defaultValue(false)
        .build()
    );

    private final Setting<Set<EntityType<?>>> targets = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("Crystal Targets")
        .description("Targets That get killed upon sight")
        .defaultValue(Set.of(EntityType.PLAYER))
        .build()
    );

    public CrystalMacro() {
        super(AddonTemplate.DONUTSMP, "Crystal-Macro" , "Juts like Killaura but with crystals");
    }

    private void onTick(TickEvent.Post event) {
        if (placeObsidian.get()) {

        }
    }

    private void onRender(Render3DEvent event) {

    }
}
