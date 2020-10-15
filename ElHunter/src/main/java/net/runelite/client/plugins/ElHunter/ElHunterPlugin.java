package net.runelite.client.plugins.ElHunter;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.queries.TileQuery;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginType;
import net.runelite.client.ui.overlay.OverlayManager;
import org.pf4j.Extension;
import net.runelite.client.plugins.botutils.BotUtils;
import java.awt.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import static net.runelite.client.plugins.ElHunter.ElHunterState.*;

@Extension
@PluginDependency(BotUtils.class)
@PluginDescriptor(
		name = "El Hunter",
		description = "Trains Hunter",
		type = PluginType.SKILLING
)
@Slf4j
public class ElHunterPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private BotUtils utils;

	@Inject
	private ConfigManager configManager;

	@Inject
	OverlayManager overlayManager;

	@Inject
	ItemManager itemManager;

	@Inject
	private ElHunterConfig config;

	@Inject
	private ElHunterOverlay overlay;



	//plugin data
	GameObject targetObject;
	MenuEntry targetMenu;
	NPC targetNPC;
	int clientTickBreak = 0;
	int tickTimer;
	boolean startHunter;
	ElHunterState status;
	List<Integer> REQUIRED_ITEMS = new ArrayList<>();

	//overlay data
	Instant botTimer;
	int clientTickCounter;
	boolean clientClick;


	// Provides our config
	@Provides
	ElHunterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ElHunterConfig.class);
	}

	@Override
	protected void startUp()
	{
		botTimer = Instant.now();
		setValues();
		startHunter=false;
		log.info("Plugin started");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		setValues();
		startHunter=false;
		log.info("Plugin stopped");
	}

	@Subscribe
	private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked)
	{
		if (!configButtonClicked.getGroup().equalsIgnoreCase("ElHunter"))
		{
			return;
		}
		log.info("button {} pressed!", configButtonClicked.getKey());
		if (configButtonClicked.getKey().equals("startButton"))
		{
			if (!startHunter)
			{
				startHunter = true;
				targetMenu = null;
				botTimer = Instant.now();
				overlayManager.add(overlay);
			} else {
				shutDown();
			}
		}
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("ElHunter"))
		{
			return;
		}
		startHunter = false;
	}

	private void setValues()
	{
		switch(config.type()){
			case SWAMP_LIZARDS:
			case RED_SALAMANDER:
				REQUIRED_ITEMS = List.of(954,303);
		}
		clientTickCounter=-1;
		clientTickBreak=0;
		clientClick=false;
	}

	@Subscribe
	private void onClientTick(ClientTick clientTick)
	{
		if(clientTickBreak>0){
			clientTickBreak--;
			return;
		}
		clientTickBreak=utils.getRandomIntBetweenRange(4,6);
	}

	@Subscribe
	private void onGameTick(GameTick gameTick)
	{
		if (!startHunter)
		{
			return;
		}
		if (!client.isResized())
		{
			utils.sendGameMessage("client must be set to resizable");
			startHunter = false;
			return;
		}
		clientTickCounter=0;
		status = checkPlayerStatus();
		switch (status) {
			case ANIMATING:
			case NULL_PLAYER:
			case TICK_TIMER:
				break;
			case MOVING:
				shouldRun();
				break;
		}
	}

	@Subscribe
	private void onMenuOptionClicked(MenuOptionClicked event)
	{
		log.info(event.toString());
		if(targetMenu!=null){
			event.consume();
			client.invokeMenuAction(targetMenu.getOption(), targetMenu.getTarget(), targetMenu.getIdentifier(), targetMenu.getOpcode(),
					targetMenu.getParam0(), targetMenu.getParam1());
			targetMenu = null;
		}

	}

	private long sleepDelay()
	{
		if(config.customDelays()){
			return utils.randomDelay(config.sleepWeighted(), config.sleepMin(), config.sleepMax(), config.sleepDeviation(), config.sleepTarget());
		} else {
			return utils.randomDelay(false, 60, 350, 100, 100);
		}

	}

	private int tickDelay()
	{
		if(config.customDelays()){
			return (int) utils.randomDelay(config.tickWeighted(),config.tickMin(), config.tickMax(), config.tickDeviation(), config.tickTarget());
		} else {
			return (int) utils.randomDelay(false,1, 3, 2, 2);
		}

	}

	private ElHunterState checkPlayerStatus()
	{
		Player player = client.getLocalPlayer();
		if(player==null){
			return NULL_PLAYER;
		}
		if(utils.iterating){
			return ITERATING;
		}
		if(player.getPoseAnimation()!=813 && player.getPoseAnimation()!=5160 && player.getPoseAnimation()!=808){
			return MOVING;
		}
		if(player.getAnimation()!=-1){
			return ANIMATING;
		}
		if(tickTimer>0)
		{
			tickTimer--;
			return TICK_TIMER;
		}
		for(int ID : REQUIRED_ITEMS){
			if(!utils.inventoryContains(ID)){
				log.info("inventory doesnt contain:" + ID);
				return MISSING_REQUIRED;
			}
		}
		tickTimer=tickDelay();
		switch (config.type()) {
			case SWAMP_LIZARDS:
				return getSwampLizardState();
			case RED_SALAMANDER:
				return getRedSalamanderState();
			case FALCONRY:
				return getFalconryState();
		}
		return UNKNOWN;
	}

	private Point getRandomNullPoint()
	{
		if(client.getWidget(161,34)!=null){
			Rectangle nullArea = client.getWidget(161,34).getBounds();
			return new Point ((int)nullArea.getX()+utils.getRandomIntBetweenRange(0,nullArea.width), (int)nullArea.getY()+utils.getRandomIntBetweenRange(0,nullArea.height));
		}

		return new Point(client.getCanvasWidth()-utils.getRandomIntBetweenRange(0,2),client.getCanvasHeight()-utils.getRandomIntBetweenRange(0,2));
	}

	private int checkRunEnergy()
	{
		try{
			return Integer.parseInt(client.getWidget(160,23).getText());
		} catch (Exception ignored) {

		}
		return 0;
	}

	private int checkHitpoints()
	{
		try{
			return client.getBoostedSkillLevel(Skill.HITPOINTS);
		} catch (Exception e) {
			return 0;
		}
	}

	private void shouldRun()
	{
		if(client.getWidget(160,23)!=null){ //if run widget is visible
			if(Integer.parseInt(client.getWidget(160,23).getText())>(30+utils.getRandomIntBetweenRange(0,20))){ //if run > 30+~20
				if(client.getWidget(160,27).getSpriteId()==1069){ //if run is off
					targetMenu = new MenuEntry("Toggle Run","",1,57,-1,10485782,false);
					utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
					return;
				}
			}
		}
	}

	private ElHunterState getSwampLizardState()
	{
		if(releaseLizards(10149)){
			return RELEASING;
		}
		if(checkForGroundItems()){
			return PICKING_UP;
		}
		targetObject = utils.findNearestGameObject(9341);
		if(targetObject!=null){
			if(targetObject.getWorldLocation().distanceTo2D(client.getLocalPlayer().getWorldLocation())<8) {
				targetMenu = new MenuEntry("Set-trap", "<col=ffff>Young tree", targetObject.getId(), 3, targetObject.getSceneMinLocation().getX(), targetObject.getSceneMinLocation().getY(), false);
				utils.delayMouseClick(getRandomNullPoint(), sleepDelay());
				return SETTING_TRAP;
			}
		}
		targetObject = utils.findNearestGameObject(9004);
		if(targetObject!=null){
			if(targetObject.getWorldLocation().distanceTo2D(client.getLocalPlayer().getWorldLocation())<8) {
				targetMenu = new MenuEntry("Check", "<col=ffff>Net trap", targetObject.getId(), 3, targetObject.getSceneMinLocation().getX(), targetObject.getSceneMinLocation().getY(), false);
				utils.delayMouseClick(getRandomNullPoint(), sleepDelay());
				return CHECKING_TRAP;
			}
		}
		return UNKNOWN;
	}

	private ElHunterState getRedSalamanderState()
	{
		if(releaseLizards(10147)){
			return RELEASING;
		}
		if(checkForGroundItems()){
			return PICKING_UP;
		}
		targetObject = utils.findNearestGameObject(8990);
		if(targetObject!=null){
			if(targetObject.getWorldLocation().distanceTo2D(client.getLocalPlayer().getWorldLocation())<6) {
				targetMenu = new MenuEntry("Set-trap", "<col=ffff>Young tree", targetObject.getId(), 3, targetObject.getSceneMinLocation().getX(), targetObject.getSceneMinLocation().getY(), false);
				utils.delayMouseClick(getRandomNullPoint(), sleepDelay());
				return SETTING_TRAP;
			}
		}
		targetObject = utils.findNearestGameObject(8986);
		if(targetObject!=null){
			if(targetObject.getWorldLocation().distanceTo2D(client.getLocalPlayer().getWorldLocation())<6) {
				targetMenu = new MenuEntry("Check", "<col=ffff>Net trap", targetObject.getId(), 3, targetObject.getSceneMinLocation().getX(), targetObject.getSceneMinLocation().getY(), false);
				utils.delayMouseClick(getRandomNullPoint(), sleepDelay());
				return CHECKING_TRAP;
			}
		}
		return UNKNOWN;
	}

	private boolean checkForGroundItems()
	{
		for(Tile tile : new TileQuery().isWithinDistance(client.getLocalPlayer().getWorldLocation(),10).result(client)) {
			if(tile.getGroundItems()!=null){
				for(TileItem tileItem : tile.getGroundItems()){
					if(REQUIRED_ITEMS.contains(tileItem.getId())){
						targetMenu = new MenuEntry ("Take", "<col=ff9040>",tileItem.getId(),20,tileItem.getTile().getX(),tileItem.getTile().getY(),false);
						utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
						return true;
					}
				}
			}
		}
		return false;
	}

	private boolean releaseLizards(int id)
	{
		if (utils.inventoryContains(id)){
			targetMenu = new MenuEntry("Release", "<col=ff9040>", utils.getInventoryWidgetItem(id).getId(), 37, utils.getInventoryWidgetItem(id).getIndex(), 9764864, false);
			utils.delayMouseClick(getRandomNullPoint(), sleepDelay());
			return true;
		}
		return false;
	}

	private ElHunterState getFalconryState()
	{
		if(dropFalconry()){
			return DROPPING;
		}
		if(falconryProjectile()){
			return CATCHING;
		}
		if(client.getBoostedSkillLevel(Skill.HUNTER)>68){
			targetNPC = utils.findNearestNpc(1343);
			if(targetNPC!=null){
				targetMenu = new MenuEntry("Retrieve", "<col=ffff00>Gyr Falcon", targetNPC.getIndex(), 9, 0, 0, false);
				utils.delayMouseClick(getRandomNullPoint(), sleepDelay());
				return CATCHING;
			}
			targetNPC = utils.findNearestNpc(5533);
			if(targetNPC!=null){
				targetMenu = new MenuEntry("Catch", "<col=ffff00>Dashing kebbit", targetNPC.getIndex(), 9, 0, 0, false);
				utils.delayMouseClick(getRandomNullPoint(), sleepDelay());
				return RETRIEVING;
			}
		}

		if(client.getBoostedSkillLevel(Skill.HUNTER)>56){
			targetNPC = utils.findNearestNpc(1344);
			if(targetNPC!=null){
				targetMenu = new MenuEntry("Retrieve", "<col=ffff00>Gyr Falcon", targetNPC.getIndex(), 9, 0, 0, false);
				utils.delayMouseClick(getRandomNullPoint(), sleepDelay());
				return RETRIEVING;
			}
			targetNPC = utils.findNearestNpc(5532);
			if(targetNPC!=null){
				targetMenu = new MenuEntry("Catch", "<col=ffff00>Dark kebbit", targetNPC.getIndex(), 9, 0, 0, false);
				utils.delayMouseClick(getRandomNullPoint(), sleepDelay());
				return CATCHING;
			}
		}

		if(client.getBoostedSkillLevel(Skill.HUNTER)>42){
			targetNPC = utils.findNearestNpc(1342);
			if(targetNPC!=null){
				targetMenu = new MenuEntry("Retrieve", "<col=ffff00>Gyr Falcon", targetNPC.getIndex(), 9, 0, 0, false);
				utils.delayMouseClick(getRandomNullPoint(), sleepDelay());
				return RETRIEVING;
			}
			targetNPC = utils.findNearestNpc(5531);
			if(targetNPC!=null){
				targetMenu = new MenuEntry("Catch", "<col=ffff00>Dark kebbit", targetNPC.getIndex(), 9, 0, 0, false);
				utils.delayMouseClick(getRandomNullPoint(), sleepDelay());
				return CATCHING;
			}
		}
		return UNKNOWN;
	}

	private boolean dropFalconry()
	{
		if(utils.getInventorySpace()<2){
			utils.dropItems(new HashSet<>(Arrays.asList(10127, 10125, 10115, 526)),true,config.sleepMin(),config.sleepMax());
			return true;
		}
		return false;
	}

	private boolean falconryProjectile()
	{
		for(Projectile projectile : client.getProjectiles())
		{
			if(projectile.getId()==922){
				return true;
			}
		}
		return false;
	}
}
