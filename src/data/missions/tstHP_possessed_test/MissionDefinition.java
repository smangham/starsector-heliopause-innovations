package data.missions.tstHP_possessed_test;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.combat.EscapeRevealPlugin;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import com.fs.starfarer.api.util.Misc;
import toaster.hp.campaign.ids.Variants;

public class MissionDefinition implements MissionDefinitionPlugin {
	public void defineMission(MissionDefinitionAPI api) {
		// Set up the fleets so we can add ships and fighter wings to them.
		// In this scenario, the fleets are attacking each other, but
		// in other scenarios, a fleet may be defending or trying to escape
		api.initFleet(FleetSide.PLAYER, "ISS", FleetGoal.ESCAPE, false, 5);
		api.initFleet(FleetSide.ENEMY, "ISS", FleetGoal.ATTACK, true);

		// Set a small blurb for each fleet that shows up on the mission detail and
		// mission results screens to identify each side.
		api.setFleetTagline(FleetSide.PLAYER, "Charter of Opis Survey Expedition");
		api.setFleetTagline(FleetSide.ENEMY, "Drifting Hulks");
		
		// These show up as items in the bulleted list under 
		// "Tactical Objectives" on the mission detail screen
		api.addBriefingItem("Escape the mysterious derelicts.");

		// Set up the player's fleet.  Variant names come from the
		// files in data/variants and data/variants/fighters
		api.addToFleet(FleetSide.PLAYER, Variants.PINNACLE_EXPLORATION, FleetMemberType.SHIP, "ISS Pride of Opis", true);
        api.addToFleet(FleetSide.PLAYER, "venture_Exploration", FleetMemberType.SHIP, false);
        api.addToFleet(FleetSide.PLAYER, "hammerhead_Balanced", FleetMemberType.SHIP, false);
        api.addToFleet(FleetSide.PLAYER, "wayfarer_Standard", FleetMemberType.SHIP, false);
        api.addToFleet(FleetSide.PLAYER, "vigilance_Support1", FleetMemberType.SHIP, false);

		// Set up the enemy fleet.
        api.addToFleet(FleetSide.ENEMY, Variants.POSSESSED_VENTURE, FleetMemberType.SHIP, true);
        api.addToFleet(FleetSide.ENEMY, Variants.POSSESSED_STARLINER, FleetMemberType.SHIP, false);
        api.addToFleet(FleetSide.ENEMY, Variants.POSSESSED_TARSUS, FleetMemberType.SHIP, false);
        api.addToFleet(FleetSide.ENEMY, Variants.POSSESSED_TARSUS, FleetMemberType.SHIP, false);
        api.addToFleet(FleetSide.ENEMY, Variants.POSSESSED_MULE, FleetMemberType.SHIP, false);
        api.addToFleet(FleetSide.ENEMY, Variants.POSSESSED_SHEPHERD, FleetMemberType.SHIP, false);
        api.addToFleet(FleetSide.ENEMY, Variants.POSSESSED_SHEPHERD, FleetMemberType.SHIP, false);
        api.addToFleet(FleetSide.ENEMY, Variants.POSSESSED_SHEPHERD, FleetMemberType.SHIP, false);

        // Set up the map.
		float width = 18000f;
		float height = 18000f;
		api.initMap((float)-width/2f, (float)width/2f, (float)-height/2f, (float)height/2f);
		
		float minX = -width/2f;
		float minY = -height/2f;

        for (int i = 0; i < 10; i++) {
            float x = (float) Math.random() * width - width/2;
            float y = (float) Math.random() * height - height/2;
            float radius = 750f + (float) Math.random() * 500f;
            api.addNebula(x, y, radius);
        }

        api.addNebula(minX + width * 0.8f - 2000, minY + height * 0.4f, 1500f);
        api.addNebula(minX + width * 0.8f - 2000, minY + height * 0.5f, 1500f);
        api.addNebula(minX + width * 0.8f - 2000, minY + height * 0.6f, 1500f);

		BattleCreationContext context = new BattleCreationContext(
				null, null, null, null
		);
        context.setInitialEscapeRange(7000f);
        api.addPlugin(new EscapeRevealPlugin(context));
	}
}
