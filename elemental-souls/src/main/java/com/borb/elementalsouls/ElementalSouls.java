package com.borb.elementalsouls;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static net.minecraft.server.command.CommandManager.literal;

public final class ElementalSouls implements ModInitializer {
    private static final Map<UUID, SoulType> SOULS = new HashMap<>();
    private static final Map<UUID, Bridge> BRIDGES = new HashMap<>();
    private static long tickCounter;

    private enum SoulType {
        FIRE, WATER
    }

    private static final class Bridge {
        private final ServerWorld world;
        private final double x;
        private final double y;
        private final double z;
        private final double dx;
        private final double dz;
        private final boolean water;
        private int age;

        private Bridge(ServerWorld world, ServerPlayerEntity player, boolean water) {
            this.world = world;
            this.water = water;
            double yaw = Math.toRadians(player.getYaw());
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

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("soul")
            .then(literal("fire").executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                setSoul(player, SoulType.FIRE);
                context.getSource().sendFeedback(() -> Text.literal("🔥 You became a Fire Soul."), false);
                return 1;
            }))
            .then(literal("water").executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                setSoul(player, SoulType.WATER);
                context.getSource().sendFeedback(() -> Text.literal("🌊 You became a Water Soul."), false);
                return 1;
            }))
            .then(literal("off").executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                SOULS.remove(player.getUuid());
                player.removeStatusEffect(StatusEffects.INVISIBILITY);
                player.removeStatusEffect(StatusEffects.FIRE_RESISTANCE);
                context.getSource().sendFeedback(() -> Text.literal("Your elemental morph has ended."), false);
                return 1;
            }))
            .then(literal("bridge")
                .then(literal("fire").executes(context -> summonBridge(context.getSource(), false)))
                .then(literal("water").executes(context -> summonBridge(context.getSource(), true))))
        );
    }

    private static int summonBridge(ServerCommandSource source, boolean water) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        BRIDGES.put(player.getUuid(), new Bridge(player.getServerWorld(), player, water));
        source.sendFeedback(() -> Text.literal(water
            ? "🌊 Water Bridge summoned! It forms from particles, stays for 25 seconds, then dissolves."
            : "🔥 Fire Bridge summoned! It forms from particles, stays for 25 seconds, then dissolves."), false);
        return 1;
    }

    private static void setSoul(ServerPlayerEntity player, SoulType type) {
        SOULS.put(player.getUuid(), type);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 60, 0, false, false, false));
        if (type == SoulType.FIRE) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 60, 0, false, false, false));
        } else {
            player.removeStatusEffect(StatusEffects.FIRE_RESISTANCE);
        }
    }

    private static void tick(MinecraftServer server) {
        tickCounter++;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            SoulType soul = SOULS.get(player.getUuid());
            if (soul != null) {
                if (tickCounter % 20 == 0) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 60, 0, false, false, false));
                    if (soul == SoulType.FIRE) {
                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 60, 0, false, false, false));
                        player.setFireTicks(0);
                    }
                }
                emitSoulBody(player, soul);
            }

            for (Bridge bridge : BRIDGES.values()) {
                if (bridge.world == player.getServerWorld() && bridge.age >= 100 && bridge.age < 600) {
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
            if (bridge.age % 2 == 0) {
                drawBridge(bridge);
            }
            if (bridge.age == 600) {
                burstAndRemove(bridge);
            }
        }
    }

    private static void emitSoulBody(ServerPlayerEntity player, SoulType soul) {
        ServerWorld world = player.getServerWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 24; i++) {
            double h = random.nextDouble(0.08, 1.92);
            double radius = h < 0.35 ? 0.22 : (h > 1.55 ? 0.20 : 0.34);
            double angle = random.nextDouble(Math.PI * 2.0);
            double px = player.getX() + Math.cos(angle) * radius;
            double pz = player.getZ() + Math.sin(angle) * radius;
            double py = player.getY() + h;
            ParticleEffect particle;
            if (soul == SoulType.FIRE) {
                particle = random.nextInt(5) == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
            } else {
                int pick = random.nextInt(4);
                particle = pick == 0 ? ParticleTypes.SPLASH
                    : pick == 1 ? ParticleTypes.DRIPPING_WATER
                    : ParticleTypes.BUBBLE;
            }
            world.spawnParticles(particle, px, py, pz, 1, 0.035, 0.045, 0.035, 0.005);
        }
    }

    private static void drawBridge(Bridge bridge) {
        double progress = bridge.age < 100 ? bridge.age / 100.0 : 1.0;
        if (bridge.age >= 600) {
            progress = Math.max(0.0, 1.0 - (bridge.age - 600) / 40.0);
        }
        int segments = Math.max(1, (int) Math.ceil(25.0 * progress));
        ParticleEffect deck = bridge.water ? ParticleTypes.BUBBLE : ParticleTypes.FLAME;
        ParticleEffect accent = bridge.water ? ParticleTypes.SPLASH : ParticleTypes.SOUL_FIRE_FLAME;
        ParticleEffect rail = bridge.water ? ParticleTypes.DRIPPING_WATER : ParticleTypes.FLAME;
        double px = -bridge.dz;
        double pz = bridge.dx;

        for (int i = 0; i <= segments; i++) {
            double t = i / 25.0;
            double cx = bridge.x + bridge.dx * i;
            double cz = bridge.z + bridge.dz * i;
            double cy = bridge.surface(t);

            for (int lane = -1; lane <= 1; lane++) {
                double side = lane * 0.82;
                bridge.world.spawnParticles(deck, cx + px * side, cy + 0.035, cz + pz * side,
                    1, 0.025, 0.025, 0.025, 0.0);
            }

            for (int sideSign : new int[]{-1, 1}) {
                double side = sideSign * 1.28;
                bridge.world.spawnParticles(rail, cx + px * side, cy + 0.72, cz + pz * side,
                    1, 0.025, 0.025, 0.025, 0.0);
                if (i % 2 == 0) {
                    for (int post = 1; post <= 4; post++) {
                        bridge.world.spawnParticles(accent,
                            cx + px * side, cy + post * 0.14, cz + pz * side,
                            1, 0.01, 0.02, 0.01, 0.0);
                    }
                    bridge.world.spawnParticles(accent,
                        cx + px * side * 0.5, cy + 0.18, cz + pz * side * 0.5,
                        1, 0.02, 0.02, 0.02, 0.0);
                }
            }

            if (i % 3 == 0) {
                bridge.world.spawnParticles(accent, cx, cy + 0.14, cz, 2, 0.18, 0.05, 0.18, 0.01);
            }
        }
    }

    private static void supportPlayerOnBridge(ServerPlayerEntity player, Bridge bridge) {
        double rx = player.getX() - bridge.x;
        double rz = player.getZ() - bridge.z;
        double along = rx * bridge.dx + rz * bridge.dz;
        double side = rx * -bridge.dz + rz * bridge.dx;
        if (along < 0.0 || along > 25.0 || Math.abs(side) > 1.18) {
            return;
        }

        double surfaceY = bridge.surface(along / 25.0);
        Vec3d velocity = player.getVelocity();
        double feetY = player.getY();
        if (velocity.y <= 0.08 && feetY <= surfaceY + 0.25 && feetY >= surfaceY - 1.65) {
            player.setPosition(player.getX(), surfaceY, player.getZ());
            player.setVelocity(velocity.x, 0.0, velocity.z);
            player.fallDistance = 0.0F;
            player.setOnGround(true);
            if (!bridge.water) {
                player.setFireTicks(0);
            }
        }
    }

    private static void burstAndRemove(Bridge bridge) {
        ParticleEffect burst = bridge.water ? ParticleTypes.SPLASH : ParticleTypes.FLAME;
        for (int i = 0; i <= 25; i++) {
            double t = i / 25.0;
            double x = bridge.x + bridge.dx * i;
            double z = bridge.z + bridge.dz * i;
            double y = bridge.surface(t);
            bridge.world.spawnParticles(burst, x, y + 0.2, z, 8, 0.35, 0.2, 0.35, 0.04);
        }
    }
}
