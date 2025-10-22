package toaster.hp.shipsystems;

import java.util.List;

import java.awt.Color;

import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.impl.combat.MoteControlScript;
import org.hyperlib.FXColours;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.EmpArcEntityAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import toaster.hp.campaign.ids.ShipSystems;
import toaster.hp.hullmods.GhostPossessed;


//public class GhostMoteController extends BaseShipSystemScript {
public class GhostMoteControlScript extends MoteControlScript {
    public static final String ID = ShipSystems.POSSESSED_MOTE_CONTROLLER;

    public static final float CHARGE_COST = 1f;  /// The charge reduction on usage

    // --------------------------------
    // Settings for the attractor behaviour
    // --------------------------------
    protected static float MAX_ATTRACTOR_RANGE = 3000f;
    public static float ATTRACTOR_DURATION_LOCK = 20f;
    public static float ATTRACTOR_DURATION = 10f;
    public static Color ATTRACTOR_ARC_COLOUR = FXColours.DEEP_HYPERSPACE_STORMY;

    // --------------------------------
    // Variables that change during run
    // --------------------------------
    protected State stateLast = State.IDLE;
    protected IntervalUtil attractorParticleInterval = new IntervalUtil(0.05f, 0.1f);
    protected boolean findNewTargetOnUse = true;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = null;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
        } else {
            return;
        }
        if (state != this.stateLast) {
            this.stateLast = state;
            if (state == State.IN) {
                GhostPossessed ghostPossessed = GhostPossessed.getGhostPossessedScriptFor(ship);
                if (ghostPossessed != null) ghostPossessed.modifyCharge(-CHARGE_COST);
            }
        }

        GhostPossessed.SharedGhostMoteAIData data = GhostPossessed.getSharedData(ship);

        if (effectLevel <= 0) {
            findNewTargetOnUse = true;
        }

        CombatEngineAPI engine = Global.getCombatEngine();
        float amount = engine.getElapsedInLastFrame();

        attractorParticleInterval.advance(amount);
        if (attractorParticleInterval.intervalElapsed()) {
            spawnAttractorParticles(ship);
        }

        if (effectLevel > 0 && findNewTargetOnUse) {
            calculateTargetData(ship);
            findNewTargetOnUse = false;
        }

        if (effectLevel == 1) {
            // possible if system is reused immediately w/ no time to cool down, I think
            if (data.attractorTarget == null) {
                calculateTargetData(ship);
            }

            Vector2f slotLoc = ship.getLocation();

            CombatEntityAPI asteroid = engine.spawnAsteroid(0, data.attractorTarget.x, data.attractorTarget.y, 0, 0);
            asteroid.setCollisionClass(CollisionClass.NONE);
            CombatEntityAPI target = asteroid;
            if (data.attractorLock != null) {
                target = data.attractorLock;
            }

            EmpArcEntityAPI.EmpArcParams params = new EmpArcEntityAPI.EmpArcParams();
            params.segmentLengthMult = 8f;
            params.zigZagReductionFactor = 0.15f;

            params.brightSpotFullFraction = 0.5f;
            params.brightSpotFadeFraction = 0.5f;

            float dist = Misc.getDistance(slotLoc, target.getLocation());
            params.flickerRateMult = 0.6f - dist / 3000f;
            if (params.flickerRateMult < 0.3f) {
                params.flickerRateMult = 0.3f;
            }

            float emp = 0;
            float dam = 0;
            EmpArcEntityAPI arc = (EmpArcEntityAPI)engine.spawnEmpArc(ship, slotLoc, ship, target,
                    DamageType.ENERGY,
                    dam,
                    emp, // emp
                    100000f, // max range
                    "mote_attractor_targeted_ship",
                    40f, // thickness
                    ATTRACTOR_ARC_COLOUR,
                    new Color(255,255,255,255),
                    params
            );
            if (data.attractorLock != null) {
                arc.setTargetToShipCenter(slotLoc, data.attractorLock);
            }
            arc.setCoreWidthOverride(30f);
            arc.setSingleFlickerMode(true);

            if (data.attractorLock == null) {
                Global.getSoundPlayer().playSound("mote_attractor_targeted_empty_space", 1f, 1f, data.attractorTarget, new Vector2f());
            }
            engine.removeEntity(asteroid);
        }
    }

    @Override
    protected void spawnAttractorParticles(ShipAPI ship) {
        if (true) return; // just not liking this much
        GhostPossessed.SharedGhostMoteAIData data = GhostPossessed.getSharedData(ship);

        if (data.attractorTarget == null) return;

        CombatEngineAPI engine = Global.getCombatEngine();

        Vector2f targetLoc = data.attractorTarget;

        int glows = 2;
        float maxRadius = 300f;
        float minRadius = 200f;

        if (data.attractorLock != null) {
            maxRadius += data.attractorLock.getCollisionRadius();
            minRadius += data.attractorLock.getCollisionRadius();
            targetLoc = data.attractorLock.getShieldCenterEvenIfNoShield();
        }

        float minDur = 0.5f;
        float maxDur = 0.75f;
        float minSize = 15f;
        float maxSize = 30f;
        for (int i = 0; i < glows; i++) {
            float radius = minRadius + (float) Math.random() * (maxRadius - minRadius);
            Vector2f loc = Misc.getPointAtRadius(targetLoc, radius);
            Vector2f dir = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(loc, targetLoc));
            float dist = Misc.getDistance(loc, targetLoc);

            float dur = minDur + (float) Math.random() * (maxDur - minDur);
            float speed = dist / dur;
            dir.scale(speed);

            float size = minSize + (float) Math.random() * (maxSize - minSize);

            engine.addHitParticle(loc, dir, size, 1f, 0.3f, dur, ATTRACTOR_ARC_COLOUR);
            engine.addHitParticle(loc, dir, size * 0.5f, 1f, 0.3f, dur, Color.white);
        }
    }

    /**
     * Updates the shared data for the ship with the new target.
     *
     * @param ship              The ship to get the target data for.
     */
    @Override
    public void calculateTargetData(ShipAPI ship) {
        GhostPossessed.SharedGhostMoteAIData data = GhostPossessed.getSharedData(ship);
        Vector2f targetLoc = getTargetLoc(ship);
        //System.out.println(getTargetedLocation(ship));
        data.attractorLock = getLockTarget(ship, targetLoc);

        data.attractorRemaining = ATTRACTOR_DURATION;
        if (data.attractorLock != null) {
            targetLoc = new Vector2f(data.attractorLock.getLocation());
            data.attractorRemaining = ATTRACTOR_DURATION_LOCK;
        }
        data.attractorTarget = targetLoc;

        if (data.attractorLock != null) {
            // need to do this in a script because when the ship is phased, the charge-in time of the system (0.1s)
            // is not enough for the jitter to come to full effect (which requires 0.1s "normal" time)
            Global.getCombatEngine().addPlugin(createTargetJitterPlugin(data.attractorLock,
                    ship.getSystem().getChargeUpDur(), ship.getSystem().getChargeDownDur(),
                    com.fs.starfarer.api.impl.combat.MoteControlScript.getJitterColor(ship)));
        }
    }

    @Override
    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        if (system.isOutOfAmmo()) return null;
        if (system.getState() != ShipSystemAPI.SystemState.IDLE) return null;

        boolean inRange = isMouseInRange(ship);
        //Vector2f targetLoc = getTargetLoc(ship);
        //ShipAPI target = getLockTarget(ship, targetLoc);

        if (!inRange) {
            return "OUT OF RANGE";
        }
//		if (target != null) {
//			return "ENEMY SHIP";
//		}
//		return "AREA";
        return null;
    }

    @Override
    public Vector2f getTargetLoc(ShipAPI from) {
        Vector2f slotLoc = from.getLocation();
        Vector2f targetLoc = new Vector2f(getTargetedLocation(from));
        float dist = Misc.getDistance(slotLoc, targetLoc);
        if (dist > getRange(from)) {
            targetLoc = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(slotLoc, targetLoc));
            targetLoc.scale(getRange(from));
            Vector2f.add(targetLoc, slotLoc, targetLoc);
        }
        return targetLoc;
    }

    @Override
    public boolean isMouseInRange(ShipAPI from) {
        Vector2f targetLoc = new Vector2f(from.getMouseTarget());
        return isLocationInRange(from, targetLoc);
    }

    @Override
    public boolean isLocationInRange(ShipAPI from, Vector2f loc) {
        Vector2f slotLoc = from.getLocation();
        float dist = Misc.getDistance(slotLoc, loc);
        if (dist > getRange(from)) {
            return false;
        }
        return true;
    }

    @Override
    public ShipAPI getLockTarget(ShipAPI from, Vector2f loc) {
        Vector2f slotLoc = from.getLocation();
        for (ShipAPI other : Global.getCombatEngine().getShips()) {
            if (other.isFighter()) continue;
            if (other.getOwner() == from.getOwner()) continue;
            if (other.isHulk()) continue;
            if (!other.isTargetable()) continue;

            float dist = Misc.getDistance(slotLoc, other.getLocation());
            if (dist > getRange(from)) continue;

            dist = Misc.getDistance(loc, other.getLocation());
            if (dist < other.getCollisionRadius() + 50f) {
                return other;
            }
        }
        return null;
    }

    public static float getRange(ShipAPI ship) {
        if (ship == null) return MAX_ATTRACTOR_RANGE;
        return ship.getMutableStats().getSystemRangeBonus().computeEffective(MAX_ATTRACTOR_RANGE);
    }

    // Probably needed for the AI?
    public static boolean isHighFrequency(ShipAPI ship) { return true; }
    public static int getMaxMotes(ShipAPI ship) { return GhostPossessed.getSharedData(ship).maxMotes; }


    protected EveryFrameCombatPlugin createTargetJitterPlugin(final ShipAPI target,
                                                              final float in,
                                                              final float out,
                                                              final Color jitterColor) {
        return new BaseEveryFrameCombatPlugin() {
            float elapsed = 0f;
            @Override
            public void advance(float amount, List<InputEventAPI> events) {
                if (Global.getCombatEngine().isPaused()) return;

                elapsed += amount;

                float level = 0f;
                if (elapsed < in) {
                    level = elapsed / in;
                } else if (elapsed < in + out) {
                    level = 1f - (elapsed - in) / out;
                    level *= level;
                } else {
                    Global.getCombatEngine().removePlugin(this);
                    return;
                }


                if (level > 0) {
                    float jitterLevel = level;
                    float maxRangeBonus = 50f;
                    float jitterRangeBonus = jitterLevel * maxRangeBonus;
                    target.setJitterUnder(this, jitterColor, jitterLevel, 10, 0f, jitterRangeBonus);
                    target.setJitter(this, jitterColor, jitterLevel, 4, 0f, 0 + jitterRangeBonus);
                }
            }
        };
    }
}









