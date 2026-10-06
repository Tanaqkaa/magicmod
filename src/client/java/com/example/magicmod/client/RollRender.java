package com.example.magicmod.client;

/** Данные анимации, которые кладём в render state игрока на время кадра. */
public record RollRender(boolean active, int type, float progress, int fwd, int side, float halfHeight) {
	public static final RollRender NONE = new RollRender(false, 0, 0f, 0, 0, 0f);
}
