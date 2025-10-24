package toaster.hp.weapons.ghost;

import java.util.Iterator;

import java.awt.Color;
import java.util.Random;

import org.hyperlib.util.HyperLibVector;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EmpArcEntityAPI;
import com.fs.starfarer.api.combat.EmpArcEntityAPI.EmpArcParams;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.Misc;

/**
 * Copy of the shock repeater, but with a larger arc.
 *
 * @author Alex originally
 * @author Toaster tweaked
 */
@SuppressWarnings("unused")
public class GhostStaticOnFireEffect implements OnFireEffectPlugin {
    public static float ARC_THICKNESS = 20f;
    public static float ARC_CORE_WIDTH_MULT = 0.67f;
    public static float EDGE_OFFSET_COLLISION_RADIUS_MULT = 0.15f;

    /**
     * When the gun fires, pick a target and arc.
     * <p>
     * Modification to cope with 360 degree angle with no slots.
     *
     * @param projectile The fake projectile fired.
     * @param weapon     The weapon firing.
     * @param engine     The combat engine.
     */
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        CombatEntityAPI target = findTarget(projectile, weapon, engine);

        // If we didn't have a source for this, we can't run it!
        ShipAPI ship = projectile.getSource();
        if (ship == null) return;

        EmpArcEntityAPI arc;
        EmpArcParams arcParams = new EmpArcEntityAPI.EmpArcParams();
        arcParams.fadeOutDist = 10f;

        if (target != null) {
            arc = engine.spawnEmpArc(
                    ship,
                    HyperLibVector.getEdgeLocation(
                            target.getLocation(), ship,
                            EDGE_OFFSET_COLLISION_RADIUS_MULT * ship.getCollisionRadius()
                    ),
                    ship,
                    target,
                    DamageType.ENERGY,
                    projectile.getDamageAmount(),
                    projectile.getEmpAmount(),
                    100000f,
                    "shock_repeater_emp_impact",
                    ARC_THICKNESS,
                    weapon.getSpec().getGlowColor(),
                    Color.WHITE,
                    arcParams
            );
        } else {
            Vector2f to = pickNoTargetDest(projectile, weapon, engine);
            Vector2f from = HyperLibVector.getEdgeLocation(
                    to, ship, EDGE_OFFSET_COLLISION_RADIUS_MULT * ship.getCollisionRadius()
            );
            arc = engine.spawnEmpArcVisual(
                    from, ship, to, ship, ARC_THICKNESS, weapon.getSpec().getGlowColor(), Color.white
            );
        }

        arc.setCoreWidthOverride(ARC_THICKNESS * ARC_CORE_WIDTH_MULT);
        arc.setSingleFlickerMode();
        arc.setFadedOutAtStart(true);
        arc.setRenderGlowAtStart(false);
    }

    /**
     * Modification to cope with 360 degree angle.
     * <p>
     * Tries to go close to the ship's current target if one,
     * otherwise picks a random location around the ship if there's no target.
     *
     * @param projectile The fake projectile fired.
     * @param weapon     The weapon firing.
     * @param engine     The combat engine.
     * @return A location either at a random position, or towards the ship's target.
     */
    public Vector2f pickNoTargetDest(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        float spread = weapon.getRange() / 5f;
        float range = weapon.getRange() - spread;
        ShipAPI ship = projectile.getSource();

        // Error handling
        if (ship == null) return projectile.getLocation();

        if (projectile.getSource().getShipTarget() != null) {
            Vector2f from = ship.getLocation();
            Vector2f dir = (Vector2f) VectorUtils.getDirectionalVector(
                    from, ship.getShipTarget().getLocation()
            ).scale(range);
            Vector2f.add(
                    from, dir, dir
            );
            return Misc.getPointWithinRadius(dir, spread);

        } else {
            return Misc.getPointWithinRadiusUniform(
                    ship.getLocation(), ship.getCollisionRadius(), range, new Random()
            );
        }
    }

    /**
     * Finds a target for the weapon.
     * <p>
     * Modification to remove with 360 degree angle.
     *
     * @param projectile The fake projectile.
     * @param weapon     The firing weapon.
     * @param engine     The combat engine.
     * @return The entity that the weapon most wants to shoot.
     */
    public CombatEntityAPI findTarget(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        float range = weapon.getRange();
        Vector2f from = projectile.getLocation();

        Iterator<Object> iter = Global.getCombatEngine().getAllObjectGrid().getCheckIterator(
                from, range * 2f, range * 2f
        );
        int owner = weapon.getShip().getOwner();
        CombatEntityAPI best = null;
        float minScore = Float.MAX_VALUE;

        ShipAPI ship = weapon.getShip();
        boolean ignoreFlares = ship != null && ship.getMutableStats().getDynamic().getValue(Stats.PD_IGNORES_FLARES, 0) >= 1;
        ignoreFlares |= weapon.hasAIHint(AIHints.IGNORES_FLARES);

        while (iter.hasNext()) {
            Object o = iter.next();
            if (!(o instanceof MissileAPI) &&
                    //!(o instanceof CombatAsteroidAPI) &&
                    !(o instanceof ShipAPI)) continue;
            CombatEntityAPI other = (CombatEntityAPI) o;
            if (other.getOwner() == owner) continue;

            if (other instanceof ShipAPI) {
                ShipAPI otherShip = (ShipAPI) other;
                if (otherShip.isHulk()) continue;
                if (otherShip.isPhased()) continue;
                if (!otherShip.isTargetable()) continue;
            }

            if (other.getCollisionClass() == CollisionClass.NONE) continue;

            if (ignoreFlares && other instanceof MissileAPI missile) {
                if (missile.isFlare()) continue;
            }

            float radius = Misc.getTargetingRadius(from, other, false);
            float dist = Misc.getDistance(from, other.getLocation()) - radius;
            if (dist > range) continue;

            float score = dist;

            if (score < minScore) {
                minScore = score;
                best = other;
            }
        }
        return best;
    }
}
