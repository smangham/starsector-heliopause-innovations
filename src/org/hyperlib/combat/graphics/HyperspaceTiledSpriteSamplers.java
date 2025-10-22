package org.hyperlib.combat.graphics;

import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * Samplers for the various tiled sprites.
 */
public class HyperspaceTiledSpriteSamplers {
    protected static TiledSpriteSampler deepHyperspace = new TiledSpriteSampler(
            "hyperlib", "deep_hyperspace", 4, 4
    );
    protected static TiledSpriteSampler deepHyperspaceDark = new TiledSpriteSampler(
            "hyperlib", "deep_hyperspace_dark", 4, 4
    );
    protected static TiledSpriteSampler deepHyperspaceGlow = new TiledSpriteSampler(
            "hyperlib", "deep_hyperspace_glow", 4, 4
    );

    public static SpriteAPI getHyperspaceSprite() {
        return deepHyperspace.getSprite();
    }
    public static SpriteAPI getHyperspaceDarkSprite() {
        return deepHyperspaceDark.getSprite();
    }
    public static SpriteAPI getHyperspaceGlowSprite() {
        return deepHyperspaceGlow.getSprite();
    }
}
