package tdm;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import javax.vecmath.Vector3d;

import cz.cuni.amis.pathfinding.alg.astar.AStarResult;
import cz.cuni.amis.pathfinding.map.IPFMapView;
import cz.cuni.amis.pogamut.base.agent.navigation.IPathFuture;
import cz.cuni.amis.pogamut.base.agent.navigation.impl.PrecomputedPathFuture;
import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.EventListener;
import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.ObjectClassEventListener;
import cz.cuni.amis.pogamut.base.communication.worldview.object.event.WorldObjectUpdatedEvent;
import cz.cuni.amis.pogamut.base.utils.guice.AgentScoped;
import cz.cuni.amis.pogamut.base.utils.math.DistanceUtils;
import cz.cuni.amis.pogamut.base3d.worldview.object.ILocated;
import cz.cuni.amis.pogamut.base3d.worldview.object.Location;
import cz.cuni.amis.pogamut.base3d.worldview.object.Rotation;
import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.agent.module.sensor.AgentInfo;
import cz.cuni.amis.pogamut.ut2004.agent.module.utils.UT2004Skins;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.NavigationState;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.levelGeometry.RayCastResult;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.navmesh.NavMeshClearanceComputer.ClearanceLimit;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.navmesh.pathfollowing.NavMeshNavigation;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004Bot;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004BotModuleController;
import cz.cuni.amis.pogamut.ut2004.communication.messages.ItemType;
import cz.cuni.amis.pogamut.ut2004.communication.messages.UT2004ItemType;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Initialize;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.BotDamaged;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.BotKilled;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.ConfigChange;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.GameInfo;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.HearNoise;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.IncomingProjectile;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.InitedMessage;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Item;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.ItemPickedUp;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.NavPoint;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Player;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.PlayerDamaged;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.PlayerKilled;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Self;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.TeamScore;
import cz.cuni.amis.pogamut.ut2004.teamcomm.bot.UT2004BotTCController;
import cz.cuni.amis.pogamut.ut2004.utils.UT2004BotRunner;
import cz.cuni.amis.utils.Cooldown;
import cz.cuni.amis.utils.ExceptionToString;
import cz.cuni.amis.utils.collections.MyCollections;
import cz.cuni.amis.utils.exception.PogamutException;
import cz.cuni.amis.utils.flag.FlagListener;
import math.geom2d.Vector2D;
import tdm.tc.TDMCommConcentrateFire;
import tdm.tc.TDMCommItems;
import tdm.tc.TDMCommObjectUpdates;
import tdm.tc.msgs.TCConcentrateFire;
import tdm.tc.msgs.TCItemPursuing;

/**
 * TDM BOT TEMPLATE CLASS
 * Version: 0.0.1
 */
@AgentScoped
public class TDMBot extends UT2004BotTCController<UT2004Bot> {

	private static Object CLASS_MUTEX = new Object();
	
	/**
	 * Whether to load Level Geometry information, slows down bot startup, thus you might
	 * consider disabling the load (== false) when developing behaviors not dependent on LevelGeometry.
	 */
	public static final boolean LEVEL_GEOMETRY_AUTOLOAD = true;
	
	/**
	 * TRUE => draws navmesh and terminates
	 */
	public static final boolean DRAW_NAVMESH = false;
	private static boolean navmeshDrawn = false;
	
	/**
	 * TRUE => rebinds NAVMESH+NAVIGATION GRAPH; useful when you add new map tweak into {@link MapTweaks}.
	 */
	public static final boolean UPDATE_NAVMESH = false;
	
	/**
	 * Whether to draw navigation path; works only if you are running 1 bot...
	 */
	public static final boolean DRAW_NAVIGATION_PATH = false;
	private boolean navigationPathDrawn = false;
	
	/**
	 * If true, all bots will enter RED team... 
	 */
	public static final boolean START_BOTS_IN_SINGLE_TEAM = false;
		
	/**
	 * How many bots we have started so far; used to split bots into teams.
	 */
	private static AtomicInteger BOT_COUNT = new AtomicInteger(0);
	/**
	 * How many bots have entered RED team.
	 */
	private static AtomicInteger BOT_COUNT_RED_TEAM = new AtomicInteger(0);
	/**
	 * How many bots have entered BLUE team.
	 */
	private static AtomicInteger BOT_COUNT_BLUE_TEAM = new AtomicInteger(0);
	
	/**
	 * 0-based; note that during the tournament all your bots will have botInstance == 0!
	 */
	private int botInstance = 0;
	
	/**
	 * 0-based; note that during the tournament all your bots will have botTeamInstance == 0!
	 */
	private int botTeamInstance = 0;
	
	private TDMCommItems<TDMBot> commItems;
	private TDMCommObjectUpdates<TDMBot> commObjectUpdates;
	private TDMCommConcentrateFire<TDMBot> commConcentratedFire;
	
    // =============
    // BOT LIFECYCLE
    // =============
    
    /**
     * Bot's preparation - called before the bot is connected to GB2004 and launched into UT2004.
     */
    @Override
    public void prepareBot(UT2004Bot bot) {       	
        // DEFINE WEAPON PREFERENCES
        initWeaponPreferences();
        
        // INITIALIZATION OF COMM MODULES
        commItems = new TDMCommItems<TDMBot>(this);
        commObjectUpdates = new TDMCommObjectUpdates<TDMBot>(this);
        commConcentratedFire = new TDMCommConcentrateFire<TDMBot>(this);
    }
    
    @Override
    protected void initializeModules(UT2004Bot bot) {
    	super.initializeModules(bot);
    	levelGeometryModule.setAutoLoad(LEVEL_GEOMETRY_AUTOLOAD);
    }
    
    /**
     * This is a place where you should use map tweaks, i.e., patch original Navigation Graph that comes from UT2004.
     */
    @Override
    public void mapInfoObtained() {
    	// See {@link MapTweaks} for details; add tweaks in there if required.
    	MapTweaks.tweak(navBuilder);    	
    	if (botInstance == 0) navMeshModule.setReloadNavMesh(UPDATE_NAVMESH);    	
    }
    
    /**
     * Define your weapon preferences here (if you are going to use weaponPrefs).
     */
    private void initWeaponPreferences() {
    	
    	weaponPrefs.newPrefsRange(100)
		.add(UT2004ItemType.SHIELD_GUN, true);
		weaponPrefs.newPrefsRange(400)
		.add(UT2004ItemType.FLAK_CANNON, true)
		.add(UT2004ItemType.LINK_GUN, true)
		.add(UT2004ItemType.BIO_RIFLE, true);
		weaponPrefs.newPrefsRange(1150)
		.add(UT2004ItemType.ROCKET_LAUNCHER, true)
		.add(UT2004ItemType.LINK_GUN, false)
		.add(UT2004ItemType.MINIGUN, true);
		weaponPrefs.newPrefsRange(2500)
		.add(UT2004ItemType.LIGHTNING_GUN, true)
		.add(UT2004ItemType.SHOCK_RIFLE, true);
		
    	weaponPrefs.addGeneralPref(UT2004ItemType.LIGHTNING_GUN, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.SHOCK_RIFLE, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.ROCKET_LAUNCHER, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.LINK_GUN, false);
        weaponPrefs.addGeneralPref(UT2004ItemType.LINK_GUN, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.MINIGUN, false);
        weaponPrefs.addGeneralPref(UT2004ItemType.MINIGUN, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.ASSAULT_RIFLE, true);        
        weaponPrefs.addGeneralPref(UT2004ItemType.FLAK_CANNON, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.BIO_RIFLE, true);
	}

	@Override
    public Initialize getInitializeCommand() {
    	// IT IS FORBIDDEN BY COMPETITION RULES TO CHANGE DESIRED SKILL TO DIFFERENT NUMBER THAN 6
    	// IT IS FORBIDDEN BY COMPETITION RULES TO ALTER ANYTHING EXCEPT NAME & SKIN VIA INITIALIZE COMMAND
		// Change the name of your bot, e.g., Jakub Gemrot would rewrite this to: targetName = "JakubGemrot"
		String targetName = "KeGao";
		botInstance = BOT_COUNT.getAndIncrement();
		
		int targetTeam = AgentInfo.TEAM_RED;
		if (!START_BOTS_IN_SINGLE_TEAM) {
			targetTeam = botInstance % 2 == 0 ? AgentInfo.TEAM_RED : AgentInfo.TEAM_BLUE;
		}
		switch (targetTeam) {
		case AgentInfo.TEAM_RED: 
			botTeamInstance = BOT_COUNT_RED_TEAM.getAndIncrement();  
			targetName += "-RED-" + botTeamInstance; 
			break;
		case AgentInfo.TEAM_BLUE: 
			botTeamInstance = BOT_COUNT_BLUE_TEAM.getAndIncrement(); 
			targetName += "-BLUE-" + botTeamInstance;
			break;
		}		
        return new Initialize().setName(targetName).setSkin(targetTeam == AgentInfo.TEAM_RED ? UT2004Skins.SKINS[0] : UT2004Skins.SKINS[UT2004Skins.SKINS.length-1]).setTeam(targetTeam).setDesiredSkill(6);
    }

    /**
     * Bot has been initialized inside GameBots2004 (Unreal Tournament 2004) and is about to enter the play
     * (it does not have the body materialized yet).
     *  
     * @param gameInfo
     * @param currentConfig
     * @param init
     */
    @Override
    public void botInitialized(GameInfo gameInfo, ConfigChange currentConfig, InitedMessage init) {
    	// INITIALIZE TABOO SETS, if you have them, HERE
    }

    // ==========================
    // EVENT LISTENERS / HANDLERS
    // ==========================
	
    /**
     * {@link PlayerDamaged} listener that senses that "some other bot was hurt".
     *
     * @param event
     */
    @EventListener(eventClass = PlayerDamaged.class)
    public void playerDamaged(PlayerDamaged event) {
    	UnrealId botHurtId = event.getId();
    	if (botHurtId == null) return;
    	
    	int damage = event.getDamage();
    	Player botHurt = (Player)world.get(botHurtId); // MAY BE NULL!
    	
    	//log.info("OTHER HURT: " + damage + " DMG to " + botHurtId.getStringId() + " [type=" + event.getDamageType() + ", weapon=" + event.getWeaponName() + "]");
    }
    
    /**
     * {@link BotDamaged} listener that senses that "I was hurt".
     *
     * @param event
     */
    @EventListener(eventClass = BotDamaged.class)
    public void botDamaged(BotDamaged event) {
    	int damage = event.getDamage();
    	
    	if (event.getInstigator() == null) {
    		//log.info("HURT: " + damage + " DMG done to ME [type=" + event.getDamageType() + ", weapon=" + event.getWeaponName() + "] by UNKNOWN");
    	} else {
    		UnrealId whoCauseDmgId = event.getInstigator();
    		Player player = (Player) world.get(whoCauseDmgId); // MAY BE NULL!
    		//log.info("HURT: " + damage + " DMG done to ME [type=" + event.getDamageType() + ", weapon=" + event.getWeaponName() + "] by " + whoCauseDmgId.getStringId());
    	}
    }
    
    /**
     * {@link PlayerKilled} listener that senses that "some other bot has died".
     *
     * @param event
     */
    @EventListener(eventClass = PlayerKilled.class)
    public void playerKilled(PlayerKilled event) {
    	UnrealId botDiedId = event.getId();
    	if (botDiedId == null) return;
    	
    	Player botDied = (Player) world.get(botDiedId);
    	
    	if (event.getKiller() == null) {
    		//log.info("OTHER DIED: " + botDiedId.getStringId() + ", UNKNOWN killer");
    	} else {
    		UnrealId killerId = event.getKiller();
    		if (killerId.equals(info.getId())) {
    			//log.info("OTHER KILLED: " + botDiedId.getStringId() + " by ME");
    		} else {
    			Player killer = (Player) world.get(killerId);
    			if (botDiedId.equals(killerId)) {
    				//log.info("OTHER WAS KILLED: " + botDiedId.getStringId() + " comitted suicide");
    			} else {
    				//log.info("OTHER WAS KILLED: " + botDiedId.getStringId() + " by " + killerId.getStringId());
    			}
    		}
    	}
    }
    
    /**
     * {@link BotKilled} listener that senses that "your bot has died".
     */
	@Override
	public void botKilled(BotKilled event) {
		if (event.getKiller() == null) {
			//log.info("DEAD");
		} else {
			UnrealId killerId = event.getKiller();
			Player killer = (Player) world.get(killerId);
			//log.info("KILLED by" + killerId.getStringId());
		} 
		reset();
	}
	
    /**
     * {@link HearNoise} listener that senses that "some noise was heard by the bot".
     *
     * @param event
     */
    @EventListener(eventClass = HearNoise.class)
    public void hearNoise(HearNoise event) {
    	double noiseDistance = event.getDistance();   // 100 ~ 1 meter
    	Rotation faceRotation = event.getRotation();  // rotate bot to this if you want to face the location of the noise
    	//log.info("HEAR NOISE: distance = " + noiseDistance);
    }
    
    /**
     * {@link ItemPickedUp} listener that senses that "your bot has picked up some item".
     * 
     * See sources for {@link ItemType} for details about item types / categories / groups.
     *
     * @param event
     */
    boolean currentTargetItemPicked = false;
    @EventListener(eventClass = ItemPickedUp.class)
    public void itemPickedUp(ItemPickedUp event) {
    	ItemType itemType = event.getType();
    	ItemType.Group itemGroup = itemType.getGroup();
    	ItemType.Category itemCategory = itemType.getCategory();
    	Item item = items.getItem(event.getId());
    	//log.info("PICKED " + itemCategory.name + ": " + itemType.getName() + " [group=" + itemGroup.getName() + "]");
    	
    	if (info.getSelf() == null) return; // ignore the first equipment...
    	Item pickedUp = items.getItem(event.getId());
    	if (pickedUp == null) return; // ignore unknown items
    	
    	if (currentTargetItem != null && currentTargetItem.getId() == pickedUp.getId()) {
    		currentTargetItemPicked = true;
    	}
    }
    
    /**
     * {@link IncomingProjectile} listener that senses that "some projectile has appeared OR moved OR disappeared".
     *
     * @param event
     */
    boolean projectileIncoming = false;
    Vector2D dodgeDirection = null;
    @ObjectClassEventListener(objectClass = IncomingProjectile.class, eventClass = WorldObjectUpdatedEvent.class)
    public void incomingProjectileUpdated(WorldObjectUpdatedEvent<IncomingProjectile> event) {
    	IncomingProjectile projectile = event.getObject();
    	// DO NOT SPAM... uncomment for debug
    	//log.info("PROJECTILE UPDATED: " + projectile);
    	
    	Location projectileLocation = event.getObject().getLocation();
    	Vector3d projectileDirection = event.getObject().getDirection();
    	Location projectileToBotDirection = info.getLocation().sub(projectileLocation).getNormalized();
    	Vector3d projectileHeadingDirectionVector3d = projectileDirection;
    	Location projectileHeadingDirection = new Location(projectileHeadingDirectionVector3d.getX(), projectileHeadingDirectionVector3d.getY(), projectileHeadingDirectionVector3d.getZ());
    	projectileHeadingDirection = projectileHeadingDirection.getNormalized();
    	Double projectileToBotDistance = info.getDistance(projectileLocation);
    	double attactAngle = Math.acos(projectileToBotDirection.dot(projectileHeadingDirection));
    	double brashingpastDistance = projectileToBotDistance * Math.tan(attactAngle);

    	if (attactAngle > 0 && attactAngle < Math.PI / 2 && brashingpastDistance <= 100) {
    		projectileIncoming = true;
    		
    		Vector2D optimalDodgeDirection = new Vector2D(projectileToBotDirection.sub(projectileHeadingDirection).getX(),projectileToBotDirection.sub(projectileHeadingDirection).getY());
    		Vector2D suboptimalDodgeDirection = optimalDodgeDirection.times(-1);
    		double optimalDodgeDirectionDistance = raycastNavMesh(info.getLocation(), optimalDodgeDirection);
    		double suboptimalDodgeDirectionDistance = raycastNavMesh(info.getLocation(), suboptimalDodgeDirection);
    		if (optimalDodgeDirectionDistance > 300) {
    			dodgeDirection = optimalDodgeDirection;
    		}
    		else if (suboptimalDodgeDirectionDistance > 300) {
    			dodgeDirection = suboptimalDodgeDirection;
    		}
    		else {
    			if (optimalDodgeDirectionDistance >= suboptimalDodgeDirectionDistance) {
    				dodgeDirection = optimalDodgeDirection;
    			}
    			else {
    				dodgeDirection = suboptimalDodgeDirection;
    			}
    		}
    		
    	} 
    }
    
    /**
     * {@link Player} listener that senses that "some other bot has appeared OR moved OR disappeared"
     *
     * WARNING: this method will also be called during handshaking GB2004.
     *
     * @param event
     */
    @ObjectClassEventListener(objectClass = Player.class, eventClass = WorldObjectUpdatedEvent.class)
    public void playerUpdated(WorldObjectUpdatedEvent<Player> event) {
    	if (info.getLocation() == null) {
    		// HANDSHAKING GB2004
    		return;
    	}
    	Player player = event.getObject();    	
    	// DO NOT SPAM... uncomment for debug
    	//log.info("PLAYER UPDATED: " + player.getId().getStringId());
    }
        
    
    /**
     * {@link TeamScore} listener that senses changes within scoring.
     *
     * @param event
     */
    @ObjectClassEventListener(objectClass = TeamScore.class, eventClass = WorldObjectUpdatedEvent.class)
    public void teamScoreUpdated(WorldObjectUpdatedEvent<TeamScore> event) {
    	switch (event.getObject().getTeam()) {
    	case AgentInfo.TEAM_RED: 
    		//log.info("RED TEAM SCORE UPDATED: " + event.getObject());
    		break;
    	case AgentInfo.TEAM_BLUE:
    		//log.info("BLUE TEAM SCORE UPDATED: " + event.getObject());
    		break;
    	}
    }
    
    
    private long selfLastUpdateStartMillis = 0;
    private long selfTimeDelta = 0;
    
    /**
     * {@link Self} object has been updated. This update is received about every 50ms. You can use this update
     * to fine-time some of your behavior like "weapon switching". I.e. SELF is updated every 50ms while LOGIC is invoked every 250ms.
     * 
     * Note that during "SELF UPDATE" only information about your bot location/rotation ({@link Self}) is updated. All other visibilities 
     * remains the same as during last {@link #logic()}.
     * 
     * Note that new {@link NavMeshNavigation} is using SELF UPDATES to fine-control the bot's navigation.
     * 
     * @param event
     */
    @ObjectClassEventListener(objectClass = Self.class, eventClass = WorldObjectUpdatedEvent.class)
    public void selfUpdated(WorldObjectUpdatedEvent<Self> event) {
    	if (lastLogicStartMillis == 0) {
    		// IGNORE ... logic has not been executed yet...
    		return;
    	}
    	if (selfLastUpdateStartMillis == 0) {
    		selfLastUpdateStartMillis = System.currentTimeMillis();
    		return;
    	}
    	long selfUpdateStartMillis = System.currentTimeMillis(); 
    	selfTimeDelta = selfUpdateStartMillis  - selfLastUpdateStartMillis;
    	selfLastUpdateStartMillis = selfUpdateStartMillis;
    	log.info("---[ SELF UPDATE | D: " + (selfTimeDelta) + "ms ]---");
    	
    	try {
    		
    		// YOUR CODE HERE
    		
    	} catch (Exception e) {
    		// MAKE SURE THAT YOUR BOT WON'T FAIL!
    		log.info(ExceptionToString.process(e));
    	} finally {
    		//log.info("---[ SELF UPDATE END ]---");
    	}
    	
    }
    
    /**
     * The navigation state has changed...
     * @param changedValue
     */
    private void navigationStateChanged(NavigationState changedValue) {
    	switch(changedValue) {
    	case TARGET_REACHED:
    		if (currentTargetItemPicked) {
    			nth = 0;
				currentTargetItemPicked = false;
				currentTargetItem = null;
				//draw.clearAll();
			}
    		else {
    			if (currentTargetItem != null && currentTargetItem.isVisible() && items.isPickable(currentTargetItem)) {
    				move.dodgeRelative(new Location(-1, 0, 0), false);
    			}
    			else {
    				nth += 1;
    				currentTargetItem = getNthTargetItem();
    			}	
    		}
    		return;
		case PATH_COMPUTATION_FAILED:
			nth += 1;
			currentTargetItem = getNthTargetItem();
			return;
		case STUCK:
			nth += 1;
			currentTargetItem = getNthTargetItem();
			navigation.navigate(currentTargetItem);
			return;
		}
    }

    // ==============
    // MAIN BOT LOGIC
    // ==============
    
    /**
     * Method that is executed only once before the first {@link TDMBot#logic()} 
     */
    @SuppressWarnings("unused")
	@Override
    public void beforeFirstLogic() {
    	lastLogicStartMillis = System.currentTimeMillis();
    	if (DRAW_NAVMESH && botInstance == 0) {
    		boolean drawNavmesh = false;
    		synchronized(CLASS_MUTEX) {
    			if (!navmeshDrawn) {
    				drawNavmesh = true;
    				navmeshDrawn = true;
    			}
    		}
    		if (drawNavmesh) {
    			log.warning("!!! DRAWING NAVMESH !!!");
    			navMeshModule.getNavMeshDraw().draw(true, true);
    			navmeshDrawn  = true;
    			log.warning("NavMesh drawn, waiting a bit to finish the drawing...");
    		}    		
    	}
    	
    	navigation.addStrongNavigationListener(new FlagListener<NavigationState>() {
			@Override
			public void flagChanged(NavigationState changedValue) {
				navigationStateChanged(changedValue);
			}
        });
    }
    
    private long lastLogicStartMillis = 0;
    private long lastLogicEndMillis = 0;
    private long timeDelta = 0;
    
    /**
     * Main method that controls the bot - makes decisions what to do next. It
     * is called iteratively by Pogamut engine every time a synchronous batch
     * from the environment is received. This is usually 4 times per second.
     * 
     * This is a typical place from where you start coding your bot. Even though bot
     * can be completely EVENT-DRIVEN, the reactive aproach via "ticking" logic()
     * method is more simple / straight-forward.
     */
    @Override
    public void logic() {
    	long logicStartTime = System.currentTimeMillis();
    	try {
	    	// LOG VARIOUS INTERESTING VALUES
    		logLogicStart();
	    	logMind();
	    	
	    	// UPDATE TEAM COMM
	    	commItems.update();
	    	commObjectUpdates.update();
	    	commConcentratedFire.update();
	    	
	    	// MAIN BOT LOGIC
	    	botLogic();
	    	
    	} catch (Exception e) {
    		// MAKE SURE THAT YOUR BOT WON'T FAIL!
    		log.info(ExceptionToString.process(e));
    		// At this point, it is a good idea to reset all state variables you have...
    		reset();
    	} finally {
    		// MAKE SURE THAT YOUR LOGIC DOES NOT TAKE MORE THAN 250 MS (Honestly, we have never seen anybody reaching even 150 ms per logic cycle...)
    		// Note that it is perfectly OK, for instance, to count all path-distances between you and all possible pickup-points / items in the game
    		// sort it and do some inference based on that.
    		long timeSpentInLogic = System.currentTimeMillis() - logicStartTime;
    		log.info("Logic time:         " + timeSpentInLogic + " ms");
    		if (timeSpentInLogic >= 245) {
    			log.warning("!!! LOGIC TOO DEMANDING !!!");
    		}
    		log.info("===[ LOGIC END ]===");
    		lastLogicEndMillis = System.currentTimeMillis();
    	}    	
    }
    
    public void botLogic() {
    	/*
    	// RANDOM NAVIGATION
    	if (navigation.isNavigating()) {
    		if (DRAW_NAVIGATION_PATH) {
    			if (!navigationPathDrawn) {
    				drawNavigationPath(true);
    				navigationPathDrawn = true;
    			}
    		}
    		return;
    	}
    	navigation.navigate(navPoints.getRandomNavPoint());
    	navigationPathDrawn = false;
    	log.info("RUNNING TO: " + navigation.getCurrentTarget());
    	*/
    	dodgeBehavior();
    	if (combatBehavior()) return;
    	if (disengageBehavior()) return;
    	collectItemBehavior();
    	
    }
    
    private void setDebugInfo(String info) {
    	bot.getBotName().setInfo(info);
    	log.info(info);
    }
    
    private void sayGlobal(String msg) {
    	// Simple way to send msg into the UT2004 chat
    	body.getCommunication().sendGlobalTextMessage(msg);
    	// And user log as well
    	log.info(msg);
    }
    // ==============
    // bot logic: dodge part
    // ==============
    private void dodgeBehavior() {
    	if (projectileIncoming) {
    		setDebugInfo("dodging");
    		move.dodgeTo(new Location(info.getLocation().x + dodgeDirection.getX(), info.getLocation().y + dodgeDirection.getY()), false);
    		projectileIncoming = false;
    		dodgeDirection = null;
    	}
    };
    
    // ==============
    // bot logic: combat part
    // ==============

    
    Location enemyLastLocation;
    Player enemyLastSeen;
    private boolean seeEnemy() {
    	Player enemy = players.getNearestVisibleEnemy();
    	
    	if (enemy != null) {
    		enemyLastLocation = enemy.getLocation();
    		enemyLastSeen = enemy;
    		return true;
    	}
    	else {
    		return false;
    	}
    }
    
    private boolean wantToFight() {
    	return info.getArmor() + info.getHealth() > 80 
    			&& weaponry.hasLoadedMeleeWeapon()
    			&& weaponry.hasLoadedRangedWeapon();
    }
    
    Cooldown damageCD = new Cooldown(3000);
    private void damageCooldown() {
    	if (!seeEnemy() && damageCD.tryUse()) {
    		enemyAccumulatedDamage = 0;
    	}
    }
    
    int enemyAccumulatedDamage = 0;
    private boolean wantToHunt() {
    	damageCooldown();
    	return info.getArmor() + info.getHealth() > 150 - enemyAccumulatedDamage
    			&& weaponry.hasLoadedMeleeWeapon() 
    			&& weaponry.hasLoadedRangedWeapon();
    }
    
    boolean strafeDirection = false; // false == left, true == right
    private boolean combatBehavior() {
    	if (!(seeEnemy() && (wantToFight() || wantToHunt()))) {
    		shoot.stopShooting();
    		return false;
    	}
    	setDebugInfo("combating");
    	navigation.setFocus(enemyLastLocation);    	
    	
    	if (navigation.isNavigating()) {
    		navigation.stopNavigation();
    	}
    	
    	if (strafeDirection == false) {
			move.strafeLeft(200);
			strafeDirection = !strafeDirection;
		}
		else {
			move.strafeRight(200);
			strafeDirection = !strafeDirection;
		}	
    	
    	double enemyDistance = info.getLocation().getDistance(enemyLastLocation);
    	if (enemyDistance > 1150 && enemyDistance <= 2500 && weaponry.hasLoadedWeapon(UT2004ItemType.ROCKET_LAUNCHER)) {
    		weaponry.changeWeapon(UT2004ItemType.ROCKET_LAUNCHER);
    		
    		tcClient.sendToTeamOthers(new TCConcentrateFire(enemyLastSeen.getId()));
        	shoot.shoot(enemyLastLocation.getLocation().sub(new Location(0, 0, 50)));
    	}
    	else {
    		tcClient.sendToTeamOthers(new TCConcentrateFire(enemyLastSeen.getId()));
    		shoot.shoot(weaponPrefs, enemyLastSeen);
    	}
    	
    	
		return true;
	};
	
	// ==============
    // bot logic: disengage part
    // ==============
	
	private boolean disengageBehavior() {
    	
    	if (!(!wantToFight() && !wantToHunt())) {
    		return false;
    	}
    	else{
    		setDebugInfo("disengaging");
    		navigation.setFocus(enemyLastSeen);
    		if (seeEnemy()) {
    			tcClient.sendToTeamOthers(new TCConcentrateFire(enemyLastSeen.getId()));
    			shoot.shoot(enemyLastSeen);
    		}
    		    		
    		if (navigation.isNavigating()) {
    			return true;
    		}
    		else {
    			Item item = getNthTargetItem();
    			if (item != null) {
        			if (navigateAStarPath(item.getNavPoint())){
        				return true;
        			}
        		}
    		}
    	}
    	return false;  	
    }
	
	// ==============
    // bot logic: item collection part
    // ==============
	

	class ItemWIthImportance {
	    public Item item;
	    public double importance;
	          
	    public ItemWIthImportance(Item item, double importance) {
	      
	        this.item = item;
	        this.importance = importance;
	    }
	      
	    public Item getItem() {
	        return item;
	    }
	    public double getImportance() {
	        return importance;
	    } 
	}
	
	class ItemComparator implements Comparator<ItemWIthImportance>{

		@Override
		public int compare(ItemWIthImportance o1, ItemWIthImportance o2) {
			if (o1.importance < o2.importance) return 1;
            else if (o1.importance > o2.importance) return -1;
            return 0;
		}
    }
	
	private Collection<Item> getImportantItems() {
		PriorityQueue<ItemWIthImportance> candidates = new PriorityQueue<ItemWIthImportance>(new ItemComparator());
		for(Item item : items.getSpawnedItems().values()) {
			if (items.isPickable(item)) {
				if (item.getType() == UT2004ItemType.U_DAMAGE_PACK) candidates.add(new ItemWIthImportance(item, 10));
				else if (item.getType() == UT2004ItemType.SHOCK_RIFLE) candidates.add(new ItemWIthImportance(item, 9));
				else if (item.getType() == UT2004ItemType.LIGHTNING_GUN) candidates.add(new ItemWIthImportance(item, 9));
				else if (item.getType() == UT2004ItemType.SUPER_SHIELD_PACK) candidates.add(new ItemWIthImportance(item, 9));
				else if (item.getType() == UT2004ItemType.SUPER_HEALTH_PACK) candidates.add(new ItemWIthImportance(item, 9));
				else if (item.getType() == UT2004ItemType.ROCKET_LAUNCHER) candidates.add(new ItemWIthImportance(item, 8));
				else if (item.getType() == UT2004ItemType.HEALTH_PACK) candidates.add(new ItemWIthImportance(item, 8));
				else if (item.getType() == UT2004ItemType.SHIELD_PACK) candidates.add(new ItemWIthImportance(item, 8));
				else if (item.getType() == UT2004ItemType.LINK_GUN) candidates.add(new ItemWIthImportance(item, 7));
				else if (item.getType() == UT2004ItemType.FLAK_CANNON) candidates.add(new ItemWIthImportance(item, 6));
				else if (item.getType() == UT2004ItemType.MINIGUN) candidates.add(new ItemWIthImportance(item, 6));
				//else candidates.add(new ItemWIthImportance(item, 5));
			}
		}
		
		List<Item> topItems = new ArrayList<Item>();
		while (!candidates.isEmpty()) {
			ItemWIthImportance current = candidates.poll();
			if (current != null) {
				topItems.add(current.item);
			}
			if (topItems.size() == 30) {
				break;
			}
		}
		
		return topItems;
	}
	
	public int nth = 0;
	public Item getNthTargetItem() {
		return	DistanceUtils.getNthNearest(
					nth,
					getImportantItems(), 
					info.getLocation(), 
					new DistanceUtils.IGetDistance<Item>() {
	
						@Override
						public double getDistance(Item object, ILocated target) {
							return navMeshModule.getAStarPathPlanner().getDistance(target, object);
						}
					}
			);
	}
	
	
	
	public Item currentTargetItem = null;
	double previousSlope = 0;
	double currentSlope = 0;
	Location previousLocation = null;
	Location currentLocation = null;
	Cooldown dodgeMoveCD = new Cooldown(2000);
    private void collectItemBehavior() {
    	
    	if (seeEnemy()) {
    		navigation.setFocus(enemyLastLocation);
    		shoot.shoot(enemyLastSeen);
    		tcClient.sendToTeamOthers(new TCConcentrateFire(enemyLastSeen.getId()));
    	}
    	
    	
    	Item targetItem = getNthTargetItem();
    	
    	
    	if (currentTargetItem == null && targetItem != null) {		
			currentTargetItem = targetItem;
    	}
    	
    	
    	
    	if (navigation.isNavigating()) {
    		//setDebugInfo("collecting");
    		setDebugInfo("Going for " + currentTargetItem.getType().getName());
    		navigation.setFocus(null);
    		
    		/*
    		currentLocation = info.getLocation();
    		if (currentLocation.getDistance(currentTargetItem.getLocation()) > 500 &&
    			dodgeMoveCD.tryUse() && previousLocation != null &&
    			(previousLocation.x != currentLocation.x || previousLocation.y != currentLocation.y) &&
    			raycastNavMesh(info.getLocation(), new Vector2D(currentLocation.x - previousLocation.x, currentLocation.y - previousLocation.y)) > 300)    			
    		{
    			
    			currentSlope = (currentLocation.y - previousLocation.y) / (currentLocation.x - previousLocation.x);
    			double relativeAngleRadian = Math.abs(Math.atan(previousSlope) - Math.atan(previousSlope));
    			if (previousSlope != 0 && relativeAngleRadian < Math.PI / 18 ) {
    				move.dodgeRelative(new Location(1, 0, 0), true);
    			}
    		}
    		previousLocation = currentLocation;
    		previousSlope = currentSlope;
    		*/
    		return;
    	}
    	else if (currentTargetItem != null){   		
    		navigation.navigate(currentTargetItem);
    		double currentFitness = navMeshModule.getAStarPathPlanner().getDistance(bot, currentTargetItem);
    		tcClient.sendToTeamOthers(new TCItemPursuing(info.getId(), currentTargetItem.getId(), currentFitness));
    	}
	}
    
    public void reset() {
    	navigationPathDrawn = false;
    }
    
    // ===========
    // MIND LOGGER
    // ===========
    
    /**
     * It is good to log that the logic has started so you can then retrospectively check the batches.
     */
    public void logLogicStart() {
    	long logicStartTime = System.currentTimeMillis();
    	timeDelta = logicStartTime - lastLogicStartMillis;
    	log.info("===[ LOGIC ITERATION | Delta: " + (timeDelta) + "ms | Since last: " + (logicStartTime - lastLogicEndMillis) + "ms]===");    		
    	lastLogicStartMillis = logicStartTime;    	
    }
    
    /**
     * It is good in-general to periodically log anything that relates to your's {@link TDMBot#logic()} decision making.
     * 
     * You might consider exporting these values to some custom Swing window (you crete for yourself) that will be more readable.
     */
    public void logMind() {
    	log.info("My health/armor:   " + info.getHealth() + " / " + info.getArmor() + " (low:" + info.getLowArmor() + " / high:" + info.getHighArmor() + ")");
    	log.info("My weapon:         " + weaponry.getCurrentWeapon());
    }
    
    // =====================================
    // UT2004 DEATH-MATCH INTERESTING GETTERS
    // ======================================
    
    /**
     * Returns path-nearest {@link NavPoint} that is covered from 'enemy'. Uses {@link UT2004BotModuleController#getVisibility()}.
     * @param enemy
     * @return
     */
    public NavPoint getNearestCoverPoint(Player enemy) {
    	if (!visibility.isInitialized()) {
    		log.warning("VISIBILITY NOT INITIALIZED: returning random navpoint");    		
    		return MyCollections.getRandom(navPoints.getNavPoints().values());
    	}
    	List<NavPoint> coverPoints = new ArrayList<NavPoint>(visibility.getCoverNavPointsFrom(enemy.getLocation()));
    	return fwMap.getNearestNavPoint(coverPoints, info.getNearestNavPoint());
    }
    
    /**
     * Returns whether 'item' is possibly spawned (to your current knowledge).
     * @param item
     * @return
     */
    public boolean isPossiblySpawned(Item item) {
    	return items.isPickupSpawned(item);
    }
    
    /**
     * Returns whether you can actually pick this 'item', based on "isSpawned" and "isPickable" in your current state and knowledge.
     */
    public boolean isCurrentlyPickable(Item item) {
    	return isPossiblySpawned(item) && items.isPickable(item);
    }
        
    // ==========
    // RAYCASTING
    // ==========
    
    /**
     * Performs a client-side raycast against UT2004 map geometry.
     * 
     * It is not sensible to perform more than 1000 raycasts per logic() per bot.
     *  
     * NOTE THAT IN ORDER TO USE THIS, you have to rename "map_" folder into "map" ... so it would load the level geometry.
     * Note that loading a level geometry up takes quite a lot of time (>60MB large BSP tree...). 
     *  
     * @param from
     * @param to
     * @return
     */
    public RayCastResult raycast(ILocated from, ILocated to) {
    	if (!levelGeometryModule.isInitialized()) {
    		throw new RuntimeException("Level Geometry not initialized! Cannot RAYCAST!");
    	}
    	return levelGeometryModule.getLevelGeometry().rayCast(from.getLocation(), to.getLocation());
    }
    
    /**
     * Performs a client-side raycast against NavMesh in 'direction'. Returns distance of the edge in given 'direction' sending the ray 'from'.
     * @param from
     * @param direction
     * @return
     */
    public double raycastNavMesh(ILocated from, Vector2D direction) {
    	if (!navMeshModule.isInitialized()) {
    		log.severe("NavMesh not initialized! Cannot RAYCAST-NAVMESH!");
    		return 0;
    	}
    	ClearanceLimit limit = navMeshModule.getClearanceComputer().findEdge(from.getLocation(), direction);
    	if (limit == null) return Double.POSITIVE_INFINITY;
    	return from.getLocation().getDistance(limit.getLocation());
    }
    
    // =======
    // DRAWING
    // =======
    
    public void drawNavigationPath(boolean clearAll) {
    	if (clearAll) {
    		draw.clearAll();
    	}
    	List<ILocated> path = navigation.getCurrentPathCopy();
    	for (int i = 1; i < path.size(); ++i) {
    		draw.drawLine(path.get(i-1), path.get(i));
    	}
    }
    
    public void drawPath(IPathFuture<? extends ILocated> pathFuture, boolean clearAll) {
    	if (clearAll) {
    		draw.clearAll();
    	}
    	List<? extends ILocated> path = pathFuture.get();
    	for (int i = 1; i < path.size(); ++i) {
    		draw.drawLine(path.get(i-1), path.get(i));
    	}
    }
    
    public void drawPath(IPathFuture<? extends ILocated> pathFuture, Color color, boolean clearAll) {
    	if (clearAll) {
    		draw.clearAll();
    	}
    	if (color == null) color = Color.WHITE;
    	List<? extends ILocated> path = pathFuture.get();
    	for (int i = 1; i < path.size(); ++i) {
    		draw.drawLine(color, path.get(i-1), path.get(i));
    	}
    }
    
    // =====
    // AStar
    // =====
    
    private NavPoint lastAStarTarget = null;
    
    public boolean navigateAStarPath(NavPoint targetNavPoint) {
        if (lastAStarTarget == targetNavPoint) {
            if (navigation.isNavigating()) return true;
        }
        PrecomputedPathFuture<ILocated> path = getAStarPath(targetNavPoint);
        if (path == null) {
            navigation.stopNavigation();
            return false;
        }
        lastAStarTarget = targetNavPoint;
        navigation.navigate(path, true);
        return true;
    }
    
    private IPFMapView<NavPoint> mapView = new IPFMapView<NavPoint>() {

        @Override
        public Collection<NavPoint> getExtraNeighbors(NavPoint node, Collection<NavPoint> mapNeighbors) {
            return null;
        }

        @Override
        public int getNodeExtraCost(NavPoint node, int mapCost) {
            return 0;
        }

        @Override
        public int getArcExtraCost(NavPoint nodeFrom, NavPoint nodeTo, int mapCost) {
            return 0;
        }

        @Override
        public boolean isNodeOpened(NavPoint node) {
            return true;
        }

        @Override
        public boolean isArcOpened(NavPoint nodeFrom, NavPoint nodeTo) {
            return true;
        }
    };
    
    private PrecomputedPathFuture<ILocated> getAStarPath(NavPoint targetNavPoint) {
        NavPoint startNavPoint = info.getNearestNavPoint();
        AStarResult<NavPoint> result = aStar.findPath(startNavPoint, targetNavPoint, mapView);
        if (result == null || !result.isSuccess()) return null;
        PrecomputedPathFuture path = new PrecomputedPathFuture(startNavPoint, targetNavPoint, result.getPath());
        return path;
    }
    
    // ===========
    // MAIN METHOD
    // ===========
    
    /**
     * Main execute method of the program.
     * 
     * @param args
     * @throws PogamutException
     */
    public static void main(String args[]) throws PogamutException {
    	// Starts N agents of the same type at once
    	// WHEN YOU WILL BE SUBMITTING YOUR CODE, MAKE SURE THAT YOU RESET NUMBER OF STARTED AGENTS TO '1' !!!
    	// => during the development, please use {@link Starter_Bots} instead to ensure you will leave "1" in here
    	new UT2004BotRunner(TDMBot.class, "TDMBot").setMain(true).setLogLevel(Level.INFO).startAgents(1);
    }
    
}
