package com.mocapstudio;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class RecoMod implements ModInitializer {
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
    private static final Map<UUID, Snapshot> ACTIVE = new HashMap<>();
    private static final int MAX_BLOCKS = 200_000;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("reco")
            .executes(ctx -> toggle(ctx.getSource()))
            .then(Commands.literal("pos1").executes(ctx -> setCorner(ctx.getSource(), true)))
            .then(Commands.literal("pos2").executes(ctx -> setCorner(ctx.getSource(), false)))
            .then(Commands.literal("cancel").executes(ctx -> cancel(ctx.getSource()))));

        dispatcher.register(Commands.literal("fly")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("on").executes(ctx -> setFlight(ctx.getSource(), true)))
            .then(Commands.literal("off").executes(ctx -> setFlight(ctx.getSource(), false))));
    }

    private static int setFlight(CommandSourceStack source, boolean enabled) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            player.getAbilities().mayfly = enabled;
            if (!enabled) {
                player.getAbilities().flying = false;
            }
            player.onUpdateAbilities();
            source.sendSuccess(() -> Component.literal(enabled
                ? "Flight enabled. Double-tap jump to fly."
                : "Flight disabled."), false);
            return 1;
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Run this command in-game as a player."));
            return 0;
        }
    }

    private static int setCorner(CommandSourceStack source, boolean first) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            Selection selection = SELECTIONS.computeIfAbsent(player.getUUID(), id -> new Selection());
            if (first) selection.pos1 = player.blockPosition().immutable();
            else selection.pos2 = player.blockPosition().immutable();
            source.sendSuccess(() -> Component.literal((first ? "Arena corner 1" : "Arena corner 2")
                + " set to " + player.blockPosition().toShortString() + "."), false);
            return 1;
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Run this command in-game as a player."));
            return 0;
        }
    }

    private static int toggle(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            UUID id = player.getUUID();
            Snapshot existing = ACTIVE.remove(id);
            if (existing != null) {
                restore(existing);
                source.sendSuccess(() -> Component.literal("Arena restored. Block changes in the recorded area have been undone."), false);
                return 1;
            }

            Selection selection = SELECTIONS.get(id);
            BlockPos a;
            BlockPos b;
            if (selection != null && selection.pos1 != null && selection.pos2 != null) {
                a = selection.pos1;
                b = selection.pos2;
            } else {
                BlockPos p = player.blockPosition();
                a = p.offset(-16, -8, -16);
                b = p.offset(16, 8, 16);
            }

            int minX = Math.min(a.getX(), b.getX());
            int minY = Math.max(player.level().getMinY(), Math.min(a.getY(), b.getY()));
            int minZ = Math.min(a.getZ(), b.getZ());
            int maxX = Math.max(a.getX(), b.getX());
            int maxY = Math.min(player.level().getMaxY() - 1, Math.max(a.getY(), b.getY()));
            int maxZ = Math.max(a.getZ(), b.getZ());
            long count = (long)(maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            if (count <= 0 || count > MAX_BLOCKS) {
                source.sendFailure(Component.literal("Arena is too large (" + count + " blocks). Select an area of at most " + MAX_BLOCKS + " blocks."));
                return 0;
            }

            ServerLevel level = (ServerLevel) player.level();
            Map<BlockPos, BlockState> states = new LinkedHashMap<>((int) count);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        states.put(pos, level.getBlockState(pos));
                    }
                }
            }
            ACTIVE.put(id, new Snapshot(level, states));
            source.sendSuccess(() -> Component.literal("Arena snapshot saved (" + states.size()
                + " blocks). Do your recording, then run /reco again to restore it."), false);
            return 1;
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Could not start Reco. Run this command in-game."));
            return 0;
        }
    }

    private static int cancel(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            Snapshot snapshot = ACTIVE.remove(player.getUUID());
            if (snapshot == null) {
                source.sendFailure(Component.literal("No active Reco snapshot."));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("Reco snapshot discarded; no blocks were restored."), false);
            return 1;
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Run this command in-game as a player."));
            return 0;
        }
    }

    private static void restore(Snapshot snapshot) {
        for (Map.Entry<BlockPos, BlockState> entry : snapshot.states.entrySet()) {
            snapshot.level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
    }

    private static final class Selection {
        private BlockPos pos1;
        private BlockPos pos2;
    }

    private static final class Snapshot {
        private final ServerLevel level;
        private final Map<BlockPos, BlockState> states;

        private Snapshot(ServerLevel level, Map<BlockPos, BlockState> states) {
            this.level = level;
            this.states = states;
        }
    }
}
