package com.borb.elementalsouls;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record BridgeSyncPayload(
    UUID id,
    String dimension,
    double x, double y, double z,
    double dx, double dz,
    boolean water,
    int age,
    boolean remove
) implements CustomPacketPayload {
    public static final Type<BridgeSyncPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("elemental_souls", "bridge_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BridgeSyncPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), BridgeSyncPayload::decode);

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(dimension);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeDouble(dx);
        buf.writeDouble(dz);
        buf.writeBoolean(water);
        buf.writeVarInt(age);
        buf.writeBoolean(remove);
    }

    private static BridgeSyncPayload decode(RegistryFriendlyByteBuf buf) {
        return new BridgeSyncPayload(
            buf.readUUID(),
            buf.readUtf(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readBoolean(),
            buf.readVarInt(),
            buf.readBoolean()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
