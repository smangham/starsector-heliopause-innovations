package org.hyperlib.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import org.lwjgl.util.vector.Vector2f;

import java.util.LinkedHashMap;

/**
 * Class that determines the X & Y extent of an entity to evenly sample points from within.
 * <p>
 * Exists as some ships are very long, so just randomly sampling on a circle and accept/reject is inefficient.
 */
public class HyperLibCollision {
    public static final String KEY_SHIP_MAP = "HyperLibCollision_shipMap";

    @SuppressWarnings("unchecked")
    protected static LinkedHashMap<ShipAPI, ShipPointSampler> getShipSamplerMap() {
        LinkedHashMap<ShipAPI, ShipPointSampler> map =
                (LinkedHashMap<ShipAPI, ShipPointSampler>) Global.getCombatEngine().getCustomData().get(KEY_SHIP_MAP);
        if (map == null) {
            map = new LinkedHashMap<>();
            Global.getCombatEngine().getCustomData().put(KEY_SHIP_MAP, map);
        }
        return map;
    }

    protected static ShipPointSampler getShipSampler(ShipAPI ship) {
        ShipPointSampler sampler = getShipSamplerMap().getOrDefault(ship, null);
        if (sampler == null) {
            sampler = new ShipPointSampler(ship);
            getShipSamplerMap().put(ship, sampler);
        }
        return sampler;
    }

    public static Vector2f getBoundsPoint(ShipAPI ship) {
        return getShipSampler(ship).getBoundsPoint();
    }

    public static Vector2f getInternalPoint(ShipAPI ship) {
        return getShipSampler(ship).getInternalPoint();
    }

    public static Vector2f getInternalPointDistantFrom(ShipAPI ship, Vector2f point, float radius) {
        return getShipSampler(ship).getInternalPointDistantFrom(point, radius);
    }

    public static Vector2f getEdgePoint(ShipAPI ship) {
        return getShipSampler(ship).getEdgePoint();
    }

    public static float getMinDimension(ShipAPI ship) { return getShipSampler(ship).getMinDimension(); }
}
