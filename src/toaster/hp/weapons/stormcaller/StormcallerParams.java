package toaster.hp.weapons.stormcaller;

import com.fs.starfarer.api.Global;
import org.hyperlib.HyperLibColours;

import java.awt.*;

/**
 *
 */
public class StormcallerParams {
    public static final float EXPLOSION_RADIUS = 192f;
    /// How large is the explosion radius
    public static final float EXPLOSION_CORE_RADIUS = 128f;
    /// How large is the 'core', full-damage radius

    public static final float STRIKE_TO_CLOUD_RADIUS_MULT = 1.33f;
    /// If no ship to hit, how far to look for a cloud to hit
    public static final float STRIKE_IMPULSE = 400f;
    /// Can't get impulse from weapon data.
    public static final float STRIKE_ARC_WIDTH = 32f;
    /// The width of the EMP arc.
    public static final Color STRIKE_COLOUR = HyperLibColours.DEEP_HYPERSPACE_STRIKE;  /// The colour of the EMP arc.

    /**
     * Sets the area the cloud is placed over.
     * <p>
     * For equal areas of mines (number n, radius r) and placement zone (radius R): nπr² = πR²
     * The highest possible circular packing fraction is 0.9, but there is *no way* we're reaching that.
     * 'Random close-packed discs' are it turns out an area of research, with ~.66 as the packing fraction.
     * So this must be *at least* 1/0.66 i.e. 1.5+, one we take into account the exclusion radius multiplier.
     */
    public static final float MINE_SPAWN_RADIUS_PER_ROOT_CHARGE = 1.33f;
    public static final float MINE_SPAWN_SPREAD_SPEED_RADIUS_MULT = 1f;
    /// How fast the 'cloud' visuals propagate
    public static final int MINE_SPAWN_TRIES = 16;
    /// How many times to try and spawn a mine before giving up.
    public static final float MINE_EXCLUSION_RADIUS_MULT = 0.5f;
    /// How much of the 'core' radius is the exclusion zone

    // Storm cloud visuals
    public static final float CLOUD_SPRITE_SIZE = (float) Math.sqrt(2f * EXPLOSION_RADIUS * EXPLOSION_RADIUS);
    /// How large the cloud is
    public static final float CLOUD_FLASH_WAIT_MULT = 0.66f;
    /// After each flash, maximum flash delay is multiplied by this.
    public static final float CLOUD_FADE_OUT = 3f;  /// How long should it take to fade out.
}