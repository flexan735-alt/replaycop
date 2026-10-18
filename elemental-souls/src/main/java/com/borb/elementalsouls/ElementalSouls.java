package com.borb.elementalsouls;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ElementalSouls implements ModInitializer {
    private static final Map<UUID, SoulType> SOULS = new HashMap<>();
    private static final Map<UUID, Bridge> BRIDGES = new HashMap<>();
    private static long tickCounter;

    private enum SoulType { FIRE, WATER }

    private static final class Bridge {
        private final ServerLevel world;
        private final double x, y, z, dx, dz;
        private final boolean water;
        private int age;

        private Bridge(ServerLevel world, ServerPlayer player, boolean water) {
            this.world = world;
            this.water = water;
            double yaw = Math.toRadians(player.getYRot());
            this.dx = -Math.sin(yaw);
            this.dz = Math.cos(yaw);
            this.x = player.getX() + dx * 1.5;
            this.y = player.getY();
            this.z = player.getZ() + dz * 1.5;
        }

        private double surface(double t) {
            return y + Math.sin(Math.PI * t) * 1.65;
        }
    }

    @Override
    public void onInitialize() {
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
        BRIDGES.put(player.getUUID(), new Bridge(player.serverLevel(), player, water));
        source.sendSuccess(() -> Component.literal(water
            ? "🌊 Water Bridge summoned! It forms from particles, stays for 25 seconds, then dissolves."
            : "🔥 Fire Bridge summoned! It forms from particles, stays for 25 seconds, then dissolves."), false);
        return 1;
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
            if (soul != null) {
                if (tickCounter % 20 == 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false, false));
                    if (soul == SoulType.FIRE) {
                        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, false, false, false));
                        player.clearFire();
                    }
                }
                emitSoulBody(player, soul);
            }

            for (Bridge bridge : BRIDGES.values()) {
                if (bridge.world == player.serverLevel() && bridge.age >= 100 && bridge.age < 600) {
                    supportPlayerOnBridge(player, bridge);
                }
            }
        }

        Iterator<Map.Entry<UUID, Bridge>> iterator = BRIDGES.entrySet().iterator();
        while (iterator.hasNext()) {
            Bridge bridge = iterator.next().getValue();
            bridge.age++;
            if (bridge.age > 640) {
                burstAndRemove(bridge);
                iterator.remove();
                continue;
            }
            if (bridge.age % 2 == 0) drawBridge(bridge);
            if (bridge.age == 600) burstAndRemove(bridge);
        }
    }

    private static void emitSoulBody(ServerPlayer player, SoulType soul) {
        ServerLevel world = player.serverLevel();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 24; i++) {
            double h = random.nextDouble(0.08, 1.92);
            double radius = h < 0.35 ? 0.22 : (h > 1.55 ? 0.20 : 0.34);
            double angle = random.nextDouble(Math.PI * 2.0);
            double px = player.getX() + Math.cos(angle) * radius;
            double pz = player.getZ() + Math.sin(angle) * radius;
            double py = player.getY() + h;
            ParticleOptions particle;
            if (soul == SoulType.FIRE) {
                particle = random.nextInt(5) == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
            } else {
                int pick = random.nextInt(3);
                particle = pick == 0 ? ParticleTypes.SPLASH
                    : pick == 1 ? ParticleTypes.DRIPPING_WATER
                    : ParticleTypes.BUBBLE;
            }
            world.sendParticles(particle, px, py, pz, 1, 0.035, 0.045, 0.035, 0.005);
        }
    }

    private static void drawBridge(Bridge bridge) {
        double progress = bridge.age < 100 ? bridge.age / 100.0 : 1.0;
        if (bridge.age >= 600) progress = Math.max(0.0, 1.0 - (bridge.age - 600) / 40.0);
        int segments = Math.max(1, (int) Math.ceil(25.0 * progress));
        ParticleOptions deck = bridge.water ? ParticleTypes.BUBBLE : ParticleTypes.FLAME;
        ParticleOptions accent = bridge.water ? ParticleTypes.SPLASH : ParticleTypes.SOUL_FIRE_FLAME;
        ParticleOptions rail = bridge.water ? ParticleTypes.DRIPPING_WATER : ParticleTypes.FLAME;
        double px = -bridge.dz;
        double pz = bridge.dx;

        for (int i = 0; i <= segments; i++) {
            double t = i / 25.0;
            double cx = bridge.x + bridge.dx * i;
            double cz = bridge.z + bridge.dz * i;
            double cy = bridge.surface(t);

            for (int lane = -1; lane <= 1; lane++) {
                double side = lane * 0.82;
                bridge.world.sendParticles(deck, cx + px * side, cy + 0.035, cz + pz * side,
                    1, 0.025, 0.025, 0.025, 0.0);
            }

            for (int sideSign : new int[]{-1, 1}) {
                double side = sideSign * 1.28;
                bridge.world.sendParticles(rail, cx + px * side, cy + 0.72, cz + pz * side,
                    1, 0.025, 0.025, 0.025, 0.0);
                if (i % 2 == 0) {
                    for (int post = 1; post <= 4; post++) {
                        bridge.world.sendParticles(accent, cx + px * side, cy + post * 0.14, cz + pz * side,
                            1, 0.01, 0.02, 0.01, 0.0);
                    }
                }
            }

            if (i % 3 == 0) {
                bridge.world.sendParticles(accent, cx, cy + 0.14, cz, 2, 0.18, 0.05, 0.18, 0.01);
            }
        }
    }

    private static void supportPlayerOnBridge(ServerPlayer player, Bridge bridge) {
        double rx = player.getX() - bridge.x;
        double rz = player.getZ() - bridge.z;
        double along = rx * bridge.dx + rz * bridge.dz;
        double side = rx * -bridge.dz + rz * bridge.dx;
        if (along < 0.0 || along > 25.0 || Math.abs(side) > 1.18) return;

        double surfaceY = bridge.surface(along / 25.0);
        Vec3 velocity = player.getDeltaMovement();
        double feetY = player.getY();
        if (velocity.y <= 0.08 && feetY <= surfaceY + 0.25 && feetY >= surfaceY - 1.65) {
            player.setPos(player.getX(), surfaceY, player.getZ());
            player.setDeltaMovement(velocity.x, 0.0, velocity.z);
            player.fallDistance = 0.0F;
            player.setOnGround(true);
            if (!bridge.water) player.clearFire();
        }
    }

    private static void burstAndRemove(Bridge bridge) {
        ParticleOptions burst = bridge.water ? ParticleTypes.SPLASH : ParticleTypes.FLAME;
        for (int i = 0; i <= 25; i++) {
            double t = i / 25.0;
            double x = bridge.x + bridge.dx * i;
            double z = bridge.z + bridge.dz * i;
            double y = bridge.surface(t);
            bridge.world.sendParticles(burst, x, y + 0.2, z, 8, 0.35, 0.2, 0.35, 0.04);
        }
    }
}
