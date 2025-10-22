package toaster.hp.campaign.terrain.hyperspace;

import com.fs.starfarer.api.campaign.BuffManagerAPI.Buff;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import java.awt.*;

/**
 * Boosts a fleet's sensor profile for a short time.
 */
public class GhostSensorProfileStormBoost implements Buff {
    public static final float SENSOR_PROFILE_INCREASE = 1000f;  /// Flat sensor profile boost.
    public static final float DURATION_SECONDS = 3f;  /// How long the buff lasts.

    protected String buffId;
    protected float elapsed = 0f;

    /**
     * Creates the buff.
     *
     * @param buffId    The ID of this buff.
     */
    public GhostSensorProfileStormBoost(String buffId) {
        this.buffId = buffId;
    }

    @Override
    public String getId() { return this.buffId; }

    /**
     * Track the time the buff takes until elapsing.
     *
     * @param amount    Seconds elapsed during the last frame.
     */
    @Override
    public void advance(float amount) {
        elapsed += amount;
    }

    /**
     * @param member
     */
    @Override
    public void apply(FleetMemberAPI member) {
        member.getStats().getSensorProfile().modifyFlat(this.buffId, SENSOR_PROFILE_INCREASE);
    }

    /**
     * @return
     */
    @Override
    public boolean isExpired() {
        return elapsed > DURATION_SECONDS;
    }
}
