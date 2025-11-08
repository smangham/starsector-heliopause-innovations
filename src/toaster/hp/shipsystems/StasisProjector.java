package toaster.hp.shipsystems;

import java.util.List;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipSystemAPI.SystemState;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import toaster.hp.campaign.ids.ShipSystems;

/**
 * Based off of the entropy amplifier, with graphics & stats borrowed from the temporal shell.
 *
 * @author Alex originally.
 * @author Toaster modified.
 */
public class StasisProjector extends BaseShipSystemScript {
    public static final String ID = ShipSystems.STASIS_FIELD;

	public static Object KEY_TARGET = new Object();

	protected static final float RANGE = 1500f;

    public static final float TIME_RATE_MIN = 0.05f;  /// The slowest the target's time rate goes (not 0 just in case of /0 errors...)

    public static final String FLOATING_TEXT = Global.getSettings().getString(ID, "floating_text");  /// The text shown over the system's target.
    public static final String STATUS_USER_TEXT = Global.getSettings().getString(ID, "status_user");  /// The status text shown when the player uses the system.
    public static final String STATUS_TARGET_TEXT = Global.getSettings().getString(ID, "status_target");  /// The status text shown if the player is the target of the system.
    public static final String INFO_TEXT_READY = Global.getSettings().getString(ID, "info_ready");  /// The system info text shown when it's ready to use.
    public static final String INFO_TEXT_OUT_OF_RANGE = Global.getSettings().getString(ID, "info_out_of_range");  /// The system info text shown when the mouse is out of range.
    public static final String INFO_TEXT_NO_TARGET = Global.getSettings().getString(ID, "info_no_target");  /// The system info text shown when there's no target.

    public static final Color JITTER_COLOR = new Color(90,165,255,55);
    public static final Color JITTER_UNDER_COLOR = new Color(90,165,255,155);
    public static final float JITTER_UNDER_BONUS = 7f;
    public static final float JITTER_MAX_RANGE_BONUS = 10f;

    /**
     * Ramps down the time rate to a minimum value.
     *
     * @param effectLevel The effect level, 0-1 whilst fading in, 1-0 whilst fading out.
     * @return The effective time rate for that effect, caps at 95%.
     */
    public static float getTimeRate(float effectLevel) {
        return TIME_RATE_MIN + (1f - (effectLevel * effectLevel)) * (1f - TIME_RATE_MIN);
    }

    /**
     * How much the damage reduction, projectile speed e.t.c. is reduced.
     *
     * @param effectLevel The effect level, 0-1 whilst fading in, 1-0 whilst fading out.
     * @return The damage reduction, uncapped (so 100% at full operation)
     */
    public static float getDamageReduction(float effectLevel) {
        return 1f - (effectLevel * effectLevel);
    }

    /**
     * How strong the jitter effect is.
     *
     * @param effectLevel The system effect level.
     * @param state The system state.
     * @param chargeDur The charge-up duration of the system.
     * @return The jitter magnitude from 0-1.
     */
    public static float getJitterLevel(float effectLevel, State state, float chargeDur) {
        float jitterLevel = effectLevel;

        if (state == State.IN) {
            jitterLevel = MathUtils.clamp(jitterLevel, effectLevel / (1f / chargeDur), 1f);
        } else if (state == State.ACTIVE) {
            jitterLevel = 1f;
        }
        return (float) Math.sqrt(jitterLevel);
    }

    /**
     * Holds the data for the system's operation.
     * The system apparently can't hold data on its own script (?).
     */
	public static class TargetData {
		public ShipAPI ship;
		public ShipAPI target;
		public EveryFrameCombatPlugin targetEffectPlugin;
		public float currTimeMult;
        public float currDamageMult;
		public float jitterLevel;
        public float engineFadeLevel;
        public State state;
		public TargetData(ShipAPI ship, ShipAPI target) {
			this.ship = ship;
			this.target = target;
		}
	}

    /**
     * Called every frame to update the target data to apply to the targeted ship.
     *
     * @param stats The stats of the ship the system is on.
     * @param id The id of this system, for modifiers.
     * @param state The state of the system (IN/OUT/ACTIVE/IDLE/COOLDOWN).
     * @param effectLevel The effect level, from 0-1 when IN, 1 when ACTIVE, then 1-0 when OUT.
     */
	public void apply(MutableShipStatsAPI stats, final String id, State state, float effectLevel) {
		ShipAPI ship;
		if (stats.getEntity() instanceof ShipAPI) {
			ship = (ShipAPI) stats.getEntity();
		} else {
			return;
		}

        CombatEngineAPI engine = Global.getCombatEngine();

		final String targetDataKey = ship.getId() + "_" + ID;
		
		Object targetDataObj = engine.getCustomData().get(targetDataKey);

		if (state == State.IN && targetDataObj == null) {
			ShipAPI target = findTarget(ship);
            engine.getCustomData().put(targetDataKey, new TargetData(ship, target));
			if (target != null) {
				if (target.getFluxTracker().showFloaty() || ship == engine.getPlayerShip() || target == engine.getPlayerShip()) {
					target.getFluxTracker().showOverloadFloatyIfNeeded(
                            FLOATING_TEXT, Misc.getHighlightColor(), 4f, true
                    );
				}
			}
		} else if (state == State.IDLE && targetDataObj != null) {
            engine.getCustomData().remove(targetDataKey);
			((TargetData)targetDataObj).currTimeMult = 1f;
            ((TargetData)targetDataObj).currDamageMult = 1f;
            ((TargetData)targetDataObj).jitterLevel = 0f;
            ((TargetData)targetDataObj).engineFadeLevel = 0f;
			targetDataObj = null;
		}
		if (targetDataObj == null || ((TargetData) targetDataObj).target == null) return;
		
		final TargetData targetData = (TargetData) targetDataObj;
		targetData.currTimeMult = getTimeRate(effectLevel);
        targetData.currDamageMult = getDamageReduction(effectLevel);
        targetData.state = state;
        targetData.engineFadeLevel = effectLevel * effectLevel;
        targetData.jitterLevel = getJitterLevel(effectLevel, state, ship.getSystem().getChargeUpDur());

		if (targetData.targetEffectPlugin == null) {
			targetData.targetEffectPlugin = new BaseEveryFrameCombatPlugin() {
				@Override
				public void advance(float amount, List<InputEventAPI> events) {
                    CombatEngineAPI engine = Global.getCombatEngine();

					if (engine.isPaused()) return;

					if (targetData.target == engine.getPlayerShip()) {
                        engine.maintainStatusForPlayerShip(
                                KEY_TARGET,
								targetData.ship.getSystem().getSpecAPI().getIconSpriteName(),
								targetData.ship.getSystem().getDisplayName(), 
								"" + (int)(getTimeRate(targetData.currTimeMult) * 100f) + " " + STATUS_TARGET_TEXT, true
                        );
					}
					
					if (targetData.currDamageMult == 1f || !targetData.ship.isAlive()) {
                        engine.getTimeMult().unmodifyMult(id);
                        targetData.target.getMutableStats().getTimeMult().unmodifyMult(id);
						targetData.target.getMutableStats().getHullDamageTakenMult().unmodify(id);
						targetData.target.getMutableStats().getArmorDamageTakenMult().unmodify(id);
						targetData.target.getMutableStats().getShieldDamageTakenMult().unmodify(id);
						targetData.target.getMutableStats().getEmpDamageTakenMult().unmodify(id);
                        targetData.target.getMutableStats().getProjectileSpeedMult().unmodify(id);
                        engine.removePlugin(targetData.targetEffectPlugin);

					} else {
                        targetData.target.getMutableStats().getTimeMult().modifyMult(id, targetData.currTimeMult);
						targetData.target.getMutableStats().getHullDamageTakenMult().modifyMult(id, targetData.currDamageMult);
						targetData.target.getMutableStats().getArmorDamageTakenMult().modifyMult(id, targetData.currDamageMult);
						targetData.target.getMutableStats().getShieldDamageTakenMult().modifyMult(id, targetData.currDamageMult);
						targetData.target.getMutableStats().getEmpDamageTakenMult().modifyMult(id, targetData.currDamageMult);
                        targetData.target.getMutableStats().getProjectileSpeedMult().modifyMult(id, targetData.currDamageMult);
                        targetData.target.setJitter(
                                this, JITTER_COLOR, targetData.jitterLevel,
                                3, 0, 0 + targetData.jitterLevel *  JITTER_MAX_RANGE_BONUS
                        );
                        targetData.target.setJitterUnder(
                                this, JITTER_UNDER_COLOR, targetData.jitterLevel,
                                25, 0f, JITTER_UNDER_BONUS + targetData.jitterLevel *  JITTER_MAX_RANGE_BONUS
                        );
                        targetData.target.getEngineController().fadeToOtherColor(
                                this, JITTER_COLOR, JITTER_COLOR, targetData.engineFadeLevel, 0.5f
                        );
					}

                    if (state == State.IN || state == State.ACTIVE) {
                        targetData.target.getEngineController().fadeToOtherColor(
                                this, JITTER_COLOR, JITTER_COLOR, 1f - targetData.currDamageMult, 1f
                        );
                    }
				}
			};
            engine.addPlugin(targetData.targetEffectPlugin);
		}
	}

    /**
     * Never called as the script runs whilst idle (why?)
     *
     * @param stats The stats of the ship.
     * @param id The id of this effect.
     */
	public void unapply(MutableShipStatsAPI stats, String id) {
		
	}

    /**
     * Finds a valid target for the ship system.
     * <p>
     * Mostly just pulls the target from the system AI, but if that's not set/the user is the player finds a valid ship.
     *
     * @param ship The ship to find the target for.
     * @return The target ship.
     */
	protected ShipAPI findTarget(ShipAPI ship) {
		float range = getMaxRange(ship);
		boolean player = ship == Global.getCombatEngine().getPlayerShip();
		ShipAPI target = ship.getShipTarget();
		
		if (ship.getShipAI() != null && ship.getAIFlags().hasFlag(AIFlags.TARGET_FOR_SHIP_SYSTEM)){
			target = (ShipAPI) ship.getAIFlags().getCustom(AIFlags.TARGET_FOR_SHIP_SYSTEM);
			if (target != null && target.getOriginalOwner() == ship.getOriginalOwner()) target = null;
		}

		if (target != null) {
			float dist = Misc.getDistance(ship.getLocation(), target.getLocation());
			float radSum = ship.getCollisionRadius() + target.getCollisionRadius();
			if (dist > range + radSum) target = null;

		} else {
            if (player) {
                Misc.FindShipFilter filter =  s -> true;
                target = Misc.findClosestShipTo(
                        ship, ship.getMouseTarget(), HullSize.FRIGATE, range,
                        true, false, filter
                );

            } else {
                Object test = ship.getAIFlags().getCustom(AIFlags.MANEUVER_TARGET);
                if (test instanceof ShipAPI) {
                    target = (ShipAPI) test;
                    float dist = Misc.getDistance(ship.getLocation(), target.getLocation());
                    float radSum = ship.getCollisionRadius() + target.getCollisionRadius();
                    if (dist > range + radSum || target.isFighter()) target = null;
                    if (target != null && target.getOriginalOwner() == ship.getOriginalOwner()) target = null;
                }
            }

		}
		
		if (target != null && target.isFighter()) target = null;
		if (target == null) {
			target = Misc.findClosestShipEnemyOf(ship, ship.getLocation(), HullSize.FRIGATE, range, true);
		}
		
		return target;
	}

    /**
     * Gets the effective range of the system.
     *
     * @param ship The ship this system is on.
     * @return The range of the system.
     */
	public static float getMaxRange(ShipAPI ship) {
		return ship.getMutableStats().getSystemRangeBonus().computeEffective(RANGE);
	}

    /**
     * Gets the status of the system to show to the player when piloting.
     *
     * @param index The index to show the status at?
     * @param state The current state (IN/OUT e.t.c.)
     * @param effectLevel The effect of the system, 0-> 1 -> 0 IN/ACTIVE/OUT.
     * @return The text to show when the system is in use.
     */
	public StatusData getStatusData(int index, State state, float effectLevel) {
		if (effectLevel > 0) {
			if (index == 0) {
				return new StatusData(
                        "" + (int)(getTimeRate(effectLevel)* 100f) + "% " +STATUS_USER_TEXT, false
                );
			}
		}
		return null;
	}

    /**
     * Shows the info for the ship system when player-piloted.
     *
     * @param system The system definition.
     * @param ship The ship the system is on.
     * @return A string describing the target status of the system.
     */
	@Override
	public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
		if (system.isOutOfAmmo()) return null;
		if (system.getState() != SystemState.IDLE) return null;
		
		ShipAPI target = findTarget(ship);
		if (target != null && target != ship) {
			return INFO_TEXT_READY;
		}
		if ((target == null) && ship.getShipTarget() != null) {
			return INFO_TEXT_OUT_OF_RANGE;
		}
		return INFO_TEXT_NO_TARGET;
	}

    /**
     * Can the system currently be used?
     *
     * @param system The system definition.
     * @param ship The ship the system is on.
     * @return True if there's a valid target.
     */
	@Override
	public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
		//if (true) return true;
		ShipAPI target = findTarget(ship);
		return target != null && target != ship;
	}
}
