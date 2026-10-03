package uz.uchar;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

/**
 * U - Elytra boost: elytra bilan uchayotganda W tugmasini bosib tursangiz,
 * xuddi feyerverk ishlatgandek qarab turgan tomoningizga tezlashasiz.
 * Hisob-kitob o'yindagi feyerverk kuchi bilan bir xil qilingan.
 */
public class ElytraClient implements ClientModInitializer {
	private static final double[] SPEEDS = {1.0, 1.5, 2.0};
	private static final String[] SPEED_NAMES = {"1x", "1.5x", "2x"};

	private static KeyMapping toggleKey;
	private static KeyMapping speedKey;
	private static boolean enabled = true;
	private static int speedIndex = 0;

	@Override
	public void onInitializeClient() {
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.uchar.elytra", InputConstants.Type.KEYSYM, InputConstants.KEY_U, "key.categories.uchar"));
		speedKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.uchar.elytraspeed", InputConstants.Type.KEYSYM, InputConstants.KEY_I, "key.categories.uchar"));

		ClientTickEvents.END_CLIENT_TICK.register(ElytraClient::onTick);
	}

	private static void onTick(Minecraft client) {
		while (toggleKey.consumeClick()) {
			enabled = !enabled;
			message(client, enabled ? "§aElytra boost YONIQ §7(W ni bosib turing)" : "§cElytra boost O'CHIQ");
		}

		while (speedKey.consumeClick()) {
			speedIndex = (speedIndex + 1) % SPEEDS.length;
			message(client, "§eElytra tezligi: " + SPEED_NAMES[speedIndex]);
		}

		LocalPlayer player = client.player;

		if (!enabled || player == null || !player.isFallFlying() || !client.options.keyUp.isDown()) {
			return;
		}

		// Feyerverk formulasi: har tikda qarab turgan tomonga tezlashtiradi.
		double max = 1.5 * SPEEDS[speedIndex];
		Vec3 look = player.getLookAngle();
		Vec3 v = player.getDeltaMovement();

		player.setDeltaMovement(v.add(
				look.x * 0.1 + (look.x * max - v.x) * 0.5,
				look.y * 0.1 + (look.y * max - v.y) * 0.5,
				look.z * 0.1 + (look.z * max - v.z) * 0.5));
	}

	private static void message(Minecraft client, String text) {
		if (client.player != null) {
			client.player.displayClientMessage(Component.literal(text), true);
		}
	}
}
