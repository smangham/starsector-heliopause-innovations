package org.hyperlib.combat.sound;

import com.fs.starfarer.api.combat.CombatEntityAPI;


/**
 * Plays a looping sound of a hyperspace storm
 * <p>
 * Subclasses the loop base, basically just to fix some settings.
 */
public class HyperspaceStormSoundLoop extends SoundLoopBase {
    // Static settings
    protected static String SOUND_ID = "terrain_hyperspace_storm";
    /// The sound ID
    protected static float VOLUME_MAX = 1.5f;
    /// Volume scaling for this sound
    protected static float FADE_DURATION = 0.2f;  /// This is the 'natural' fade

    /**
     * Initialises the sound
     *
     * @param soundAnchor CombatEntity this sound is (conceptually) coming from.
     * @param duration    How long this lasts for.
     */
    public HyperspaceStormSoundLoop(CombatEntityAPI soundAnchor, float duration) {
        super(SOUND_ID, soundAnchor, VOLUME_MAX, duration, FADE_DURATION, FADE_DURATION, false, false);
    }
}
