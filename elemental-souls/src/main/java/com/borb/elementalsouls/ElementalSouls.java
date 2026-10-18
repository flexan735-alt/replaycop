package com.borb.elementalsouls;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ElementalSouls implements ModInitializer {
    private static final Map<UUID, SoulType> SOULS = new HashMap<>();
    private static final Map<UUID, Bridge> BRIDGES = new HashMap<>();
    private static final double[][] BODY_POINTS = {
        {0.00, 1.78, 0.00}, {-0.10, 1.72, 0.00}, {0.10, 1.72, 0.00},
        {0.00, 1.48, 0.00}, {-0.22, 1.42, 0.00}, {0.22, 1.42, 0.00},
        {-0.34, 1.25, 0.00}, {0.34, 1.25, 0.00}, {-0.40, 1.07, 0.00},
        {0.40, 1.07, 0.00}, {0.00, 1.25, 0.00}, {0.00, 1.06, 0.00},
        {0.00, 0.88, 0.00}, {-0.14, 0.82, 0.00}, {0.14, 0.82, 0.00},
        {-0.14, 0.62, 0.00}, {0.14, 0.62, 0.00}, {-0.15, 0.40, 0.00},
        {0.15, 0.40, 0.00}, {-0.17, 0.12, 0.02}, {0.17, 0.12, 0.02}
    };
    private static long tickCounter;

    private enum SoulType { FIRE, WATER }

    private static final class Bridge {
        private final UUID id;
        private final ServerLevel world;
        private final double x, y, z, dx, dz;
        private final boolean water;
        private int age;

        private Bridge(ServerLevel world, ServerPlayer player, boolean water) {
            this.id = player.getUUID();
            this.world = world;
            this.water = water;
            double yaw = Math.toRadians(player.getYRot());
            this.dx = -Math.sin(yaw);
            this.dz = Math.cos(yaw);
            // Put the start directly under the caster so the first step is supported.
            this.x = player.getX() + dx * 0.25;
            this.y = player.getY();
            this.z = player.getZ() + dz * 0.25;
        }

        private double surface(double t) {
            double u = Math.max(0.0, Math.min(1.0, t));
            return y + Math.sin(Math.PI * u) * 1.65;
        }

        private String dimension() {
            return world.dimension().location().toString();
        }

        private BridgeSyncPayload payload(boolean remove) {
            return new BridgeSyncPayload(id, dimension(), x, y, z, dx, dz, water, age, remove);
        }
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(BridgeSyncPayload.TYPE, BridgeSyncPayload.CODEC);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(ElementalSouls::tick);
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("soul")
            .then(Commands.literal("fire").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                setSoul(player, SoulType.FIRE);
                context.getSource().sendSuccess(() -> Component.literal("🔥 You became a Fire Soul."), false);
                return 1;
            }))
            .then(Commands.literal("water").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                setSoul(player, SoulType.WATER);
                context.getSource().sendSuccess(() -> Component.literal("🌊 You became a Water Soul."), false);
                return 1;
            }))
            .then(Commands.literal("off").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                SOULS.remove(player.getUUID());
                player.removeEffect(MobEffects.INVISIBILITY);
                player.removeEffect(MobEffects.FIRE_RESISTANCE);
                context.getSource().sendSuccess(() -> Component.literal("Your elemental morph has ended."), false);
                return 1;
            }))
            .then(Commands.literal("bridge")
                .then(Commands.literal("fire").executes(context -> summonBridge(context.getSource(), false)))
                .then(Commands.literal("water").executes(context -> summonBridge(context.getSource(), true))))
        );
    }

    private static int summonBridge(CommandSourceStack source, boolean water) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        UUID owner = player.getUUID();

        Bridge previous = BRIDGES.remove(owner);
        if (previous != null) removeBridge(previous);

        Bridge bridge = new Bridge((ServerLevel) player.level(), player, water);
        BRIDGES.put(owner, bridge);
        BridgeRegistry.upsert(owner, bridge.dimension(), bridge.x, bridge.y, bridge.z,
            bridge.dx, bridge.dz, bridge.water, bridge.age);
        broadcastBridge(bridge, false);

        source.sendSuccess(() -> Component.literal(water
            ? "🌊 Particle bridge summoned! Watch it build, then cross safely."
            : "🔥 Particle bridge summoned! Watch it build, then cross safely."), false);
        return 1;
    }

    private static void broadcastBridge(Bridge bridge, boolean remove) {
        BridgeSyncPayload payload = bridge.payload(remove);
        for (ServerPlayer viewer : bridge.world.players()) {
            ServerPlayNetworking.send(viewer, payload);
        }
    }

    private static void removeBridge(Bridge bridge) {
        BridgeRegistry.remove(bridge.id);
        broadcastBridge(bridge, true);
    }

    private static void setSoul(ServerPlayer player, SoulType type) {
        SOULS.put(player.getUUID(), type);
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false, false));
        if (type == SoulType.FIRE) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, false, false, false));
        } else {
            player.removeEffect(MobEffects.FIRE_RESISTANCE);
        }
    }

    private static void tick(MinecraftServer server) {
        tickCounter++;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            SoulType soul = SOULS.get(player.getUUID());
            if (soul == null) continue;

            if (tickCounter % 20 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false, false));
                if (soul == SoulType.FIRE) {
                    player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, false, false, false));
                    player.clearFire();
                }
            }
            emitSoulBody(player, soul);
        }

        Iterator<Map.Entry<UUID, Bridge>> iterator = BRIDGES.entrySet().iterator();
        while (iterator.hasNext()) {
            Bridge bridge = iterator.next().getValue();
            bridge.age++;

            if (bridge.age >= 640) {
                removeBridge(bridge);
                iterator.remove();
                continue;
            }

            BridgeRegistry.setAge(bridge.id, bridge.age);
        }
    }

    private static void emitSoulBody(ServerPlayer player, SoulType soul) {
        ServerLevel world = (ServerLevel) player.level();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > 0.0025;
        // 3 particles/tick while moving (was 24); 5 while still, keeping a readable silhouette.
        int amount = moving ? 3 : 5;
        for (int i = 0; i < amount; i++) {
            int index = Math.floorMod((int) (tickCounter * amount + i * 7L), BODY_POINTS.length);
            double[] point = BODY_POINTS[index];
            double jitter = moving ? 0.045 : 0.065;
            double px = player.getX() + point[0] + random.nextDouble(-jitter, jitter);
            double py = player.getY() + point[1] + random.nextDouble(-jitter, jitter);
            double pz = player.getZ() + point[2] + random.nextDouble(-jitter, jitter);

            ParticleOptions particle;
            if (soul == SoulType.FIRE) {
                particle = random.nextInt(6) == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
            } else {
                int pick = random.nextInt(4);
                particle = pick == 0 ? ParticleTypes.SPLASH
                    : pick == 1 ? ParticleTypes.DRIPPING_WATER
                    : pick == 2 ? ParticleTypes.BUBBLE
                    : ParticleTypes.END_ROD;
            }
            world.sendParticles(particle, px, py, pz, 1, 0.015, 0.025, 0.015, 0.002);
        }
    }
}
