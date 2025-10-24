package org.hyperlib.combat.sound;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;


/**
 * Plays a looping sound of a hyperspace storm
 */
public class SoundLoopBase extends BaseEveryFrameCombatPlugin {
    protected static float SOUND_FADE_TIME = 0.2f;
    /// This is the 'natural' fade

    // Set during initialisation
    private final String soundId;
    private final CombatEntityAPI soundAnchor;
    private final Vector2f location;
    private final float duration;
    private final float fadeInDuration;
    private final float fadeOutDuration;
    private final float volume;
    private final boolean removeWithAnchor;
    private final boolean expireWithAnchor;

    // Changes during run
    private float elapsed = 0;

    /**
     * Initialises the sound
     *
     * @param soundId          The ID of the looping sound.
     * @param soundAnchor      CombatEntity this sound is (conceptually) coming from.
     * @param volume           The volume to play the sound at.
     * @param duration         How long this lasts for.
     * @param fadeInDuration   How long it takes for it to fade in.
     * @param fadeOutDuration  How long it takes for it to fade out.
     * @param removeWithAnchor Whether the sound should stop if the anchor is removed.
     * @param expireWithAnchor Whether the sound should stop if the anchor expires.
     */
    public SoundLoopBase(
            String soundId,
            CombatEntityAPI soundAnchor,
            float volume,
            float duration,
            float fadeInDuration,
            float fadeOutDuration,
            boolean removeWithAnchor,
            boolean expireWithAnchor
    ) {
        this.soundId = soundId;
        this.soundAnchor = soundAnchor;
        this.location = soundAnchor.getLocation();
        this.volume = volume;
        this.duration = duration;
        this.fadeInDuration = fadeInDuration;
        this.fadeOutDuration = fadeOutDuration;
        this.removeWithAnchor = removeWithAnchor;
        this.expireWithAnchor = expireWithAnchor;
    }

    /**
     * Plays the looping sound each frame
     *
     * @param amount The amount of time that's passed since last frame.
     * @param events ???
     */
    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        Global.getSoundPlayer().playLoop(
                this.soundId, this.soundAnchor, 1f, this.volume,
                this.location, new Vector2f(),
                this.fadeInDuration, this.fadeOutDuration
        );
        this.elapsed += amount;
        if (this.elapsed > this.duration) Global.getCombatEngine().removePlugin(this);
        if (this.removeWithAnchor) {
            if (this.soundAnchor.wasRemoved()) Global.getCombatEngine().removePlugin(this);
        }
        if (this.expireWithAnchor) {
            if (this.soundAnchor.isExpired()) Global.getCombatEngine().removePlugin(this);
        }
    }
}
