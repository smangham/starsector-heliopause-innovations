package toaster.hp.weapons.ghost;

import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.ProximityExplosionEffect;
import com.fs.starfarer.api.combat.ShipAPI;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import toaster.hp.weapons.stormcaller.StormcallerParams;

/**
 * Applies the knockback.
 */
@SuppressWarnings("unused")
public class GhostThunderboltMineExplosion implements ProximityExplosionEffect {
    public static float STRIKE_IMPULSE = 350f;

    /**
     * Effect run when the explosion explodes, before damage is dealt.
     *
     * @param explosion             The explosion that happened.
     * @param originalProjectile    The projectile that exploded.
     */
	public void onExplosion(DamagingProjectileAPI explosion, DamagingProjectileAPI originalProjectile) {
        for (ShipAPI ship: CombatUtils.getShipsWithinRange(explosion.getLocation(), explosion.getCollisionRadius())) {
            CombatUtils.applyForce(
                    ship,
                    VectorUtils.getDirectionalVector(originalProjectile.getSource().getLocation(), ship.getLocation()),
                    STRIKE_IMPULSE
            );
        }
	}
}



