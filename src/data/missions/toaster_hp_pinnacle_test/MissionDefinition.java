package data.missions.toaster_hp_pinnacle_test;

import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.combat.EscapeRevealPlugin;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;


public class MissionDefinition implements MissionDefinitionPlugin {

	public void defineMission(MissionDefinitionAPI api) {
		// Set up the fleets so we can add ships and fighter wings to them.
		// In this scenario, the fleets are attacking each other, but
		// in other scenarios, a fleet may be defending or trying to escape
		api.initFleet(FleetSide.PLAYER, "ISS", FleetGoal.ATTACK, false, 5);
		api.initFleet(FleetSide.ENEMY, "TTS", FleetGoal.ATTACK, true);

		// Set a small blurb for each fleet that shows up on the mission detail and
		// mission results screens to identify each side.
		api.setFleetTagline(FleetSide.PLAYER, "Independent Survey Expedition");
		api.setFleetTagline(FleetSide.ENEMY, "Tri-Tachyon Special Acquisitions Team");
		
		// These show up as items in the bulleted list under 
		// "Tactical Objectives" on the mission detail screen
		api.addBriefingItem("Drive off the Tri-Tachyon fleet.");

		// Set up the player's fleet.  Variant names come from the
		// files in data/variants and data/variants/fighters
		//api.addToFleet(FleetSide.PLAYER, "harbinger_Strike", FleetMemberType.SHIP, "TTS Invisible Hand", true);
		api.addToFleet(FleetSide.PLAYER, "toaster_hp_pinnacle_Exploration", FleetMemberType.SHIP, "ISS Pride of Maxios", true);

		// Set up the enemy fleet.
		api.addToFleet(FleetSide.ENEMY, "medusa_PD", FleetMemberType.SHIP,  true);
		
		// Set up the map.
		float width = 24000f;
		float height = 18000f;
		api.initMap((float)-width/2f, (float)width/2f, (float)-height/2f, (float)height/2f);
		
		float minX = -width/2;
		float minY = -height/2;

        for (int i = 0; i < 25; i++) {
            float x = (float) Math.random() * width - width/2;
            float y = (float) Math.random() * height - height/2;
            float radius = 1000f + (float) Math.random() * 1000f;
            api.addNebula(x, y, radius);
        }

        api.addNebula(minX + width * 0.8f - 2000, minY + height * 0.4f, 2000);
        api.addNebula(minX + width * 0.8f - 2000, minY + height * 0.5f, 2000);
        api.addNebula(minX + width * 0.8f - 2000, minY + height * 0.6f, 2000);

		BattleCreationContext context = new BattleCreationContext(
				null, null, null, null
		);
	}
}




