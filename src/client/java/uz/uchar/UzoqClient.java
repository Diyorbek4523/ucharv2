package uz.uchar;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

/**
 * R - uzoqroqdan urish (5.5 blok).
 * B - yerda tez yurish, N - tezlikni almashtirish.
 * K - avtomatik urish, L - kimni urish: moblar / o'yinchilar / hammasi.
 *
 * Server o'yinchidan 6 blokdan (3 + 3) uzoqdagi zarbalarni qabul qilmaydi,
 * shuning uchun urish masofasi biroz kamroq — 5.5 blok — olingan.
 */
public class UzoqClient implements ClientModInitializer {
	/** O'yindagi standart urish masofasi (blok). */
	private static final double BASE_REACH = 3.0;
	private static final double LONG_REACH = 5.5;

	/** O'yindagi standart yurish tezligi. */
	private static final double BASE_SPEED = 0.10000000149011612;
	private static final double[] SPEEDS = {1.5, 2.0, 3.0};

	private static KeyMapping reachKey;
	private static KeyMapping speedToggleKey;
	private static KeyMapping speedLevelKey;
	private static KeyMapping auraKey;
	private static KeyMapping auraModeKey;

	private static boolean reachEnabled = false;
	private static boolean speedEnabled = false;
	private static int speedIndex = 1;
	private static boolean auraEnabled = false;
	/** 0 - faqat moblar, 1 - faqat o'yinchilar, 2 - hammasi. */
	private static int auraMode = 0;
	private static final String[] AURA_MODES = {"faqat moblar", "faqat o'yinchilar", "moblar va o'yinchilar"};

	@Override
	public void onInitializeClient() {
		reachKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.uzoq.toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_R, "key.categories.uchar"));
		speedToggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.uzoq.speed", InputConstants.Type.KEYSYM, InputConstants.KEY_B, "key.categories.uchar"));
		speedLevelKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.uzoq.speedlevel", InputConstants.Type.KEYSYM, InputConstants.KEY_N, "key.categories.uchar"));

		auraKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.uzoq.aura", InputConstants.Type.KEYSYM, InputConstants.KEY_K, "key.categories.uchar"));

		auraModeKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.uzoq.auramode", InputConstants.Type.KEYSYM, InputConstants.KEY_L, "key.categories.uchar"));

		ClientTickEvents.END_CLIENT_TICK.register(UzoqClient::onTick);
	}

	private static void onTick(Minecraft client) {
		while (reachKey.consumeClick()) {
			reachEnabled = !reachEnabled;
			message(client, reachEnabled ? "§aUzoqdan urish YONIQ §7(" + LONG_REACH + " blok)" : "§cUzoqdan urish O'CHIQ");
		}

		while (speedToggleKey.consumeClick()) {
			speedEnabled = !speedEnabled;
			message(client, speedEnabled ? "§aTez yurish YONIQ §7(" + SPEEDS[speedIndex] + "x)" : "§cTez yurish O'CHIQ");
		}

		while (speedLevelKey.consumeClick()) {
			speedIndex = (speedIndex + 1) % SPEEDS.length;
			message(client, "§eYurish tezligi: " + SPEEDS[speedIndex] + "x");
		}

		while (auraKey.consumeClick()) {
			auraEnabled = !auraEnabled;
			message(client, auraEnabled ? "§aAvtomatik urish YONIQ §7(" + AURA_MODES[auraMode] + ")" : "§cAvtomatik urish O'CHIQ");
		}

		while (auraModeKey.consumeClick()) {
			auraMode = (auraMode + 1) % AURA_MODES.length;
			message(client, "§eAvtomatik urish: " + AURA_MODES[auraMode]);
		}

		LocalPlayer player = client.player;

		if (player == null) {
			return;
		}

		if (auraEnabled) {
			aura(client, player);
		}

		// Server o'lim yoki dunyo almashganda qiymatlarni qaytarishi mumkin, shuning uchun har tikda tekshiramiz.
		setBase(player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE), reachEnabled ? LONG_REACH : BASE_REACH);
		setBase(player.getAttribute(Attributes.MOVEMENT_SPEED), speedEnabled ? BASE_SPEED * SPEEDS[speedIndex] : BASE_SPEED);
	}

	/**
	 * Yaqin atrofdagi eng yaqin dushman mobni uradi.
	 * Faqat qurol to'liq "zaryadlanganda" uradi, shunda har bir zarba to'liq kuch bilan tushadi.
	 * Neytral moblarga (Enderman, Piglin va h.k.) hech qachon tegmaydi.
	 */
	private static void aura(Minecraft client, LocalPlayer player) {
		if (client.screen != null || client.level == null || client.gameMode == null) {
			return;
		}

		Entity target = null;
		double best = Double.MAX_VALUE;

		for (Entity e : client.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity living) || !living.isAlive() || e == player) {
				continue;
			}

			if (!isTarget(e)) {
				continue;
			}

			// Urish masofasi o'yinchining hozirgi masofasiga teng (R yoqilsa, 5.5 blok).
			if (!player.canInteractWithEntity(e, 0.0) || !player.hasLineOfSight(e)) {
				continue;
			}

			double d = player.distanceToSqr(e);

			if (d < best) {
				best = d;
				target = e;
			}
		}

		if (target == null) {
			return;
		}

		// Qo'lga eng kuchli qilichni olamiz. Qurol almashganda o'yin zaryadni
		// boshidan boshlaydi, shuning uchun bu tikda urmaymiz - zaryad to'lishini kutamiz.
		if (selectBestWeapon(player)) {
			return;
		}

		if (player.getAttackStrengthScale(0f) < 1f) {
			return;
		}

		client.gameMode.attack(player, target);
		player.swing(InteractionHand.MAIN_HAND);
	}

	/**
	 * Hotbar'dan eng kuchli qurolni tanlaydi: avval qilichlar orasidan,
	 * qilich bo'lmasa - eng ko'p zarar beradigan buyum (masalan, bolta).
	 * Qurol almashtirilgan bo'lsa, true qaytaradi.
	 */
	private static boolean selectBestWeapon(LocalPlayer player) {
		Inventory inv = player.getInventory();
		int bestSlot = inv.selected;
		double bestScore = weaponScore(inv.getItem(inv.selected));

		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			double score = weaponScore(inv.getItem(slot));

			if (score > bestScore) {
				bestScore = score;
				bestSlot = slot;
			}
		}

		if (bestSlot != inv.selected) {
			inv.selected = bestSlot;
			return true;
		}

		return false;
	}

	/** Qurol bahosi: qilichlar har doim boshqa buyumlardan ustun turadi. */
	private static double weaponScore(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}

		double damage = 0;
		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

		for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
			if (entry.attribute().equals(Attributes.ATTACK_DAMAGE)) {
				damage += entry.modifier().amount();
			}
		}

		return stack.getItem() instanceof SwordItem ? 100 + damage : damage;
	}

	private static boolean isTarget(Entity e) {
		if (e instanceof Player p) {
			// Creative yoki spectator rejimidagilarni urib bo'lmaydi.
			return auraMode != 0 && !p.isCreative() && !p.isSpectator();
		}

		if (auraMode == 1) {
			return false;
		}

		return e instanceof Enemy && !(e instanceof NeutralMob) && !(e instanceof AbstractPiglin);
	}

	private static void setBase(AttributeInstance attribute, double value) {
		if (attribute != null && attribute.getBaseValue() != value) {
			attribute.setBaseValue(value);
		}
	}

	private static void message(Minecraft client, String text) {
		if (client.player != null) {
			client.player.displayClientMessage(Component.literal(text), true);
		}
	}
}
