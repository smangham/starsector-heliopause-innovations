package toaster.hp.world.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseMissionHub;
import org.apache.log4j.Logger;
import toaster.hp.campaign.ids.*;

import java.util.Objects;


/**
 * Initialises mod content in the Westernesse system.
 * <p>
 * Pretty basic thing.
 */
public class HeliopauseWesternesse {
    /**
     * Generates the mod submarkets e.t.c. in the system.
     * <p>
     * This is a little awkward because the market key "ailmar" isn't consistent so we have to find directly.
     *
     * @param sector The active sector.
     */
    public void generate(SectorAPI sector) {
        FactionAPI heliopause_faction = sector.getFaction(HPFactions.HELIOPAUSE);
        MarketAPI market = null;

        for (PlanetAPI planet : sector.getStarSystem("Westernesse").getPlanets()) {
            if (Objects.equals(planet.getName(), "Ailmar")) {
                market = planet.getMarket();
            }
        }

        if (market == null) {
//            log.warn("tstHP: No market initialised for Ailmar, somehow?");
        } else {
            // Find the submarket, or create it.
            if (market.getSubmarket(Submarkets.HELIOPAUSE) == null) {
//                log.info("tstHP: Creating submarket at Westernesse.");
                market.addSubmarket(Submarkets.HELIOPAUSE);
                SubmarketAPI submarket = market.getSubmarket(Submarkets.HELIOPAUSE);
                submarket.setFaction(heliopause_faction);
            }

            // Find the CEO, or create them
            boolean hasCEO = false;
            for (PersonAPI person : market.getPeopleCopy()) {
                if (Objects.equals(person.getId(), People.HELIOPAUSE_CEO)) hasCEO = true;
            }
            if (!hasCEO) {
//                log.info("tstHP: Creating CEO at Westernesse.");
                PersonAPI person = heliopause_faction.createRandomPerson(FullName.Gender.MALE);
                person.setId(People.HELIOPAUSE_CEO);
                person.setVoice(Voices.SCIENTIST);
                person.setRankId(Ranks.FACTION_LEADER);
                person.setPostId(Ranks.POST_FACTION_LEADER);
                person.setImportance(PersonImportance.VERY_HIGH);
                person.setPortraitSprite(Global.getSettings().getSpriteName(
                        HPFactions.HELIOPAUSE, "portrait_ceo")
                );
                market.getCommDirectory().addPerson(person, 4);
                market.addPerson(person);
                Global.getSector().getImportantPeople().addPerson(person);
                BaseMissionHub.set(person, new BaseMissionHub(person));
            }
        }
    }
}
