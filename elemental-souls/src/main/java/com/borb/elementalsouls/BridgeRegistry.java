package com.borb.elementalsouls;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BridgeRegistry {
    private static final Map<UUID, BridgeData> BRIDGES = new ConcurrentHashMap<>();
    private static final double HALF_WIDTH = 2.48;
    private static final int BUILD_TICKS = 100;
    private static final int FADE_START = 600;
    private static final int END_TICKS = 640;

    private BridgeRegistry() { }

    public static final class BridgeData {
        public final UUID id;
        public final String dimension;
        public final double x, y, z, dx, dz;
        public final boolean water;
        public volatile int age;

        private BridgeData(UUID id, String dimension, double x, double y, double z,
                           double dx, double dz, boolean water, int age) {
            this.id = id;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dx = dx;
            this.dz = dz;
            this.water = water;
            this.age = age;
        }

        public double surfaceAt(double along) {
            double t = Math.max(0.0, Math.min(1.0, along / 25.0));
            return y + Math.sin(Math.PI * t) * 1.65;
        }
    }

    public static void upsert(UUID id, String dimension, double x, double y, double z,
                              double dx, double dz, boolean water, int age) {
        BRIDGES.put(id, new BridgeData(id, dimension, x, y, z, dx, dz, water, age));
    }

    public static void setAge(UUID id, int age) {
        BridgeData bridge = BRIDGES.get(id);
        if (bridge != null) bridge.age = age;
    }

    public static void remove(UUID id) {
        BRIDGES.remove(id);
    }

    public static Collection<BridgeData> snapshot() {
        return new ArrayList<>(BRIDGES.values());
    }

    public static void tickClient() {
        for (BridgeData bridge : BRIDGES.values()) {
            bridge.age++;
            if (bridge.age >= END_TICKS) BRIDGES.remove(bridge.id, bridge);
        }
    }

    public static void clear() {
        BRIDGES.clear();
    }

    public static Vec3 adjustMovement(Entity entity, Vec3 movement) {
        if (!(entity instanceof Player)) return movement;

        String dimension = entity.level().dimension().identifier().toString();
        double currentY = entity.getY();
        double destinationX = entity.getX() + movement.x;
        double destinationY = currentY + movement.y;
        double destinationZ = entity.getZ() + movement.z;

        for (BridgeData bridge : BRIDGES.values()) {
            if (!bridge.dimension.equals(dimension) || bridge.age < BUILD_TICKS || bridge.age >= END_TICKS) continue;

            double rx = destinationX - bridge.x;
            double rz = destinationZ - bridge.z;
            double along = rx * bridge.dx + rz * bridge.dz;
            double side = rx * -bridge.dz + rz * bridge.dx;

            // Wide particle deck: 4.96 blocks wide, with a small edge tolerance for the player's hitbox.
            if (along < -0.55 || along > 25.55 || Math.abs(side) > HALF_WIDTH) continue;

            double surfaceY = bridge.surfaceAt(along);
            if (movement.y <= 0.12 && destinationY <= surfaceY + 0.12 && currentY >= surfaceY - 0.65) {
                double correctedY = surfaceY - currentY;
                if (correctedY <= 0.65 && correctedY >= -4.25) {
                    return new Vec3(movement.x, correctedY, movement.z);
                }
            }
        }
        return movement;
    }
}
