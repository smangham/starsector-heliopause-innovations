package org.hyperlib.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BoundsAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import toaster.hp.hullmods.GhostPossessed;

import java.util.*;


/**
 *
 */
public class HyperLibVector {
    /**
     * How many times to try picking a random point before giving up.
     */
    public static final float RANDOM_POINT_MAX_TRIES = 8;
    public static final Random RANDOM = new Random();

    /**
     * @param entity    The combat entity to calculate the offsets for.
     * @param offsets   The sprite offsets, e.g. from a ship bounds.
     * @return  The offsets translated into worldspace coords for that ship.
     */
    public static Vector2f getWorldspaceForEntitySpriteOffsets(
            CombatEntityAPI entity, Vector2f offsets
    ) {
        return Vector2f.add(
                entity.getLocation(),
                VectorUtils.rotate(offsets, entity.getFacing() + 90f),
                new Vector2f()
        );
    }

    /**
     * Picks a point within an entity's bounds.
     *
     * @param entity    The entity to pick a point within.
     * @return  A point within the ship's bounds. Its centre if the random selection fails.
     */
    public static Vector2f getRandomPointInBounds(CombatEntityAPI entity) {
        Vector2f point;

        for (int i=0; i<RANDOM_POINT_MAX_TRIES; i++) {
            point = Misc.getPointWithinRadiusUniform(entity.getLocation(), entity.getCollisionRadius(), RANDOM);
            if (CollisionUtils.isPointWithinBounds(point, entity)) return point;
        }
        return new Vector2f(entity.getLocation());
    }

    /**
     * Picks a point within an entity's bounds, but outside of an exclusion zone.
     *
     * @param entity        The entity to pick a point within.
     * @param excludePoint  The point not to pick near.
     * @param excludeRadius The radius to exclude around that point.
     * @return  A point within the ship's bounds, but not within the exclusion zone.
     *          Its centre if the random selection fails.
     */
    public static Vector2f getRandomPointInBoundsExclusionZone(
            CombatEntityAPI entity, Vector2f excludePoint, float excludeRadius
    ) {
        Vector2f point;

        for (int i=0; i<RANDOM_POINT_MAX_TRIES; i++) {
            point = Misc.getPointWithinRadiusUniform(entity.getLocation(), entity.getCollisionRadius(), RANDOM);
            if (Misc.getDistance(point, excludePoint) < excludeRadius
                    && CollisionUtils.isPointWithinBounds(point, entity)) return point;
        }
        return new Vector2f(entity.getLocation());
    }

    /**
     * Picks a point within an entity's bounds, and within a radius of a point.
     *
     * @param entity        The entity to pick a point within.
     * @param includePoint  The point to pick near.
     * @param includeRadius The radius around that point to pick.
     * @return  A point within the ship's bounds and within that radius.
     *          The point if selection fails.
     */
    public static Vector2f getRandomPointInBoundsInclusionZone(
            CombatEntityAPI entity, Vector2f includePoint, float includeRadius
    ) {
        Vector2f point;

        for (int i=0; i<RANDOM_POINT_MAX_TRIES; i++) {
            point = Misc.getPointWithinRadiusUniform(includePoint, includeRadius, RANDOM);
            if (CollisionUtils.isPointWithinBounds(point, entity)) return point;
        }
        return new Vector2f(includePoint);
    }

    /**
     * Creates a vector at a given length and direction.
     * <p>
     * More convenient than getUnitVectorAtDirection.
     *
     * @param angleDeg  The angle to create the vector at, in degrees.
     * @param length    The length of the vector.
     * @return  A vector of the given r and theta.
     */
    public static Vector2f getVectorForAngle(float angleDeg, float length) {
        return new Vector2f(
                (float) FastTrig.cos(Math.toRadians(angleDeg)) * length,
                (float) FastTrig.sin(Math.toRadians(angleDeg)) * length
        );
    }

    /**
     * Gets the starting location for an electrical arc, that's just inside an entity's bounds.
     *
     * @param location_end The point to which the arc is going.
     * @param entity       The entity from which the arc is coming.
     * @param offset       The distance into the entity to go.
     * @return  A point just inside the entity's physical edge, where the arc can begin.
     */
    public static Vector2f getEdgeLocation(Vector2f location_end, CombatEntityAPI entity, float offset) {
        Vector2f location_edge = CollisionUtils.getNearestPointOnBounds(location_end, entity);
        Vector2f direction =(Vector2f) VectorUtils.getDirectionalVector(location_end, location_edge).scale(offset);
        return getRandomPointInBoundsInclusionZone(
                entity, Vector2f.add(location_edge, direction, new Vector2f()), offset
        );
    }
}

