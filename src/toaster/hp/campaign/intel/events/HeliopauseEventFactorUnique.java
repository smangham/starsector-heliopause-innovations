package toaster.hp.campaign.intel.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseOneTimeFactor;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;
import toaster.hp.campaign.ids.Events;


/**
 * One-time unique event factor
 */
public class HeliopauseEventFactorUnique extends BaseOneTimeFactor {
    public static final String ID = Events.HELIOPAUSE_FACTOR_UNIQUE;
    private final String eventName;

    /**
     * Constructor
     * @param points        How many points of progress this factor adds.
     * @param eventName     The unique event name.
     */
    public HeliopauseEventFactorUnique(int points, String eventName) {
        super(points);
        this.eventName = eventName;
    }

    /**
     * Adds a short description shown in the intel event 'recent factors' list.
     *
     * @param intel     The intel event.
     * @return          The description, loaded from text 1 column of `strings.csv` for this ID.
     */
    @Override
    public String getDesc(BaseEventIntel intel) {
        return Global.getSettings().getDescription(ID, Description.Type.CUSTOM).getText1();
    }

    /**
     * Adds a more detailed tooltip description.
     *
     * @param intel     The intel event to create the tooltip for.
     * @return          The description, loaded from text 3 column of `strings.csv` for this ID.
     */
    @Override
    public TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        return new BaseFactorTooltip() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara(
                    Global.getSettings().getDescription(ID, Description.Type.CUSTOM).getText3(), 0f,
                        Misc.getHighlightColor(), eventName
                );
            }
        };
    }

}
