package uz.uchar;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

/**
 * J - Auto Tool: blok qazishni boshlaganingizda qo'lingizga eng mos asbobni
 * (kirka, bolta, belkurak va h.k.) avtomatik oladi. Qazishni to'xtatganda
 * avvalgi slotga qaytaradi. Sinishiga oz qolgan asboblarni ishlatmaydi.
 */
public class AutoToolClient implements ClientModInitializer {
	/** Chidamliligi shundan kam qolgan asbob tanlanmaydi (sinib qolmasligi uchun). */
	private static final int MIN_DURABILITY_LEFT = 10;

	private static KeyMapping toggleKey;
	private static boolean enabled = true;
	/** Qazishdan oldin tanlangan slot (-1 - hech narsa almashtirilmagan). */
	private static int previousSlot = -1;

	@Override
	public void onInitializeClient() {
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.uchar.autotool", InputConstants.Type.KEYSYM, InputConstants.KEY_J, "key.categories.uchar"));

		ClientTickEvents.END_CLIENT_TICK.register(AutoToolClient::onTick);
	}

	private static void onTick(Minecraft client) {
		while (toggleKey.consumeClick()) {
			enabled = !enabled;

			if (client.player != null) {
				client.player.displayClientMessage(Component.literal(enabled ? "§aAuto Tool YONIQ" : "§cAuto Tool O'CHIQ"), true);
			}
		}

		LocalPlayer player = client.player;

		if (!enabled || player == null || client.level == null || client.screen != null) {
			previousSlot = -1;
			return;
		}

		Inventory inv = player.getInventory();
		boolean mining = client.options.keyAttack.isDown()
				&& client.hitResult != null
				&& client.hitResult.getType() == HitResult.Type.BLOCK;

		if (!mining) {
			// Qazish tugadi - avvalgi slotga qaytamiz.
			if (previousSlot != -1) {
				inv.selected = previousSlot;
				previousSlot = -1;
			}

			return;
		}

		BlockPos pos = ((BlockHitResult) client.hitResult).getBlockPos();
		BlockState state = client.level.getBlockState(pos);

		if (state.isAir()) {
			return;
		}

		int bestSlot = inv.selected;
		float bestSpeed = speed(inv.getItem(inv.selected), state);

		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			float s = speed(inv.getItem(slot), state);

			if (s > bestSpeed) {
				bestSpeed = s;
				bestSlot = slot;
			}
		}

		if (bestSlot != inv.selected) {
			if (previousSlot == -1) {
				previousSlot = inv.selected;
			}

			inv.selected = bestSlot;
		}
	}

	/** Asbobning bu blokni qazish tezligi. Sinishiga oz qolgan bo'lsa, 0 qaytaradi. */
	private static float speed(ItemStack stack, BlockState state) {
		if (stack.isEmpty()) {
			return 1f;
		}

		if (stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= MIN_DURABILITY_LEFT) {
			return 0f;
		}

		return stack.getDestroySpeed(state);
	}
}
