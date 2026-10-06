package com.example.magicmod.client;

import com.example.magicmod.ActionC2SPayload;
import com.example.magicmod.ActionS2CPayload;
import com.example.magicmod.MagicMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class MagicModClient implements ClientModInitializer {
	public record Anim(int type, long start, int fwd, int side) {}

	/** Ключ для передачи данных анимации в render state (используется миксином). */
	public static final RenderStateDataKey<RollRender> ROLL_RENDER = RenderStateDataKey.create(() -> "magicmod roll");

	public static KeyMapping ROLL_KEY;
	public static KeyMapping DASH_KEY;

	/** Счётчик клиентских тиков (для анимации). */
	public static long clientTicks = 0;
	/** Активные анимации: id сущности -> анимация. */
	public static final Map<Integer, Anim> ANIMS = new HashMap<>();

	// Состояние собственного движения
	private static int selfTicksLeft = 0;
	private static int selfType = 0;
	private static double dirX, dirZ;
	private static int cooldown = 0;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, "main"));
		ROLL_KEY = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.magicmod.roll", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, category));
		DASH_KEY = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.magicmod.dash", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, category));

		// Анимации других игроков
		ClientPlayNetworking.registerGlobalReceiver(ActionS2CPayload.ID, (payload, context) -> {
			Minecraft mc = context.client();
			if (mc.level == null) return;
			Entity e = mc.level.getEntity(payload.entityId());
			if (e != null) startAnim(mc.level, e, payload.type(), payload.fwd(), payload.side());
		});

		ClientTickEvents.END_CLIENT_TICK.register(MagicModClient::tick);
	}

	private static void tick(Minecraft mc) {
		clientTicks++;
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		if (level == null || player == null) {
			ANIMS.clear();
			selfTicksLeft = 0;
			return;
		}

		// Чистим закончившиеся анимации + шлейф частиц
		Iterator<Map.Entry<Integer, Anim>> it = ANIMS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Anim> en = it.next();
			Anim a = en.getValue();
			if (clientTicks - a.start() > MagicMod.DURATION[a.type()] + 1) {
				it.remove();
				continue;
			}
			Entity e = level.getEntity(en.getKey());
			if (e == null) continue;
			ParticleOptions fx = a.type() == MagicMod.DASH ? ParticleTypes.END_ROD : ParticleTypes.CLOUD;
			for (int i = 0; i < 2; i++) {
				level.addParticle(fx,
						e.getX() + (level.random.nextDouble() - 0.5) * 0.6,
						e.getY() + (a.type() == MagicMod.DASH ? 0.9 : 0.15) + level.random.nextDouble() * 0.3,
						e.getZ() + (level.random.nextDouble() - 0.5) * 0.6,
						0, 0.01, 0);
			}
		}

		if (cooldown > 0) cooldown--;

		while (ROLL_KEY.consumeClick()) tryStart(mc, player, MagicMod.ROLL);
		while (DASH_KEY.consumeClick()) tryStart(mc, player, MagicMod.DASH);

		// Применяем импульс движения
		if (selfTicksLeft > 0) {
			int dur = MagicMod.DURATION[selfType];
			float t = (dur - selfTicksLeft) / (float) dur;
			double speed = selfType == MagicMod.ROLL ? 0.62 * (1.0 - 0.55 * t) : 1.0 * (1.0 - 0.5 * t);
			double vy = player.getDeltaMovement().y;
			if (selfType == MagicMod.DASH && !player.onGround()) vy = 0; // в воздухе рывок "парит"
			player.setDeltaMovement(dirX * speed, vy, dirZ * speed);
			selfTicksLeft--;
		}
	}

	private static void tryStart(Minecraft mc, LocalPlayer p, int type) {
		if (cooldown > 0 || selfTicksLeft > 0) return;
		if (p.isSpectator() || p.isFallFlying() || p.isPassenger() || p.isSwimming() || p.onClimbable()) return;
		if (type == MagicMod.ROLL && !p.onGround()) return;
		if (mc.screen != null) return;

		int fwd = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
		int side = (mc.options.keyRight.isDown() ? 1 : 0) - (mc.options.keyLeft.isDown() ? 1 : 0);
		if (fwd == 0 && side == 0) fwd = 1; // без ввода — вперёд

		double yaw = Math.toRadians(p.getYRot());
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // вперёд
		double rx = -Math.cos(yaw), rz = -Math.sin(yaw);  // вправо
		double dx = fwd * fx + side * rx, dz = fwd * fz + side * rz;
		double len = Math.sqrt(dx * dx + dz * dz);
		dirX = dx / len;
		dirZ = dz / len;

		selfType = type;
		selfTicksLeft = MagicMod.DURATION[type];
		cooldown = MagicMod.COOLDOWN[type];

		startAnim(mc.level, p, type, fwd, side);
		ClientPlayNetworking.send(new ActionC2SPayload(type, fwd, side));
	}

	private static void startAnim(ClientLevel level, Entity e, int type, int fwd, int side) {
		ANIMS.put(e.getId(), new Anim(type, clientTicks, fwd, side));
		ParticleOptions fx = type == MagicMod.DASH ? ParticleTypes.END_ROD : ParticleTypes.CLOUD;
		for (int i = 0; i < 10; i++) {
			level.addParticle(fx,
					e.getX() + (level.random.nextDouble() - 0.5) * 0.8, e.getY() + 0.1,
					e.getZ() + (level.random.nextDouble() - 0.5) * 0.8,
					(level.random.nextDouble() - 0.5) * 0.1, 0.03, (level.random.nextDouble() - 0.5) * 0.1);
		}
	}
}
