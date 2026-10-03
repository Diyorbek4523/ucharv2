package uz.uchar;

import java.util.UUID;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

/**
 * Survival'da uchish (creative'dagidek: Space'ni ikki marta bosib uchasiz).
 *
 * Bir kishilik dunyoda: o'yinning ichki serverida uchish qobiliyati yoqiladi.
 * Serverda (masalan, Aternos): uchish faqat sizning tomoningizda yoqiladi.
 * Server sizni kick qilmasligi uchun unda allow-flight yoqilgan bo'lishi kerak.
 */
public class UcharClient implements ClientModInitializer {
	/** O'yindagi standart uchish tezligi 0.05. */
	private static final float BASE_SPEED = 0.05f;
	private static final float[] SPEEDS = {1f, 2f, 3f, 5f};

	private static KeyMapping toggleKey;
	private static KeyMapping speedKey;
	private static KeyMapping portalKey;
	private static KeyMapping clipKey;

	/** Devordan o'tishda eng uzoq masofa (blok). */
	private static final int MAX_CLIP = 4;

	private static boolean enabled = false;
	private static int speedIndex = 1;

	@Override
	public void onInitializeClient() {
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.uchar.toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_G, "key.categories.uchar"));
		speedKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.uchar.speed", InputConstants.Type.KEYSYM, InputConstants.KEY_H, "key.categories.uchar"));

		portalKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.uchar.portal", InputConstants.Type.KEYSYM, InputConstants.KEY_P, "key.categories.uchar"));

		clipKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.uchar.clip", InputConstants.Type.KEYSYM, InputConstants.KEY_V, "key.categories.uchar"));

		ClientTickEvents.END_CLIENT_TICK.register(UcharClient::onTick);
	}

	private static float speed() {
		return BASE_SPEED * SPEEDS[speedIndex];
	}

	private static void onTick(Minecraft client) {
		while (toggleKey.consumeClick()) {
			if (client.player == null) {
				continue;
			}

			enabled = !enabled;

			if (enabled) {
				message(client, "§aUchish YONIQ §7(Space'ni ikki marta bosing)");
			} else {
				turnOff(client);
				message(client, "§cUchish O'CHIQ");
			}
		}

		while (speedKey.consumeClick()) {
			speedIndex = (speedIndex + 1) % SPEEDS.length;
			message(client, "§eUchish tezligi: " + (int) SPEEDS[speedIndex] + "x");
		}

		while (portalKey.consumeClick()) {
			findPortal(client);
		}

		while (clipKey.consumeClick()) {
			clip(client);
		}

		if (!enabled) {
			return;
		}

		// Dunyodan chiqqanda avtomatik o'chadi.
		if (client.player == null || client.getConnection() == null) {
			enabled = false;
			return;
		}

		if (client.hasSingleplayerServer()) {
			turnOnSingleplayer(client);
		} else {
			turnOnMultiplayer(client);
		}
	}

	// ---------- Bir kishilik dunyo ----------

	/**
	 * Har tikda tekshiradi: o'lgandan keyin yoki Nether/End'ga o'tganda
	 * o'yin qobiliyatlarni qayta tiklaydi, shuning uchun ularni yana yoqamiz.
	 */
	private static void turnOnSingleplayer(Minecraft client) {
		IntegratedServer server = client.getSingleplayerServer();
		UUID id = client.player.getUUID();
		float speed = speed();

		server.execute(() -> {
			ServerPlayer sp = server.getPlayerList().getPlayer(id);

			if (sp == null || sp.isCreative() || sp.isSpectator()) {
				return;
			}

			Abilities a = sp.getAbilities();

			if (!a.mayfly || a.getFlyingSpeed() != speed) {
				a.mayfly = true;
				a.setFlyingSpeed(speed);
				sp.onUpdateAbilities();
			}
		});
	}

	// ---------- Server (Aternos va boshqalar) ----------

	private static void turnOnMultiplayer(Minecraft client) {
		LocalPlayer player = client.player;

		if (player.isCreative() || player.isSpectator()) {
			return;
		}

		// Server o'lim yoki dunyo almashganda qobiliyatlarni qaytaradi, shuning uchun har tikda yoqamiz.
		Abilities a = player.getAbilities();
		a.mayfly = true;
		a.setFlyingSpeed(speed());

		if (a.flying) {
			// Serverga "yerdaman" deb aytamiz: shunda uchib bo'lib qo'nganda
			// server yiqilish masofasini hisoblamaydi va jon kamaymaydi.
			client.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(true));
		}
	}

	// ---------- O'chirish ----------

	private static void turnOff(Minecraft client) {
		if (client.player == null) {
			return;
		}

		if (client.hasSingleplayerServer()) {
			IntegratedServer server = client.getSingleplayerServer();
			UUID id = client.player.getUUID();

			server.execute(() -> {
				ServerPlayer sp = server.getPlayerList().getPlayer(id);

				if (sp == null || sp.isCreative() || sp.isSpectator()) {
					return;
				}

				Abilities a = sp.getAbilities();
				a.mayfly = false;
				a.flying = false;
				a.setFlyingSpeed(BASE_SPEED);
				sp.resetFallDistance();
				sp.onUpdateAbilities();
			});
		} else {
			LocalPlayer player = client.player;

			if (player.isCreative() || player.isSpectator()) {
				return;
			}

			Abilities a = player.getAbilities();
			a.mayfly = false;
			a.flying = false;
			a.setFlyingSpeed(BASE_SPEED);
		}
	}

	// ---------- Devordan o'tish (tajriba) ----------

	/**
	 * Qarab turgan tomoningizdagi 1-3 blokli devor ortidagi bo'sh joyga o'tkazadi.
	 * Pastga qarasangiz (polga) pastga, tepaga qarasangiz tepaga o'tadi.
	 * Diqqat: server buni rad etib, sizni orqaga qaytarishi mumkin.
	 */
	private static void clip(Minecraft client) {
		LocalPlayer player = client.player;

		if (player == null || client.level == null || client.getConnection() == null) {
			return;
		}

		int sx;
		int sy;
		int sz;

		if (player.getXRot() > 60) {
			sx = 0; sy = -1; sz = 0;
		} else if (player.getXRot() < -60) {
			sx = 0; sy = 1; sz = 0;
		} else {
			Direction facing = player.getDirection();
			sx = facing.getStepX(); sy = 0; sz = facing.getStepZ();
		}

		AABB box = player.getBoundingBox();
		boolean wall = false;

		for (int d = 1; d <= MAX_CLIP; d++) {
			double dy = sy * d;
			AABB moved = box.move(sx * d, dy, sz * d);

			if (!client.level.noCollision(player, moved)) {
				wall = true;
				continue;
			}

			if (!wall) {
				message(client, "§7Oldingizda devor yo'q");
				return;
			}

			double x = player.getX() + sx * d;
			double y = player.getY() + dy;
			double z = player.getZ() + sz * d;

			player.setPos(x, y, z);
			client.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y, z, player.onGround()));
			message(client, "§bDevordan o'tildi");
			return;
		}

		message(client, "§cDevor juda qalin (" + (MAX_CLIP - 1) + " blokdan ko'p)");
	}

	// ---------- End portalini qidirish ----------

	/**
	 * Yuklangan chunk'lar ichidan eng yaqin End portal ramkasini topadi
	 * va uning koordinatasi, masofasi va yo'nalishini chatga yozadi.
	 */
	private static void findPortal(Minecraft client) {
		ClientLevel level = client.level;
		LocalPlayer player = client.player;

		if (level == null || player == null) {
			return;
		}

		int radius = Math.min(client.options.renderDistance().get(), 16);
		int pcx = player.blockPosition().getX() >> 4;
		int pcz = player.blockPosition().getZ() >> 4;
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;

		for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
			for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
				if (!level.hasChunk(cx, cz)) {
					continue;
				}

				LevelChunk chunk = level.getChunk(cx, cz);
				LevelChunkSection[] sections = chunk.getSections();

				for (int i = 0; i < sections.length; i++) {
					LevelChunkSection section = sections[i];

					// Tez tekshiruv: bu bo'lakda portal ramkasi umuman bormi?
					if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(Blocks.END_PORTAL_FRAME))) {
						continue;
					}

					int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));

					for (int x = 0; x < 16; x++) {
						for (int y = 0; y < 16; y++) {
							for (int z = 0; z < 16; z++) {
								if (!section.getBlockState(x, y, z).is(Blocks.END_PORTAL_FRAME)) {
									continue;
								}

								int bx = (cx << 4) + x;
								int by = baseY + y;
								int bz = (cz << 4) + z;
								double d = player.distanceToSqr(bx + 0.5, by + 0.5, bz + 0.5);

								if (d < bestDist) {
									bestDist = d;
									best = new BlockPos(bx, by, bz);
								}
							}
						}
					}
				}
			}
		}

		if (best == null) {
			player.displayClientMessage(Component.literal("§7Yaqin atrofda End portali topilmadi. Ender ko'zi ko'rsatgan tomonga uchib, yana P ni bosing."), false);
			return;
		}

		int dist = (int) Math.sqrt(bestDist);
		String dir = direction(best.getX() + 0.5 - player.getX(), best.getZ() + 0.5 - player.getZ());
		player.displayClientMessage(Component.literal("§dEnd portali topildi! §fX: " + best.getX() + "  Y: " + best.getY() + "  Z: " + best.getZ()
				+ " §7(" + dist + " blok, " + dir + ")"), false);
	}

	/** Kompas yo'nalishi: shimol = -Z, janub = +Z, sharq = +X, g'arb = -X. */
	private static String direction(double dx, double dz) {
		double angle = Math.toDegrees(Math.atan2(dx, -dz));

		if (angle < 0) {
			angle += 360;
		}

		String[] names = {"shimolda", "shimoli-sharqda", "sharqda", "janubi-sharqda", "janubda", "janubi-g'arbda", "g'arbda", "shimoli-g'arbda"};
		return names[(int) Math.round(angle / 45.0) % 8];
	}

	private static void message(Minecraft client, String text) {
		if (client.player != null) {
			client.player.displayClientMessage(Component.literal(text), true);
		}
	}
}
