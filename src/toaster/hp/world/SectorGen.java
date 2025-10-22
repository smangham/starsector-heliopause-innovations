package toaster.hp.world;
import com.fs.starfarer.api.campaign.SectorAPI;
import toaster.hp.world.systems.HeliopauseWesternesse;

/**
 * Generates the mod content.
 * <p>
 * Pretty simple.
 */
public class SectorGen {
    /**
     * Generates the mod content.
     *
     * @param sector    The active sector.
     */
    public void generate(SectorAPI sector) {
        new HeliopauseWesternesse().generate(sector);
    }
}
