package toaster.hp.campaign.terrain.hyperspace;


import java.awt.Color;
import org.hyperlib.campaign.terrain.HyperLibHyperspaceTerrainPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberViewAPI;
import com.fs.starfarer.api.util.Misc;


/**
 * Variant of the HyperStormBoost that gives a bit more control.
 */
public class StormDampnerHyperStormBoost implements EveryFrameScript {
    public static float MAX_BURN = Global.getSettings().getFloat("maxStormStrikeBurn");
    public static float STORM_SPEED_BURST = Global.getSettings().getSpeedPerBurnLevel() * 50f;
    public static float DURATION_SECONDS = 1f;

    protected CampaignFleetAPI fleet;
    protected float elapsed;
    protected float angle;
    protected HyperLibHyperspaceTerrainPlugin.CellStateTracker cell;

    /**
     * Applies a boost in the direction of the storm blast.
     *
     * @param cell  The hyperspace cell that jolted the ship.
     * @param fleet The fleet that's been jolted.
     */
    public StormDampnerHyperStormBoost(HyperLibHyperspaceTerrainPlugin.CellStateTracker cell, CampaignFleetAPI fleet) {
        this.cell = cell;
        this.fleet = fleet;

        DURATION_SECONDS = 1.25f;
        STORM_SPEED_BURST = Global.getSettings().getSpeedPerBurnLevel() * 75f;

//		DURATION_SECONDS = 2f;
//		STORM_SPEED_BURST = Global.getSettings().getSpeedPerBurnLevel() * 50f;

        if (Misc.getHyperspaceTerrain().getPlugin() instanceof HyperLibHyperspaceTerrainPlugin) {
            HyperLibHyperspaceTerrainPlugin hyper = (HyperLibHyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();

            // Get the location of the cell that caused this strike
            float x = hyper.getEntity().getLocation().x;
            float y = hyper.getEntity().getLocation().y;
            float size = hyper.getTileSize();

            float w = hyper.getTiles().length * size;
            float h = hyper.getTiles()[0].length * size;

            x -= w/2f;
            y -= h/2f;

            float tx = x + cell.i * size + size/2f;
            float ty = y + cell.j * size + size/2f;

            // Get the angle from the cell that struck towards the fleet
            angle = Misc.getAngleInDegrees(new Vector2f(tx, ty), fleet.getLocation());

            // Get the angle that the fleet is trying to go
            // CHANGE: We go in the direction of *facing* always, even if fast.
            Vector2f v = fleet.getVelocity();
//            float angle2 = Misc.getAngleInDegrees(v);
            float speed = v.length();
//            if (speed < 10) angle2 = fleet.getFacing();
            float angle2 = fleet.getFacing();

            // The 'best' angle adjustment occurs when the speed is burn 20 or less.
            // Higher speed = higher mult, up to 0.9, i.e. almost all of the angle being forced is the impulse angle.
            float bestAngleAt = Global.getSettings().getBaseTravelSpeed() + Global.getSettings().getSpeedPerBurnLevel() * 20f;
            float mult = 0.5f + 0.4f * speed / bestAngleAt;
//            if (mult < 0.5f) mult = 0.5f;
//            if (mult > 0.9f) mult = 0.9f;
            mult = MathUtils.clamp(mult, 0.4f, 0.8f);  // Clamp the value

            // Get the sign it needs to turn, and then adjust it by a multiplier of the angle difference between
            // the impulse from the storm cell, and the direction of travel.
            angle += Misc.getClosestTurnDirection(angle, angle2) * Misc.getAngleDiff(angle, angle2) * mult;
        }
    }

    /**
     * Every frame adjust the ship's heading towards the 'target' heading.
     *
     * @param amount    Seconds elapsed during the last frame.
     */
    public void advance(float amount) {
        elapsed += amount;
        Vector2f boost = Misc.getUnitVectorAtDegreeAngle(angle);

        float mult = 1f;
        mult = 1f - elapsed / DURATION_SECONDS;
        mult *= Math.pow(Math.min(1f, elapsed / 0.25f), 2f);
        if (mult < 0) mult = 0;
        if (mult > 1) mult = 1;
        boost.scale(STORM_SPEED_BURST * amount * mult);

        Vector2f v = fleet.getVelocity();

        if (fleet.getCurrBurnLevel() < MAX_BURN) {
            fleet.setVelocity(v.x + boost.x, v.y + boost.y);
        }

//        float angleHeading = Misc.getAngleInDegrees(v);
//        if (v.length() < 10) angleHeading = fleet.getFacing();
        // CHANGE: Always use the fleet's facing.
        float angleHeading = fleet.getFacing();

        boost = Misc.getUnitVectorAtDegreeAngle(angleHeading);
        //boost.negate();
        if (boost.length() >= 1) {
            float durIn = 1f;
            float durOut = 3f;
            float intensity = 1f;
            float sizeNormal = 5f + 20f * intensity;
            String modId = "boost " + cell.i + cell.j * 100;
            Color glowColor = new Color(100, 100, 255, 75);
            for (FleetMemberViewAPI view : fleet.getViews()) {
                view.getWindEffectDirX().shift(modId, boost.x * sizeNormal, durIn, durOut, 1f);
                view.getWindEffectDirY().shift(modId, boost.y * sizeNormal, durIn, durOut, 1f);
                view.getWindEffectColor().shift(modId, glowColor, durIn, durOut, intensity);
            }
        }
    }

    public boolean isDone() {
        return elapsed >= DURATION_SECONDS || !fleet.isInHyperspace();
    }

    public boolean runWhilePaused() { return false; }

}

