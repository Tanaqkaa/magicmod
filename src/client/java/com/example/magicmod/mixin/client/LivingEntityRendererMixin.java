package com.example.magicmod.mixin.client;

import com.example.magicmod.MagicMod;
import com.example.magicmod.client.MagicModClient;
import com.example.magicmod.client.RollRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Анимация переката (сальто вокруг центра тела) и наклона при рывке.
 * Если сальто крутится "не в ту сторону" — поменяйте знак у ang / lean.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

	/** Шаг 1: из сущности считаем прогресс анимации и кладём его в render state. */
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At("TAIL"))
	private void magicmod$extract(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
		MagicModClient.Anim a = MagicModClient.ANIMS.get(entity.getId());
		if (a != null) {
			float p = (MagicModClient.clientTicks - a.start() + partialTick) / (float) MagicMod.DURATION[a.type()];
			if (p >= 0f && p < 1f) {
				state.setData(MagicModClient.ROLL_RENDER,
						new RollRender(true, a.type(), p, a.fwd(), a.side(), entity.getBbHeight() / 2f));
				return;
			}
		}
		state.setData(MagicModClient.ROLL_RENDER, RollRender.NONE);
	}

	/** Шаг 2: при отрисовке крутим модель вокруг центра тела. */
	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void magicmod$rotate(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
		RollRender r = state.getData(MagicModClient.ROLL_RENDER);
		if (r == null || !r.active()) return;

		poseStack.translate(0f, r.halfHeight(), 0f);

		if (r.type() == MagicMod.ROLL) {
			float eased = r.progress() * r.progress() * (3f - 2f * r.progress()); // плавный старт/финиш
			float ang = 360f * eased;
			if (r.fwd() != 0) poseStack.mulPose(Axis.XP.rotationDegrees(ang * r.fwd()));
			if (r.side() != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(ang * r.side()));
		} else {
			float lean = 30f * Mth.sin((float) Math.PI * r.progress()); // наклон по ходу рывка
			if (r.fwd() != 0) poseStack.mulPose(Axis.XP.rotationDegrees(lean * r.fwd()));
			if (r.side() != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(lean * r.side()));
		}

		poseStack.translate(0f, -r.halfHeight(), 0f);
	}
}
