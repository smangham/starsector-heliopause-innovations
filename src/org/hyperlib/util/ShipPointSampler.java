package org.hyperlib.util;

import com.fs.starfarer.api.combat.BoundsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

/**
 *
 */
class ShipPointSampler {
    protected final float x_base, y_base, x_mult, y_mult;
    protected final ShipAPI ship;

    /**
     * @param shipAPI The entity to use the bounds of.
     */
    protected ShipPointSampler(ShipAPI shipAPI) {
        float x_min = 9999f, x_max = -9999f, y_min = 9999f, y_max = -9999f;
        BoundsAPI bounds = shipAPI.getExactBounds();
        for (BoundsAPI.SegmentAPI segment : bounds.getOrigSegments()) {
            if (segment.getP1().x > x_max) x_max = segment.getP1().x;
            if (segment.getP1().x < x_min) x_min = segment.getP1().x;
            if (segment.getP1().y > y_max) y_max = segment.getP1().y;
            if (segment.getP1().y < y_min) y_min = segment.getP1().y;
            if (segment.getP2().x > x_max) x_max = segment.getP2().x;
            if (segment.getP2().x < x_min) x_min = segment.getP2().x;
            if (segment.getP2().y > y_max) y_max = segment.getP2().y;
            if (segment.getP2().y < y_min) y_min = segment.getP2().y;
        }
        ship = shipAPI;
        x_base = x_min;
        x_mult = x_max - x_min;
        y_base = y_min;
        y_mult = y_max - y_min;
    }

    /**
     * @return A point within the ship's bounds, or its centre if sampling fails.
     */
    protected Vector2f getInternalPoint() {
        Vector2f point = new Vector2f();
        float sin = (float) FastTrig.sin(Math.toRadians(ship.getFacing()));
        float cos = (float) FastTrig.cos(Math.toRadians(ship.getFacing()));

        for (int i = 0; i < HyperLibVector.RANDOM_POINT_MAX_TRIES; i++) {
            Vector2f.add(
                    ship.getLocation(),
                    new Vector2f(
                            cos * (x_base + x_mult * (float) Math.random()),
                            sin * (y_base + y_mult * (float) Math.random())
                    ),
                    point
            );
            if (CollisionUtils.isPointWithinBounds(point, ship)) return point;
        }
        return new Vector2f(ship.getLocation());
    }

    protected Vector2f getBoundsPoint() {
        List<BoundsAPI.SegmentAPI> segments = ship.getExactBounds().getSegments();
        return segments.get(MathUtils.getRandomNumberInRange(0, segments.size() - 1)).getP1();
    }

    protected Vector2f getEdgePoint() {
        List<BoundsAPI.SegmentAPI> segments = ship.getExactBounds().getSegments();
        BoundsAPI.SegmentAPI segment = segments.get(MathUtils.getRandomNumberInRange(0, segments.size() - 1));
        float random = (float) Math.random();

        return Vector2f.add(
                (Vector2f) new Vector2f(segment.getP1()).scale(random),
                (Vector2f) new Vector2f(segment.getP2()).scale(1f - random),
                new Vector2f()
        );
    }
}
