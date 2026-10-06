package com.example.magicmod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Клиент -> сервер: "я делаю перекат/рывок" (type, fwd, side). */
public record ActionC2SPayload(int type, int fwd, int side) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ActionC2SPayload> ID =
			new Type<>(Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, "action_c2s"));
	public static final StreamCodec<FriendlyByteBuf, ActionC2SPayload> CODEC =
			CustomPacketPayload.codec(ActionC2SPayload::write, ActionC2SPayload::new);

	public ActionC2SPayload(FriendlyByteBuf buf) {
		this(buf.readByte(), buf.readByte(), buf.readByte());
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeByte(type);
		buf.writeByte(fwd);
		buf.writeByte(side);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
