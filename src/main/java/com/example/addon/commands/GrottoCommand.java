package com.example.addon.commands;

import com.example.addon.modules.Hypixel.GrottoFinder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;

public class GrottoCommand extends Command {
    public GrottoCommand() {
        super("grotto", "Manage GrottoFinder lobby IDs.", "grotto-finder");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("clear").executes(ctx -> {
            GrottoFinder m = Modules.get().get(GrottoFinder.class);
            if (m == null) {
                info("GrottoFinder module not found.");
                return SINGLE_SUCCESS;
            }
            m.commandClearLobbyIds();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("save").executes(ctx -> {
            GrottoFinder m = Modules.get().get(GrottoFinder.class);
            if (m == null) {
                info("GrottoFinder module not found.");
                return SINGLE_SUCCESS;
            }
            m.commandSaveCurrentLobbyId();
            return SINGLE_SUCCESS;
        }));
    }
}
