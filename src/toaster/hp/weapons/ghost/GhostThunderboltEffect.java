package toaster.hp.weapons.ghost;

import java.util.ArrayList;
import java.util.List;

import java.awt.Color;

import com.fs.starfarer.api.combat.*;
import org.hyperlib.FXColours;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.EmpArcEntityAPI.EmpArcParams;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.util.Misc;
import toaster.hp.campaign.ids.Weapons;


/**
 * Multiple instances of this plugin - one for every projectile (on hit), and one for each weapon.
 * <p>
 * The goal is for the on-hit effect to fire off a lightning arc in case of a hit, and for the onfire/every frame copy
 * of the plugin to fire off a lightning arc in case there is a miss.
 *
 * @author Alex
 *
 */
@SuppressWarnings("unused")
public class GhostThunderboltEffect  implements OnHitEffectPlugin, OnFireEffectPlugin, EveryFrameWeaponEffectPlugin {
    public static Color RIFT_LIGHTNING_COLOR = FXColours.DEEP_HYPERSPACE_STRIKE;
    public static float RIFT_LIGHTNING_SPEED = 10000f;

    public static String RIFT_LIGHTNING_DAMAGE_REMOVER = Weapons.GHOST_THUNDERBOLT+"_damage_remover";
    public static String RIFT_LIGHTNING_FIRED_TAG = Weapons.GHOST_THUNDERBOLT+"_fired_tag";
    public static String RIFT_LIGHTNING_SOURCE_WEAPON = Weapons.GHOST_THUNDERBOLT+"_source_weapon";

    public static final float EDGE_OFFSET_COLLISION_RADIUS_MULT = 0.15f;

    public static class FiredLightningProjectile {
        public DamagingProjectileAPI projectile;
    }

    protected List<FiredLightningProjectile> fired = new ArrayList<>();

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine != null && engine.isInFastTimeAdvance()) {
            return;
        }

        List<FiredLightningProjectile> remove = new ArrayList<>();

        float maxRange = weapon.getRange();
        for (FiredLightningProjectile data : fired) {
            float dist = Misc.getDistance(data.projectile.getSpawnLocation(), data.projectile.getLocation());
            boolean firedAlready = data.projectile.getCustomData().containsKey(RIFT_LIGHTNING_FIRED_TAG);
            if (dist > maxRange || firedAlready) {
                remove.add(data);
                if (!firedAlready) {
                    fireArc(data.projectile, weapon, null, null);
                }
            }
        }
        fired.removeAll(remove);
    }

    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
//		if (weapon.getShip() != null &&
//				!weapon.getShip().hasListenerOfClass(RiftLightningBaseDamageNegator.class)) {
//			weapon.getShip().addListener(new RiftLightningBaseDamageNegator());
//		}
        //projectile.setCustomData(RIFT_LIGHTNING_PROJ_TAG, true);

        projectile.getDamage().getModifier().modifyMult(RIFT_LIGHTNING_DAMAGE_REMOVER, 0f);
        projectile.setCustomData(RIFT_LIGHTNING_SOURCE_WEAPON, weapon);

        FiredLightningProjectile data = new FiredLightningProjectile();
        data.projectile = projectile;
        fired.add(data);
    }

    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target,
                      Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {

        WeaponAPI weapon = (WeaponAPI) projectile.getCustomData().get(RIFT_LIGHTNING_SOURCE_WEAPON);
        if (weapon == null) return;

        fireArc(projectile, weapon, point, target);
    }

    public static void fireArc(
            DamagingProjectileAPI projectile, WeaponAPI weapon, Vector2f point, CombatEntityAPI target
    ) {
        boolean firedAlready = projectile.getCustomData().containsKey(RIFT_LIGHTNING_FIRED_TAG);
        if (firedAlready) return;

        projectile.setCustomData(RIFT_LIGHTNING_FIRED_TAG, true);

        CombatEngineAPI engine = Global.getCombatEngine();

        ShipAPI ship = weapon.getShip();
        if (ship == null) return;

        Vector2f from = projectile.getSpawnLocation();

        float dist = Float.MAX_VALUE;
        if (point != null) dist = Misc.getDistance(from, point);

        float maxRange = weapon.getRange();
        if (dist > maxRange || point == null) {
            dist = maxRange * (0.5f + 0.5f * (float) Math.random());
            if (projectile.didDamage()) {
                dist = maxRange;
            }
            point = Misc.getUnitVectorAtDegreeAngle(projectile.getFacing());
            point.scale(dist);
            Vector2f.add(point, from, point);
        }

        float arcSpeed = RIFT_LIGHTNING_SPEED;

        EmpArcParams params = new EmpArcParams();
        params.segmentLengthMult = 8f;
        params.zigZagReductionFactor = 0.05f;
//        params.zigZagReductionFactor = 0.25f;
        params.fadeOutDist = 50f;
        params.minFadeOutMult = 10f;
//		params.flickerRateMult = 0.7f;
        params.flickerRateMult = 0.3f;
//		params.flickerRateMult = 0.05f;
//		params.glowSizeMult = 3f;
//		params.brightSpotFullFraction = 0.5f;
        params.movementDurOverride = Math.max(0.05f, dist / arcSpeed);

        EmpArcEntityAPI arc = engine.spawnEmpArcVisual(from, ship, point, null,
                80f, // thickness
                weapon.getSpec().getGlowColor(), Color.WHITE,
                params
        );
        arc.setCoreWidthOverride(40f);
        arc.setRenderGlowAtStart(false);
        arc.setFadedOutAtStart(true);
        arc.setSingleFlickerMode(true);
        spawnMine(ship, point, params.movementDurOverride * 0.8f); // - 0.05f);
    }

    public static void spawnMine(ShipAPI source, Vector2f mineLoc, float delay) {
        CombatEngineAPI engine = Global.getCombatEngine();

        MissileAPI mine = (MissileAPI) engine.spawnProjectile(
                source, null,
                Weapons.GHOST_THUNDERBOLT_MINELAYER,
                mineLoc,
                (float) Math.random() * 360f, null
        );
        if (source != null) {
            Global.getCombatEngine().applyDamageModifiersToSpawnedProjectileWithNullWeapon(
                    source, WeaponAPI.WeaponType.ENERGY, false, mine.getDamage()
            );
        }

        float fadeInTime = 0.05f;
        mine.getVelocity().scale(0);
        mine.fadeOutThenIn(fadeInTime);

        float liveTime = Math.max(delay, 0f);
        mine.setFlightTime(mine.getMaxFlightTime() - liveTime);
        mine.addDamagedAlready(source);
        mine.setNoMineFFConcerns(true);
        if (liveTime <= 0.016f) {
            mine.explode();
        }
    }
}
