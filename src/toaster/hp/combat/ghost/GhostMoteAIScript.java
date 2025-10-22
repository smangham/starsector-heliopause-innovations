package toaster.hp.combat.ghost;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.fs.starfarer.api.combat.*;
import org.hyperlib.FXColours;
import org.lazywizard.lazylib.CollisionUtils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import toaster.hp.hullmods.GhostPossessed;
import toaster.hp.hullmods.GhostPossessed.SharedGhostMoteAIData;


/**
 *
 */
public class GhostMoteAIScript implements MissileAIPlugin {

    public static float MAX_FLOCK_RANGE = 500;
    public static float MAX_HARD_AVOID_RANGE = 200;
    public static float AVOID_RANGE = 50;
    public static float COHESION_RANGE = 100;

    public static final Color ARC_COLOUR = FXColours.DEEP_HYPERSPACE_STRIKE;
    public static final float ARC_WIDTH = 24f;
    public static final float ARC_DURATION = 0.25f;

    public static final float SOURCE_COHESION_RANGE = 300f;  // 600f;
    public static final float SOURCE_REJOIN_RANGE = 100f;  // 200f
    public static final float SOURCE_REJOIN_SCALING_BASE = 200f;  // 400f
    public static final float SOURCE_REPEL_RANGE = 25f;  // 50f

    public static final float MAX_DIST_FROM_SOURCE_TO_ENAGAGE_AS_PD_ADD = 500f;
    public static final float MAX_DIST_FROM_ATTRACTOR_TO_ENGAGE_AS_PD = 800f;

    public static final float ATTRACTOR_LOCK_STOP_FLOCKING_ADD = 300f;

    protected MissileAPI missile;

    protected IntervalUtil tracker = new IntervalUtil(0.05f, 0.1f);
    protected IntervalUtil arcFxInterval = new IntervalUtil(2f, 4f);

    protected IntervalUtil updateListTracker = new IntervalUtil(0.05f, 0.1f);
    protected List<MissileAPI> missileList = new ArrayList<MissileAPI>();
    protected List<CombatEntityAPI> hardAvoidList = new ArrayList<CombatEntityAPI>();

    protected float r;

    protected CombatEntityAPI target;
    protected SharedGhostMoteAIData data;

    protected float sourceRepel;
    protected float sourceRejoin;
    protected float sourceCohesion;

    /**
     * @param missile
     * @param sourceOverrideTime
     */
    public GhostMoteAIScript(MissileAPI missile, float sourceOverrideTime) {
        this.missile = missile;
        r = (float) Math.random();
        elapsed = -(float) Math.random() * sourceOverrideTime;

        data = GhostPossessed.getSharedData(missile.getSource());

        if (missile.getSource() != null) {
            ShipAPI source = missile.getSource();
            this.sourceRejoin = source.getCollisionRadius() + SOURCE_REJOIN_RANGE;
            this.sourceRepel = source.getCollisionRadius() + SOURCE_REPEL_RANGE;
            this.sourceCohesion = source.getCollisionRadius() + SOURCE_COHESION_RANGE;
        }

        updateHardAvoidList();
    }

    public void updateHardAvoidList() {
        hardAvoidList.clear();

        CollisionGridAPI grid = Global.getCombatEngine().getAiGridShips();
        Iterator<Object> iter = grid.getCheckIterator(missile.getLocation(), MAX_HARD_AVOID_RANGE * 2f, MAX_HARD_AVOID_RANGE * 2f);
        while (iter.hasNext()) {
            Object o = iter.next();
            if (!(o instanceof ShipAPI)) continue;

            ShipAPI ship = (ShipAPI) o;
            if (ship.isFighter()) continue;
            hardAvoidList.add(ship);
        }

        grid = Global.getCombatEngine().getAiGridAsteroids();
        iter = grid.getCheckIterator(missile.getLocation(), MAX_HARD_AVOID_RANGE * 2f, MAX_HARD_AVOID_RANGE * 2f);
        while (iter.hasNext()) {
            Object o = iter.next();
            if (!(o instanceof CombatEntityAPI)) continue;

            CombatEntityAPI asteroid = (CombatEntityAPI) o;
            hardAvoidList.add(asteroid);
        }
    }
    public void doFlocking(float elapsed) {
        if (missile.getSource() == null) return;

        ShipAPI source = missile.getSource();
        CombatEngineAPI engine = Global.getCombatEngine();

        float avoidRange = AVOID_RANGE;
        float cohesionRange = COHESION_RANGE;

        float sin = (float) Math.sin(data.elapsed * 1f);
        float mult = 1f + sin * 0.25f;
        avoidRange *= mult;

        Vector2f total = new Vector2f();
        Vector2f attractor = getAttractorLoc();

        if (attractor != null) {
            float dist = Misc.getDistance(missile.getLocation(), attractor);
            Vector2f dir = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(missile.getLocation(), attractor));
            float f = dist / 200f;
            if (f > 1f) f = 1f;
            dir.scale(f * 3f);
            Vector2f.add(total, dir, total);

            avoidRange *= 3f;
        }

        boolean hardAvoiding = false;
        for (CombatEntityAPI other : hardAvoidList) {
            float dist = Misc.getDistance(missile.getLocation(), other.getLocation());
            float hardAvoidRange = other.getCollisionRadius() + avoidRange + 50f;
            if (dist < hardAvoidRange) {
                Vector2f dir = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(other.getLocation(), missile.getLocation()));
                float f = 1f - dist / (hardAvoidRange);
                dir.scale(f * 5f);
                Vector2f.add(total, dir, total);
                hardAvoiding = f > 0.5f;
            }
        }

        for (MissileAPI otherMissile : data.motes) {
            if (otherMissile == missile) continue;

            float dist = Misc.getDistance(missile.getLocation(), otherMissile.getLocation());
            float w = otherMissile.getMaxHitpoints();
            w = 1f;

            float currCohesionRange = cohesionRange;

            if (dist < avoidRange && otherMissile != missile && !hardAvoiding) {
                Vector2f dir = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(otherMissile.getLocation(), missile.getLocation()));
                float f = 1f - dist / avoidRange;
                dir.scale(f * w);
                Vector2f.add(total, dir, total);
            }

            if (dist < currCohesionRange) {
                Vector2f dir = new Vector2f(otherMissile.getVelocity());
                Misc.normalise(dir);
                float f = 1f - dist / currCohesionRange;
                dir.scale(f * w);
                Vector2f.add(total, dir, total);
            }

//			if (dist < cohesionRange && dist > avoidRange) {
//				//Vector2f dir = Utils.getUnitVectorAtDegreeAngle(Utils.getAngleInDegrees(missile.getLocation(), mote.getLocation()));
//				Vector2f dir = Utils.getUnitVectorAtDegreeAngle(Utils.getAngleInDegrees(mote.getLocation(), missile.getLocation()));
//				float f = dist / cohesionRange - 1f;
//				dir.scale(f * 0.5f);
//				Vector2f.add(total, dir, total);
//			}
        }

        if (missile.getSource() != null) {
            float dist = Misc.getDistance(missile.getLocation(), source.getLocation());
            if (dist > sourceRejoin) {
                Vector2f dir = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(missile.getLocation(), source.getLocation()));
                float f = dist / (sourceRejoin  + SOURCE_REJOIN_SCALING_BASE) - 1f;
                dir.scale(f * 0.5f);
                Vector2f.add(total, dir, total);
            }

            if (dist < sourceRepel && elapsed > 0) {
                Vector2f dir = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(source.getLocation(), missile.getLocation()));
                float f = 1f - dist / sourceRepel;
                dir.scale(f * 5f);
                Vector2f.add(total, dir, total);
            }

            if (dist < sourceCohesion) {
                if (source.getVelocity().length() > 20f) {
                    Vector2f dir = new Vector2f(source.getVelocity());
                    Misc.normalise(dir);
                    float f = 1f - dist / sourceCohesion;
                    dir.scale(f * 1f);
                    Vector2f.add(total, dir, total);
                }
            }

            // if not strongly going anywhere, circle the source ship; only kicks in for lone motes
            if (total.length() <= 0.05f) {
                float offset = r > 0.5f ? 90f : -90f;
                Vector2f dir = Misc.getUnitVectorAtDegreeAngle(
                        Misc.getAngleInDegrees(missile.getLocation(), source.getLocation()) + offset);
                float f = 1f;
                dir.scale(f * 1f);
                Vector2f.add(total, dir, total);
            }
        }

        if (total.length() > 0) {
            float dir = Misc.getAngleInDegrees(total);
            engine.headInDirectionWithoutTurning(missile, dir, 10000);

            if (r > 0.5f) {
                missile.giveCommand(ShipCommand.TURN_LEFT);
            } else {
                missile.giveCommand(ShipCommand.TURN_RIGHT);
            }
            missile.getEngineController().forceShowAccelerating();
        }
    }

    //public void accumulate(FlockingData data, Vector2f )


    protected IntervalUtil flutterCheck = new IntervalUtil(2f, 4f);
    protected FaderUtil currFlutter = null;
    protected float flutterRemaining = 0f;

    protected float elapsed = 0f;

    /**
     * @param amount
     */
    public void advance(float amount) {
        if (missile.isFizzling()) return;
        if (missile.getSource() ==  null) return;
        elapsed += amount;

        updateListTracker.advance(amount);
        if (updateListTracker.intervalElapsed()) {
            updateHardAvoidList();
        }

        //missile.getEngineController().getShipEngines().get(0).

        if (flutterRemaining <= 0) {
            flutterCheck.advance(amount);
            if (flutterCheck.intervalElapsed() &&
                    ((float) Math.random() > 0.9f ||
                            (data.attractorLock != null && (float) Math.random() > 0.5f))) {
                flutterRemaining = 2f + (float) Math.random() * 2f;
            }
        }

//		if (flutterRemaining > 0) {
//			flutterRemaining -= amount;
//			if (currFlutter == null) {
//				float min = 1/15f;
//				float max = 1/4f;
//				float dur = min + (max - min) * (float) Math.random();
//				//dur *= 0.5f;
//				currFlutter = new FaderUtil(0f, dur/2f, dur/2f, false, true);
//				currFlutter.fadeIn();
//			}
//			currFlutter.advance(amount);
//			if (currFlutter.isFadedOut()) {
//				currFlutter = null;
//			}
//		} else {
//			currFlutter = null;
//		}
//
//		if (currFlutter != null) {
//			missile.setGlowRadius(currFlutter.getBrightness() * 30f);
//		} else {
//			missile.setGlowRadius(0f);
//		}
//		if (true) {
//			doFlocking();
//			return;
//		}

        if (elapsed >= 0.5) {
            boolean wantToFlock = !isTargetValid();
            if (data.attractorLock != null) {
                float dist = Misc.getDistance(missile.getLocation(), data.attractorLock.getLocation());
                if (dist > data.attractorLock.getCollisionRadius() + ATTRACTOR_LOCK_STOP_FLOCKING_ADD) {
                    wantToFlock = true;
                }
            }

            if (wantToFlock) {
                doFlocking(elapsed);
            } else {
                CombatEngineAPI engine = Global.getCombatEngine();
                Vector2f targetLoc = engine.getAimPointWithLeadForAutofire(missile, 1.5f, target, 50);
                engine.headInDirectionWithoutTurning(missile,
                        Misc.getAngleInDegrees(missile.getLocation(), targetLoc),
                        10000);
                //AIUtils.turnTowardsPointV2(missile, targetLoc);
                if (r > 0.5f) {
                    missile.giveCommand(ShipCommand.TURN_LEFT);
                } else {
                    missile.giveCommand(ShipCommand.TURN_RIGHT);
                }
                missile.getEngineController().forceShowAccelerating();
            }
        }

        tracker.advance(amount);
        if (tracker.intervalElapsed()) {
            if (elapsed >= 0.5f) {
                acquireNewTargetIfNeeded();
            }

        }
    }

    @SuppressWarnings("unchecked")
    protected boolean isTargetValid() {
        if (target == null || (target instanceof ShipAPI && ((ShipAPI)target).isPhased())) {
            return false;
        }
        CombatEngineAPI engine = Global.getCombatEngine();

        if (target != null && target instanceof ShipAPI && ((ShipAPI)target).isHulk()) return false;

        List list = null;
        if (target instanceof ShipAPI) {
            list = engine.getShips();
        } else {
            list = engine.getMissiles();
        }
        return target != null && list.contains(target) && target.getOwner() != missile.getOwner();
    }

    protected void acquireNewTargetIfNeeded() {
        if (data.attractorLock != null) {
            target = data.attractorLock;
            return;
        }

        CombatEngineAPI engine = Global.getCombatEngine();

        // want to: target nearest missile that is not targeted by another two motes already
        int owner = missile.getOwner();

        int maxMotesPerMissile = 2;
        float maxDistFromSourceShip = this.sourceCohesion + MAX_DIST_FROM_SOURCE_TO_ENAGAGE_AS_PD_ADD;
        float maxDistFromAttractor = MAX_DIST_FROM_ATTRACTOR_TO_ENGAGE_AS_PD;

        float minDist = Float.MAX_VALUE;
        CombatEntityAPI closest = null;
        for (MissileAPI other : engine.getMissiles()) {
            if (other.getOwner() == owner) continue;
            if (other.getOwner() == 100) continue;
            float distToTarget = Misc.getDistance(missile.getLocation(), other.getLocation());

            if (distToTarget > minDist) continue;
            if (distToTarget > 3000 && !engine.isAwareOf(owner, other)) continue;

            float distFromAttractor = Float.MAX_VALUE;
            if (data.attractorTarget != null) {
                distFromAttractor = Misc.getDistance(other.getLocation(), data.attractorTarget);
            }
            float distFromSource = Misc.getDistance(other.getLocation(), missile.getSource().getLocation());
            if (distFromSource > maxDistFromSourceShip &&
                    distFromAttractor > maxDistFromAttractor) continue;

            if (getNumMotesTargeting(other) >= maxMotesPerMissile) continue;
            if (distToTarget < minDist) {
                closest = other;
                minDist = distToTarget;
            }
        }

        for (ShipAPI other : engine.getShips()) {
            if (other.getOwner() == owner) continue;
            if (other.getOwner() == 100) continue;
            if (!other.isFighter()) continue;
            float distToTarget = Misc.getDistance(missile.getLocation(), other.getLocation());
            if (distToTarget > minDist) continue;
            if (distToTarget > 3000 && !engine.isAwareOf(owner, other)) continue;

            float distFromAttractor = Float.MAX_VALUE;
            if (data.attractorTarget != null) {
                distFromAttractor = Misc.getDistance(other.getLocation(), data.attractorTarget);
            }
            float distFromSource = Misc.getDistance(other.getLocation(), missile.getSource().getLocation());
            if (distFromSource > maxDistFromSourceShip &&
                    distFromAttractor > maxDistFromAttractor) continue;

            if (getNumMotesTargeting(other) >= maxMotesPerMissile) continue;
            if (distToTarget < minDist) {
                closest = other;
                minDist = distToTarget;
            }
        }
        target = closest;
    }

    protected int getNumMotesTargeting(CombatEntityAPI other) {
        int count = 0;
        for (MissileAPI mote : data.motes) {
            if (mote == missile) continue;
            if (mote.getUnwrappedMissileAI() instanceof GhostMoteAIScript ai) {
                if (ai.getTarget() == other) {
                    count++;
                }
            }
        }
        return count;
    }

    public Vector2f getAttractorLoc() {
        Vector2f attractor = null;
        if (data.attractorTarget != null) {
            attractor = data.attractorTarget;
            if (data.attractorLock != null) {
                attractor = data.attractorLock.getLocation();
            }
        }
        return attractor;
    }

    public CombatEntityAPI getTarget() {
        return target;
    }

    public void setTarget(CombatEntityAPI target) {
        this.target = target;
    }
    public void render() {

    }
}
