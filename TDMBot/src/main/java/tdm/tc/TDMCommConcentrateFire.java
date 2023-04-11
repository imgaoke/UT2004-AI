package tdm.tc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.AnnotationListenerRegistrator;
import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.EventListener;
import cz.cuni.amis.pogamut.base.utils.logging.LogCategory;
import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Item;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.ItemPickedUp;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Player;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.PlayerKilled;
import cz.cuni.amis.pogamut.ut2004.teamcomm.bot.UT2004TCClient;
import tdm.TDMBot;
import tdm.tc.msgs.TCConcentrateFire;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004BotModuleController;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004BotController;



/**
 * Communication module for the TDM Bot for negotiating what items will team be picking up.
 * 
 * @author Jimmy
 *
 * @param <BOTCTRL> Context class of the action. It's an shared object used by
 * all primitives. it is used as a shared memory and for interaction with the
 * environment.
 */
public class TDMCommConcentrateFire<BOTCTRL extends TDMBot> {
	
	private AnnotationListenerRegistrator listenerRegistrator;
	
	private BOTCTRL ctx;
	
	private UT2004TCClient comm;
	
	private LogCategory log;
	
	
	public TDMCommConcentrateFire(BOTCTRL ctx) {
		this.ctx = ctx;
		this.log = this.ctx.getBot().getLogger().getCategory("TDMCommConcentratedFire");
		this.comm = this.ctx.getTCClient();
		//this.localCurrentTargetItem = this.ctx.currentTargetItem;
		
		// INITIALIZE @EventListener SNOTATED LISTENERS
		listenerRegistrator = new AnnotationListenerRegistrator(this, ctx.getWorldView(), ctx.getBot().getLogger().getCategory("Listeners"));
		listenerRegistrator.addListeners();
	}
	
//  EXAMPLE HOW TO RECEIVE MESSAGE	
//	@EventListener(eventClass=ItemPickedUp.class)
//	public void itemPickedUp(ItemPickedUp msg) {				
//	}
	
	
	private void sayGlobal(String msg) {
    	// Simple way to send msg into the UT2004 chat
    	ctx.getBody().getCommunication().sendGlobalTextMessage(msg);
    	// And user log as well
    	log.info(msg);
    }
	
	
	Player visibleTarget = null;
	//Map<UnrealId,UnrealId> teammatesTargeting = new HashMap<>();
	@EventListener(eventClass=TCConcentrateFire.class)
    public void TCItemPursuing(TCConcentrateFire event) {
    	
		
    	if (event.getTarget() != null) {
    		Map<UnrealId, Player> enemies = ctx.getPlayers().getVisibleEnemies();
    		visibleTarget = enemies.get(event.getTarget());
    		if (visibleTarget != null) {
    			Double targetDistance = ctx.getInfo().getDistance(visibleTarget);
    			if (targetDistance < 2000) {
    				ctx.getNavigation().setFocus(visibleTarget);
    				sayGlobal("fire it together");
    			}
    			else {
    				sayGlobal("too far");
    			}
    		}
    	}
    	else {
    		sayGlobal("can't see the target");
    	}
    }
	
	@EventListener(eventClass = PlayerKilled.class)
    public void playerKilled(PlayerKilled event) {
    	UnrealId botDiedId = event.getId();
    	if (botDiedId == null) return;
    	
    	if (visibleTarget != null && botDiedId == visibleTarget.getId()) {
    		visibleTarget = null;
    		ctx.getNavigation().setFocus(null);
    	}
    	
    	
    }

	/**
	 * Called regularly from {@link CTFBotContext#logicBeforePlan()}.
	 */
	public void update() {
		if (!comm.isConnected()) {
			log.warning("Not connected to TC server yet...");
			return;
		}

	}

//  EXAMPLE HOW TO SEND A MESSAGE TO OTHERS IN THE TEAM	
//	private void sendMe() {
//		comm.sendToTeamOthers(new TCPlayerUpdate(ctx.getInfo()));
//	}

}
