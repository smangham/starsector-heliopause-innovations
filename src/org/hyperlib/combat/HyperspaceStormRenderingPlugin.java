package org.hyperlib.combat;

import java.util.*;

import java.awt.Color;

import com.fs.starfarer.api.util.*;
import org.hyperlib.HyperLibIds;
import org.hyperlib.combat.graphics.HyperspaceTiledSpriteSamplers;
import org.hyperlib.combat.graphics.WarpingTiledSpriteRendererUtil;
import org.hyperlib.util.ScalingFlickerUtil;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;


/**
 * A hyperspace storm cloud.
 */
public class HyperspaceStormRenderingPlugin extends BaseCombatLayeredRenderingPlugin {
    public static float CLOUD_UNDER_SIZE_MULT = 1.0f;
    public static float CLOUD_OVER_SIZE_MULT = 0.9f;
    public static float CLOUD_OVER_ALPHA_MULT = 0.75f;
    public static float CLOUD_GLOW_SIZE_MULT = 1.1f;
    public static float WARP_RATE_MULT = 0.5f;
    public static Color COLOUR = Color.white;

    /**
     * Class for an individual storm cell within the storm.
     * (May be just one cell per storm!)
     */
    public static class StormCell {
        public WarpingTiledSpriteRendererUtil rendererUnder, rendererOver, rendererGlow;
        public Color color = Color.white;

        protected ScalingFlickerUtil glowFlicker;
        protected float currBrightness;

        /**
         * StormCell constructor
         * <p>
         * Constructs a layered cell consisting of background, glow layer, and top layer.
         *
         * @param size         Side size.
         * @param vertices     The number of wiggling vertices across the sprite texture.
         * @param warpMult     Multiplier on the size of the wiggle.
         * @param glowWaitMax  If it storm flashes, how long is the maximum window between flashes..
         * @param glowWaitMult How much that interval is multiplied by each flash.
         * @param fadeIn       How long it takes for the cloud to fade in.
         * @param fadeOut      How long it takes for the cloud to fade out.
         */
        public StormCell(
                float size, int vertices, float warpMult,
                float glowWaitMax, float glowWaitMult,
                float fadeIn, float fadeOut
        ) {
            this.fader = new FaderUtil(0f, fadeIn, fadeOut);
            this.fadeIn();

            this.glowFlicker = new ScalingFlickerUtil(glowWaitMax, glowWaitMult);
            this.glowFlicker.newWait();
            this.brightness = new ValueShifterUtil(1f);
            this.spin = new MutatingValueUtil(0, 0, 0);

            SpriteAPI sprite_under = HyperspaceTiledSpriteSamplers.getHyperspaceDarkSprite();
            SpriteAPI sprite_over = HyperspaceTiledSpriteSamplers.getHyperspaceSprite();
            SpriteAPI sprite_glow = HyperspaceTiledSpriteSamplers.getHyperspaceGlowSprite();

            float sprite_under_size = size * CLOUD_UNDER_SIZE_MULT;
            sprite_under.setSize(sprite_under_size, sprite_under_size);
            sprite_under.setCenter(sprite_under_size / 2f, sprite_under_size / 2f);
            rendererUnder = new WarpingTiledSpriteRendererUtil(
                    sprite_under, vertices, vertices,
                    sprite_under_size * 0.05f * warpMult,
                    sprite_under_size * 0.05f * warpMult * 1.4f,
                    WARP_RATE_MULT
            );

            float sprite_glow_size = size * CLOUD_GLOW_SIZE_MULT;
            sprite_glow.setSize(sprite_glow_size, sprite_glow_size);
            sprite_glow.setCenter(sprite_glow_size / 2f, sprite_glow_size / 2f);
            rendererGlow = new WarpingTiledSpriteRendererUtil(
                    sprite_glow, vertices, vertices,
                    sprite_glow_size * 0.05f * warpMult,
                    sprite_glow_size * 0.05f * warpMult * 1.4f,
                    WARP_RATE_MULT
            );

            float sprite_over_size = size * CLOUD_OVER_SIZE_MULT;
            sprite_over.setSize(sprite_over_size, sprite_over_size);
            sprite_over.setCenter(sprite_over_size / 2f, sprite_over_size / 2f);
            rendererOver = new WarpingTiledSpriteRendererUtil(
                    sprite_over, vertices, vertices,
                    sprite_over_size * 0.05f * warpMult,
                    sprite_over_size * 0.05f * warpMult * 1.4f,
                    WARP_RATE_MULT
            );
            this.setAlphaMult(1f);
        }

        public Set<String> tags = new LinkedHashSet<>();

        public Set<String> getTags() {
            return this.tags;
        }

        public void addTag(String tag) {
            this.tags.add(tag);
        }

        public void removeTag(String tag) {
            this.tags.remove(tag);
        }

        public boolean hasTag(String tag) {
            return tags.contains(tag);
        }

        public float angle = 0f;

        public float getAngle() {
            return this.angle;
        }

        public void setAngle(float angle) {
            this.angle = angle;
        }

        public MutatingValueUtil spin;

        public MutatingValueUtil getSpin() {
            return this.spin;
        }

        public void setSpin(float min, float max, float rate) {
            spin = new MutatingValueUtil(min, max, rate);
        }

        public FaderUtil fader;

        public FaderUtil getFader() {
            return this.fader;
        }

        public void fadeOut() {
            this.fader.fadeOut();
        }

        public void fadeIn() {
            this.fader.fadeIn();
        }

        public float alphaMult;

        public float getAlphaMult() {
            return this.alphaMult;
        }

        public void setAlphaMult(float alphaMult) {
            this.alphaMult = alphaMult;
        }

        public String id;

        public String getId() {
            return this.id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void startStormBurst() {
            this.glowFlicker.newBurst();
        }

        public ValueShifterUtil brightness;

        public ValueShifterUtil getBrightness() {
            return brightness;
        }

        /**
         * Tick along timers, adjusting ongoing faders e.t.c.
         * <p>
         * Adjusts continually-varying glow, brightness, flicker e.t.c..
         *
         * @param amount Time that has passed this frame.
         */
        public void advance(float amount) {
            this.fader.advance(amount);
            this.brightness.advance(amount);
            this.glowFlicker.advance(amount);

            float desired = this.brightness.getCurr();
            this.currBrightness = Misc.approach(this.currBrightness, desired, 1f, 1f, amount);

            this.spin.advance(amount);
            this.angle += spin.getValue() * amount;

            this.rendererOver.advance(amount);
            this.rendererUnder.advance(amount);
            this.rendererGlow.advance(amount);
        }

        /**
         * Adjust the alpha multiplier of this call, then render the component parts.
         *
         * @param entityX   The X position of the entity to render.
         * @param entityY   The Y position of the entity to render.
         * @param alphaMult Multiplier on the default alpha.
         * @param angle     ???
         * @param layer     The rendering layer this has been called for.
         */
        public void render(float entityX, float entityY, float alphaMult, float angle, CombatEngineLayers layer) {
            alphaMult *= this.alphaMult;
            alphaMult *= this.fader.getBrightness();
            alphaMult *= this.currBrightness;
            renderImpl(entityX, entityY, alphaMult, angle, layer);
        }

        /**
         * Does the actual rendering of the component clouds.
         *
         * @param x         The X position of the entity to render.
         * @param y         The Y position of the entity to render.
         * @param alphaMult Multiplier on the default alpha.
         * @param angle     ???
         * @param layer     The rendering layer this has been called for.
         */
        public void renderImpl(float x, float y, float alphaMult, float angle, CombatEngineLayers layer) {
            if (layer == CombatEngineLayers.CLOUD_LAYER) {
                this.rendererUnder.getSprite().setNormalBlend();
                this.rendererUnder.getSprite().setAlphaMult(alphaMult);
                this.rendererUnder.getSprite().setColor(COLOUR);
                this.rendererUnder.getSprite().setAngle(angle + this.angle);
                this.rendererUnder.renderAtCenter(x, y);

            } else if (layer == CombatEngineLayers.ABOVE_SHIPS_LAYER) {
                this.rendererOver.getSprite().setNormalBlend();
                this.rendererOver.getSprite().setAlphaMult(alphaMult * CLOUD_OVER_ALPHA_MULT);
                this.rendererOver.getSprite().setColor(COLOUR);
                this.rendererOver.getSprite().setAngle(angle + this.angle);
                this.rendererOver.renderAtCenter(x, y);

            } else if (layer == CombatEngineLayers.ASTEROIDS_LAYER) {
                this.rendererGlow.getSprite().setAdditiveBlend();
                this.rendererGlow.getSprite().setAlphaMult(alphaMult * this.glowFlicker.getBrightness());
                this.rendererGlow.getSprite().setColor(COLOUR);
                this.rendererGlow.getSprite().setAngle(angle + this.angle);
                this.rendererGlow.renderAtCenter(x, y);
            }
        }
    }
    // --------------------------------

    protected CombatEntityAPI attachedTo;
    protected float elapsed = 0f;
    protected float lifetime = 0f;
    protected List<StormCell> stormCells = new ArrayList<>();


    public HyperspaceStormRenderingPlugin(
            CombatEntityAPI attachedTo, float size,
            float waitMax, float waitMult, float fadeIn, float fadeOut, float lifetime
    ) {
        CombatEntityAPI e = Global.getCombatEngine().addLayeredRenderingPlugin(this);
        e.getLocation().set(attachedTo.getLocation());
        this.attachedTo = attachedTo;
        this.lifetime = lifetime;
        this.stormCells.add(
                new StormCell(size, 5, 1f, waitMax, waitMult, fadeIn, fadeOut)
        );
    }

    public void init(CombatEntityAPI entity) {
        super.init(entity);
    }

    public float getRenderRadius() {
        float extra = 300f;
        return this.attachedTo.getCollisionRadius() + extra;
    }

    protected EnumSet<CombatEngineLayers> layers = EnumSet.of(
            CombatEngineLayers.CLOUD_LAYER, CombatEngineLayers.ABOVE_SHIPS_LAYER, CombatEngineLayers.ASTEROIDS_LAYER
    );

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return this.layers;
    }

    public void advance(float amount) {
        if (Global.getCombatEngine().isPaused() || entity == null || isExpired()) return;
        this.entity.getLocation().set(this.attachedTo.getLocation());
        this.elapsed += amount;

        if (this.lifetime > 0 && this.elapsed > this.lifetime) {
            this.lifetime = 0f;
            this.startStormBurst();
        }

        if (isExpired()) {
        }
        if (shouldDespawn()) {
            for (StormCell stormCell : this.stormCells) {
                stormCell.fadeOut();
            }
        }

        for (StormCell stormCell : stormCells) {
            stormCell.advance(amount);
        }
    }


    public boolean shouldDespawn() {
        if (attachedTo instanceof MissileAPI missile) {
            return !Global.getCombatEngine().isMissileAlive(missile);
        }
        return attachedTo.isExpired() || !Global.getCombatEngine().isEntityInPlay(attachedTo);
    }

    public boolean isExpired() {
        boolean shouldDespawn = shouldDespawn();
        if (!shouldDespawn) return false;

        boolean allFaded = true;
        for (StormCell stormCell : this.stormCells) {
            if (!stormCell.getFader().isFadedOut() && stormCell.getAlphaMult() > 0) {
                allFaded = false;
                break;
            }
        }
        return allFaded;
    }

    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        float alphaMult = viewport.getAlphaMult();
        if (alphaMult <= 0f) return;

        Vector2f aLoc = new Vector2f(attachedTo.getLocation());

        for (StormCell stormCell : this.stormCells) {
            stormCell.render(aLoc.x, aLoc.y, alphaMult, attachedTo.getFacing(), layer);
        }
    }

    public CombatEntityAPI getAttachedTo() {
        return attachedTo;
    }

    public List<StormCell> getStormCells() {
        return this.stormCells;
    }

    public StormCell getCell(String id) {
        for (StormCell stormCell : this.stormCells) {
            if (id.equals(stormCell.getId())) return stormCell;
        }
        return null;
    }

    public void fadeIn(String... tags) {
        for (StormCell stormCell : getStormCells(tags)) {
            stormCell.fadeIn();
        }
    }

    public void fadeOut(String... tags) {
        for (StormCell stormCell : getStormCells(tags)) {
            stormCell.fadeOut();
        }
    }

    public void setAlphaMult(float alphaMult, String... tags) {
        for (StormCell stormCell : getStormCells(tags)) {
            stormCell.setAlphaMult(alphaMult);
        }
    }

    public void setBrightness(float b, String... tags) {
        String key = "";
        for (String tag : tags) key += tag + "_";
        if (tags.length == 1) key = tags[0];

        for (StormCell stormCell : getStormCells(tags)) {
            stormCell.getBrightness().shift(key, b, 0.5f, 0.5f, 1f);
        }
    }

    public void startStormBurst() {
        for (StormCell stormCell : this.stormCells) {
            stormCell.startStormBurst();
        }
    }

    /**
     * Returns all cells matching a set of tags.
     *
     * @param tags Select only those cells with these tags.
     * @return A list of the storm cells with the specified tags.
     */
    public List<StormCell> getStormCells(String... tags) {
        List<StormCell> result = new ArrayList<>();

        OUTER:
        for (StormCell stormCell : this.stormCells) {
            for (String tag : tags) {
                if (stormCell.hasTag(tag)) {
                    result.add(stormCell);
                    continue OUTER;
                }
            }
        }
        return result;
    }
}







