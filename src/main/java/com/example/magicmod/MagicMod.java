package com.example.magicmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MagicMod implements ModInitializer {
	public static final String MOD_ID = "magicmod";

	public static final int ROLL = 0;
	public static final int DASH = 1;

	/** Длительность действия (тики). */
	public static final int[] DURATION = {12, 6};
	/** Кулдаун (тики). */
	public static final int[] COOLDOWN = {22, 14};
	/** Сколько тиков с начала переката игрок неуязвим (i-frames). */
	public static final int ROLL_IFRAMES = 8;

	private static final Map<UUID, Long> NEXT_ALLOWED = new HashMap<>();
	private static final Map<UUID, Long> INVULN_UNTIL = new HashMap<>();

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.playC2S().register(ActionC2SPayload.ID, ActionC2SPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ActionS2CPayload.ID, ActionS2CPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ActionC2SPayload.ID, (payload, context) ->
				handleAction(context.player(), payload.type(),
						Mth.clamp(payload.fwd(), -1, 1), Mth.clamp(payload.side(), -1, 1)));

		// Неуязвимость во время переката (кроме урона, пробивающего неуязвимость: бездна, /kill)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				Long until = INVULN_UNTIL.get(player.getUUID());
				return until == null || player.level().getGameTime() >= until;
			}
			return true;
		});
	}

	private static void handleAction(ServerPlayer player, int type, int fwd, int side) {
		if (type != ROLL && type != DASH) return;
		if (player.isSpectator() || !player.isAlive()) return;

		long now = player.level().getGameTime();
		Long next = NEXT_ALLOWED.get(player.getUUID());
		if (next != null && now < next) return;

		NEXT_ALLOWED.put(player.getUUID(), now + COOLDOWN[type]);
		if (type == ROLL) {
			INVULN_UNTIL.put(player.getUUID(), now + ROLL_IFRAMES);
			player.fallDistance = 0; // перекат гасит падение
		}

		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
				type == ROLL ? 0.8f : 0.9f, type == ROLL ? 0.7f : 1.4f);

		// Анимацию рассылаем игрокам, которые нас видят (сам игрок запускает её локально)
		ActionS2CPayload out = new ActionS2CPayload(player.getId(), type, fwd, side);
		for (ServerPlayer other : PlayerLookup.tracking(player)) {
			ServerPlayNetworking.send(other, out);
		}
	}
}
