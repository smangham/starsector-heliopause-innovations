package org.hyperlib.combat.graphics;

import com.fs.starfarer.api.graphics.SpriteAPI;
import org.magiclib.graphics.MagicTiledSpriteSampler;

/**
 * Samplers for the various tiled sprites.
 */
public class HyperspaceTiledSpriteSamplers {
    protected static MagicTiledSpriteSampler deepHyperspace = new MagicTiledSpriteSampler(
            "hyperlib", "deep_hyperspace", 4, 4
    );
    protected static MagicTiledSpriteSampler deepHyperspaceDark = new MagicTiledSpriteSampler(
            "hyperlib", "deep_hyperspace_dark", 4, 4
    );
    protected static MagicTiledSpriteSampler deepHyperspaceGlow = new MagicTiledSpriteSampler(
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
