package toaster.hp.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;


public class EnergyAttractor extends BaseShipSystemScript {
    private static final Logger log = Logger.getLogger(EnergyAttractor.class);

    private static final float PULSE_INTERVAL_SECONDS = 0.1f;
    private static final float PULSE_RANGE = 256.0f;
    private static final float ATTRACTOR_TURN_RATE = 45.0f;

    private float pulseCountdown = 0.0f;
    private ShipAPI ship;

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        //stats.getShieldTurnRateMult().modifyMult(id, 1f);
        //stats.getShieldUnfoldRateMult().modifyPercent(id, 2000);


        float amount;
        if (Global.getCombatEngine().isPaused()) {
            amount = 0f;
        } else {
            amount = Global.getCombatEngine().getElapsedInLastFrame();
        }

        pulseCountdown -= amount;
        if (pulseCountdown <= 0) {
            if (stats.getEntity() instanceof ShipAPI) {
                ship = (ShipAPI) stats.getEntity();
            } else {
                return;
            }

            Vector2f location_ship = ship.getLocation();
            float turn_rate = ATTRACTOR_TURN_RATE * PULSE_INTERVAL_SECONDS * effectLevel;
            float projectile_facing;
            float bearing_to_ship;

            pulseCountdown = PULSE_INTERVAL_SECONDS;
            log.info("Interval happened");

            for (DamagingProjectileAPI damagingProjectile: CombatUtils.getProjectilesWithinRange(ship.getLocation(), PULSE_RANGE)) {
                projectile_facing = damagingProjectile.getFacing();
                bearing_to_ship = MathUtils.clampAngle(projectile_facing + Misc.getAngleInDegrees(damagingProjectile.getLocation(), location_ship));
                bearing_to_ship = MathUtils.clamp(bearing_to_ship, bearing_to_ship-turn_rate, bearing_to_ship+turn_rate);
                damagingProjectile.setFacing(projectile_facing + bearing_to_ship);
                log.info("Projectile "+damagingProjectile+" turning "+bearing_to_ship);
            }
        }

//        //stats.getShieldDamageTakenMult().modifyMult(id, 0.1f);
//        stats.getShieldDamageTakenMult().modifyMult(id, 1f - DAMAGE_MULT * effectLevel);
//
//        stats.getShieldUpkeepMult().modifyMult(id, 0f);
    }

    public void unapply(MutableShipStatsAPI stats, String id) {
        //stats.getShieldAbsorptionMult().unmodify(id);
        stats.getShieldArcBonus().unmodify(id);
        stats.getShieldDamageTakenMult().unmodify(id);
        stats.getShieldTurnRateMult().unmodify(id);
        stats.getShieldUnfoldRateMult().unmodify(id);
        stats.getShieldUpkeepMult().unmodify(id);
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("shield absorbs 10x damage", false);
        }
        return null;
    }
}
