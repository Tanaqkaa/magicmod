package com.example.magicmod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Сервер -> соседние клиенты: "у этой сущности началась анимация". */
public record ActionS2CPayload(int entityId, int type, int fwd, int side) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ActionS2CPayload> ID =
			new Type<>(Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, "action_s2c"));
	public static final StreamCodec<FriendlyByteBuf, ActionS2CPayload> CODEC =
			CustomPacketPayload.codec(ActionS2CPayload::write, ActionS2CPayload::new);

	public ActionS2CPayload(FriendlyByteBuf buf) {
		this(buf.readInt(), buf.readByte(), buf.readByte(), buf.readByte());
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeInt(entityId);
		buf.writeByte(type);
		buf.writeByte(fwd);
		buf.writeByte(side);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
