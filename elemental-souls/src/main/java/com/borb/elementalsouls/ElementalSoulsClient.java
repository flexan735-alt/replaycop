package com.borb.elementalsouls;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

public final class ElementalSoulsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(BridgeSyncPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (payload.remove()) {
                    BridgeRegistry.remove(payload.id());
                } else {
                    BridgeRegistry.upsert(payload.id(), payload.dimension(), payload.x(), payload.y(),
                        payload.z(), payload.dx(), payload.dz(), payload.water(), payload.age());
                }
            })
        );

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BridgeRegistry.clear());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) {
                BridgeRegistry.clear();
                return;
            }
            BridgeRegistry.tickClient();
            renderParticleBridges(client);
        });
    }

    private static void renderParticleBridges(Minecraft client) {
        if (client.level == null || client.level.getGameTime() % 4 != 0) return;
        String currentDimension = client.level.dimension().location().toString();

        for (BridgeRegistry.BridgeData bridge : BridgeRegistry.snapshot()) {
            if (!bridge.dimension.equals(currentDimension) || bridge.age >= 640) continue;

            double amount = bridge.age < 100
                ? bridge.age / 100.0
                : bridge.age < 600 ? 1.0 : Math.max(0.0, (640.0 - bridge.age) / 40.0);
            int segments = bridge.age < 100
                ? Math.max(1, (int) Math.ceil(25.0 * amount))
                : bridge.age < 600
                    ? 25
                    : (int) Math.floor(25.0 * amount);

            ParticleOptions deck = bridge.water ? ParticleTypes.BUBBLE : ParticleTypes.FLAME;
            ParticleOptions spark = bridge.water ? ParticleTypes.SPLASH : ParticleTypes.SOUL_FIRE_FLAME;
            ParticleOptions rope = bridge.water ? ParticleTypes.DRIPPING_WATER : ParticleTypes.END_ROD;
            ParticleOptions trim = bridge.water ? ParticleTypes.END_ROD : ParticleTypes.SOUL;

            double px = -bridge.dz;
            double pz = bridge.dx;
            double fadeDrift = bridge.age >= 600 ? 0.055 : 0.006;

            for (int i = 0; i <= segments; i++) {
                double t = i / 25.0;
                double cx = bridge.x + bridge.dx * i;
                double cz = bridge.z + bridge.dz * i;
                double cy = bridge.surfaceAt(i);
                double flow = Math.sin(bridge.age * 0.17 + i * 0.75) * 0.016;

                // Broad, layered particle deck; no block states are placed.
                for (int lane = -4; lane <= 4; lane += 2) {
                    double side = lane * 0.60;
                    double wx = cx + px * side;
                    double wz = cz + pz * side;
                    double vy = bridge.age >= 600 ? fadeDrift + Math.abs(flow) : (bridge.age < 100 ? 0.018 : 0.004);
                    spawn(client, (lane == 0 || Math.abs(lane) == 4) ? spark : deck,
                        wx, cy + 0.025, wz,
                        px * flow + bridge.dx * fadeDrift, vy, pz * flow + bridge.dz * fadeDrift);

                    if (i % 4 == 0 && lane == 0) {
                        spawn(client, trim, wx, cy + 0.12, wz, 0.0, 0.025, 0.0);
                    }
                }

                // Twin particle ropes with a gentle catenary sag.
                double cableY = cy + 1.34 - 0.22 * Math.sin(Math.PI * t);
                for (int sideSign : new int[]{-1, 1}) {
                    double side = sideSign * 2.55;
                    double ropeX = cx + px * side;
                    double ropeZ = cz + pz * side;
                    double twist = Math.sin(bridge.age * 0.13 + i * 1.8) * 0.025;
                    spawn(client, rope, ropeX + px * twist, cableY, ropeZ + pz * twist,
                        px * flow, bridge.age >= 600 ? fadeDrift : 0.004, pz * flow);

                    // Decorative rope posts every three blocks.
                    if (i % 3 == 0) {
                        for (int post = 0; post <= 3; post++) {
                            double postY = cy + 0.18 + (cableY - cy - 0.18) * post / 3.0;
                            spawn(client, (post == 3) ? trim : spark,
                                ropeX, postY, ropeZ, px * flow, 0.008, pz * flow);
                        }
                    }
                }

                // Floating Japanese-style torii arches, made of particle streams only.
                if (i > 0 && i < 25 && i % 5 == 0) {
                    for (int span = -4; span <= 4; span += 2) {
                        double u = span / 4.0;
                        double side = u * 2.48;
                        double archY = cy + 1.78 + 0.70 * (1.0 - u * u);
                        spawn(client, trim, cx + px * side, archY, cz + pz * side,
                            px * flow, 0.01, pz * flow);
                    }
                }

                // Ember/splash lantern clusters along both shoulders.
                if (i > 0 && i < 25 && i % 5 == 0) {
                    for (int sideSign : new int[]{-1, 1}) {
                        double side = sideSign * 2.12;
                        double lx = cx + px * side;
                        double lz = cz + pz * side;
                        spawn(client, trim, lx, cy + 1.02, lz, 0.0, 0.018, 0.0);
                        spawn(client, spark, lx, cy + 1.24, lz, 0.0, 0.035, 0.0);
                    }
                }
            }

            // A twisting spiral of energy builds the next section; it reverses into a dissolve.
            if (bridge.age < 100) {
                int head = Math.min(25, segments);
                double headT = head / 25.0;
                double hx = bridge.x + bridge.dx * head;
                double hz = bridge.z + bridge.dz * head;
                double hy = bridge.surfaceAt(head);
                for (int k = 0; k < 8; k++) {
                    double a = bridge.age * 0.32 + k * Math.PI / 4.0;
                    double r = 0.35 + 0.10 * Math.sin(bridge.age * 0.24 + k);
                    double sx = hx + bridge.dx * (0.25 * Math.cos(a)) + px * r * Math.cos(a);
                    double sz = hz + bridge.dz * (0.25 * Math.cos(a)) + pz * r * Math.cos(a);
                    double sy = hy + 0.10 + k * 0.13;
                    spawn(client, spark, sx, sy, sz,
                        px * Math.cos(a) * 0.035, 0.045, pz * Math.sin(a) * 0.035);
                }
            }

            if (bridge.age >= 636) {
                double t = Math.max(0.0, Math.min(1.0, (bridge.age - 636) / 4.0));
                for (int k = 0; k < 10; k++) {
                    double a = k * Math.PI / 5.0 + bridge.age;
                    double side = Math.cos(a) * (1.0 + t);
                    double forward = Math.sin(a) * (1.0 + t);
                    spawn(client, spark, bridge.x + bridge.dx * 12.5 + px * side,
                        bridge.y + 1.65 + 0.3 * t,
                        bridge.z + bridge.dz * 12.5 + pz * side,
                        bridge.dx * forward * 0.08, 0.06, bridge.dz * forward * 0.08);
                }
            }
        }
    }

    private static void spawn(Minecraft client, ParticleOptions particle,
                              double x, double y, double z,
                              double vx, double vy, double vz) {
        if (client.level != null) {
            client.level.addParticle(particle, x, y, z, vx, vy, vz);
        }
    }
}
