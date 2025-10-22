package toaster.hp;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import toaster.hp.campaign.ids.HullMods;
import toaster.hp.campaign.ids.Tags;

import java.util.ArrayList;
import java.util.List;


/**
 * Convenience functions for campaign-level ghost things
 */
public class GhostUtil {
    public static PersonAPI getGhostCaptain() {
        PersonAPI person = Global.getFactory().createPerson();
        person.setName(new FullName("Unknown", "", FullName.Gender.ANY));
        person.setFaction(com.fs.starfarer.api.impl.campaign.ids.Factions.NEUTRAL);
        person.setPortraitSprite(Global.getSettings().getSpriteName(HullMods.GHOST_POSSESSED, "captain"));
        person.setPersonality(Personalities.RECKLESS);
        person.setRankId(Ranks.UNKNOWN);
        person.setPostId(null);

        person.getStats().setSkipRefresh(true);

        person.getStats().setLevel(10);
        person.getStats().setSkillLevel(Skills.FIELD_MODULATION, 2);
        person.getStats().setSkillLevel(Skills.PHASE_CORPS, 2);
        person.getStats().setSkillLevel(Skills.IMPACT_MITIGATION, 2);
        person.getStats().setSkillLevel(Skills.ENERGY_WEAPON_MASTERY, 2);
        person.getStats().setSkillLevel(Skills.COMBAT_ENDURANCE, 2);
        person.getStats().setSkillLevel(Skills.POLARIZED_ARMOR, 2);
        person.getStats().setSkillLevel(Skills.MISSILE_SPECIALIZATION, 2);
        person.getStats().setSkillLevel(Skills.GUNNERY_IMPLANTS, 2);
        person.getStats().setSkillLevel(Skills.FIELD_MODULATION, 2);
        person.getStats().setSkillLevel(Skills.DAMAGE_CONTROL, 2);
        person.getStats().setSkillLevel(Skills.DERELICT_CONTINGENT, 2);
        person.getStats().setSkipRefresh(false);
        return person;
    }

    /**
     * Returns all weapons on the ship that are charge-based.
     *
     * @param ship      The ship to check.
     * @return          A list of weapons that use mote charges.
     */
    public static List<WeaponAPI> getChargedWeapons(ShipAPI ship) {
        ArrayList<WeaponAPI> found = new ArrayList<>();
        for (WeaponAPI weapon: ship.getAllWeapons()) {
            if (weapon.getSpec().hasTag(Tags.CHARGED_SYSTEM)) found.add(weapon);
        }
        return found;
    }
}
