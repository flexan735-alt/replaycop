package com.mocapstudio.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class MocapStudioClient implements ClientModInitializer {
	public static KeyMapping OPEN;

	@Override
	public void onInitializeClient() {
		OPEN = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.mocapstudio.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, KeyMapping.Category.MISC));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (OPEN.consumeClick()) {
				if (mc.player != null && mc.screen == null) mc.setScreen(new DashboardScreen());
			}
		});
	}
}
