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
import cz.cuni.amis.pogamut.ut2004.teamcomm.bot.UT2004TCClient;
import tdm.TDMBot;
import tdm.tc.msgs.TCItemPursuing;
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
public class TDMCommItems<BOTCTRL extends TDMBot> {
	
	private AnnotationListenerRegistrator listenerRegistrator;
	
	private BOTCTRL ctx;
	
	private UT2004TCClient comm;
	
	private LogCategory log;
	
	private Item localCurrentTargetItem;	
	
	public TDMCommItems(BOTCTRL ctx) {
		this.ctx = ctx;
		this.log = this.ctx.getBot().getLogger().getCategory("TDMCommItems");
		this.comm = this.ctx.getTCClient();
		this.localCurrentTargetItem = this.ctx.currentTargetItem;
		
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
	
	
	Map<UnrealId,UnrealId> teammatesChasing = new HashMap<>();
	double currentFitness;
	@EventListener(eventClass=TCItemPursuing.class)
    public void TCItemPursuing(TCItemPursuing event) {
    
		/*
		if (event != null) {
			
			if (event.getWhat() != null) {
				
				sayGlobal("I received: " + event.getWhat().toString());
			}
			else {
				sayGlobal("null1");
			}
			
		}
		else {
			sayGlobal("null2");
		}
		if (localCurrentTargetItem != null) {
			sayGlobal("I have: " + localCurrentTargetItem.getId().toString());
		}
		*/
		
		
		
		
    	if (localCurrentTargetItem != null && event.getWhat() != null && event.getWhat().toString().equals(localCurrentTargetItem.getId().toString())) {
    		sayGlobal("chasing same item");
    		currentFitness = ctx.getNavMeshModule().getAStarPathPlanner().getDistance(ctx.getBot(), localCurrentTargetItem);
    		if (event.getFitness() > currentFitness){
    			comm.sendToTeamOthers(new TCItemPursuing(ctx.getInfo().getId(), localCurrentTargetItem.getId(), currentFitness));
    		}
    		else if (event.getFitness() < currentFitness) {
    			teammatesChasing.put(event.getWho(), event.getWhat());
    			while (ctx.nth < 15) {
    				ctx.nth += 1;
        			Item nextTarget = ctx.getNthTargetItem();
        			if (localCurrentTargetItem.getId() != nextTarget.getId()) {
        				boolean targetAvailable = true;
        	    		for (UnrealId who : teammatesChasing.keySet()) {
        	   				if (nextTarget.getId() == teammatesChasing.get(who)) {
        	   					targetAvailable = false;
        	   					break;
        	   				}
        	   			}
        	   			if (targetAvailable) {
        	   				localCurrentTargetItem = nextTarget;
            				currentFitness = ctx.getNavMeshModule().getAStarPathPlanner().getDistance(ctx.getBot(), localCurrentTargetItem);
                			comm.sendToTeamOthers(new TCItemPursuing(ctx.getInfo().getId(), localCurrentTargetItem.getId(), currentFitness));  
                			break;
        	   			}	
        			}  			
    			}
    			
    			if (ctx.nth == 15) {
    				ctx.nth = 0;
        			ctx.currentTargetItem = null;
        			comm.sendToTeamOthers(new TCItemPursuing(ctx.getInfo().getId(), null, Double.MIN_VALUE));
    			}
    		}
    	}
    	else {
    		//sayGlobal("chasing different item");
    		teammatesChasing.put(event.getWho(), event.getWhat());
    	}
    }
	
	@EventListener(eventClass=ItemPickedUp.class)
	public void itemPickedUp(ItemPickedUp msg) {
		comm.sendToTeamOthers(new TCItemPursuing(ctx.getInfo().getId(), null, Double.MIN_VALUE));
	}

	/**
	 * Called regularly from {@link CTFBotContext#logicBeforePlan()}.
	 */
	public void update() {
		if (!comm.isConnected()) {
			log.warning("Not connected to TC server yet...");
			return;
		}
		localCurrentTargetItem = ctx.currentTargetItem;
		
		/*
		if (localCurrentTargetItem != null) {
			currentFitness = ctx.getNavMeshModule().getAStarPathPlanner().getDistance(ctx.getBot(), localCurrentTargetItem);
			comm.sendToTeamOthers(new TCItemPursuing(ctx.getInfo().getId(), localCurrentTargetItem.getId(), currentFitness));
		}
		*/
		
		/*
		if (localCurrentTargetItem == null) {
			sayGlobal("null");
		}
		else {
			sayGlobal(localCurrentTargetItem.getType().toString());
		}
		*/
		
		// PERIODIC INFO
	}

//  EXAMPLE HOW TO SEND A MESSAGE TO OTHERS IN THE TEAM	
//	private void sendMe() {
//		comm.sendToTeamOthers(new TCPlayerUpdate(ctx.getInfo()));
//	}

}
