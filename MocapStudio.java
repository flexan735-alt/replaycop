package com.mocapstudio;

import com.mocapstudio.server.Playback;
import com.mocapstudio.server.Recorder;
import com.mocapstudio.server.StudioCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public class MocapStudio implements ModInitializer {
	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, ctx, env) -> StudioCommands.register(dispatcher));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Recorder.tickAll();
			Playback.tickAll();
		});

		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
			if (player instanceof ServerPlayer sp) Recorder.onBreak(sp, pos);
		});

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer sp) {
				ItemStack st = player.getItemInHand(hand);
				if (st.getItem() instanceof BlockItem bi) {
					Recorder.onPlace(sp, hit.getBlockPos().relative(hit.getDirection()), bi.getBlock().defaultBlockState());
				}
			}
			return InteractionResult.PASS;
		});

		// leftover ghosts from a crash / world save are removed on load
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity.getTags().contains("mocapstudio") && !Playback.GHOSTS.contains(entity.getUUID())) {
				world.getServer().execute(entity::discard);
			}
		});
	}
}
