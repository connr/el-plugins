package net.runelite.client.plugins.ElAstrals;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
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
import static net.runelite.client.plugins.ElAstrals.ElAstralsState.*;
import com.owain.chinbreakhandler.ChinBreakHandler;

@Extension
@PluginDependency(BotUtils.class)
@PluginDescriptor(
		name = "El Astrals",
		description = "Crafts astrals.",
		type = PluginType.SKILLING
)
@Slf4j
public class ElAstralsPlugin extends Plugin
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
	private ElAstralsConfig config;

	@Inject
	private ElAstralsOverlay overlay;

	@Inject
	private ChinBreakHandler chinBreakHandler;



	//plugin data
	GameObject targetObject;
	MenuEntry targetMenu;
	int clientTickBreak = 0;
	int tickTimer;
	boolean startAstrals;
	ElAstralsState status;
	int runecraftProgress = 0;
	//overlay data
	Instant botTimer;
	List<Integer> REQUIRED_ITEMS = new ArrayList<>();
	List<Integer> DEGRADED_POUCHES = new ArrayList<>();
	int ASTRAL_ID = 9075;
	int ESSENCE_ID;
	int startEss;
	int currentEss;
	int clientTickCounter;
	boolean clientClick;
	boolean firstClickOnAltar;

	WorldArea LUNAR_BANK = new WorldArea(new WorldPoint(2096,3916,0), new WorldPoint(2101,3920,0));
	WorldArea FIRST_CLICK_AREA = new WorldArea(new WorldPoint(2123,3867,0), new WorldPoint(2132,3879,0));
	WorldArea LUNAR_TELE_SPOT = new WorldArea(new WorldPoint(2105,3911,0), new WorldPoint(2114,3920,0));

	WorldPoint FIRST_CLICK_POINT = new WorldPoint(2126,3873,0);

	// Provides our config
	@Provides
	ElAstralsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ElAstralsConfig.class);
	}

	@Override
	protected void startUp()
	{
		chinBreakHandler.registerPlugin(this);
		botTimer = Instant.now();
		setValues();
		startAstrals=false;
		log.info("Plugin started");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		setValues();
		startAstrals=false;
		log.info("Plugin stopped");
		chinBreakHandler.unregisterPlugin(this);
	}

	@Subscribe
	private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked)
	{
		if (!configButtonClicked.getGroup().equalsIgnoreCase("ElAstrals"))
		{
			return;
		}
		log.info("button {} pressed!", configButtonClicked.getKey());
		if (configButtonClicked.getKey().equals("startButton"))
		{
			if (!startAstrals)
			{
				startAstrals = true;
				targetMenu = null;
				botTimer = Instant.now();
				overlayManager.add(overlay);
				chinBreakHandler.startPlugin(this);
			} else {
				shutDown();
			}
		}
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("ElAstrals"))
		{
			return;
		}
		startAstrals = false;
	}

	private void setValues()
	{
		runecraftProgress = 0;
		if(config.giantPouch()){
			REQUIRED_ITEMS = List.of(5509,5510,5512,5514,12791);
		} else {
			REQUIRED_ITEMS = List.of(5509,5510,5512,12791);
		}
		DEGRADED_POUCHES = List.of(5511,5513,5515);
		startEss=0;
		currentEss=0;
		clientTickCounter=-1;
		clientTickBreak=0;
		clientClick=false;
		if(config.daeyalt()){
			ESSENCE_ID = 24704;
		} else {
			ESSENCE_ID = 7936;
		}
		firstClickOnAltar=false;
		chinBreakHandler.stopPlugin(this);
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
		if (!startAstrals)
		{
			return;
		}
		if (!client.isResized())
		{
			utils.sendGameMessage("client must be set to resizable");
			startAstrals = false;
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
				if(config.noStams()){
					shouldRun();
				}
				break;
			case OPENING_BANK:
				if(client.getLocalPlayer().getWorldArea().intersectsWith(LUNAR_TELE_SPOT) || client.getLocalPlayer().getWorldArea().intersectsWith(LUNAR_BANK) ){
					if(runecraftProgress==17){
						runecraftProgress++;
					}
					openLunarBank();
				} else {
					teleToMoonclan();
				}
				break;
			case MISSING_REQUIRED:
				withdrawRequiredItems();
				break;
			case CLICKING_ALTAR:
				if(client.getLocalPlayer().getWorldArea().intersectsWith(LUNAR_BANK)){
					utils.walk(FIRST_CLICK_POINT,2,sleepDelay());
				} else if(client.getLocalPlayer().getWorldArea().intersectsWith(FIRST_CLICK_AREA)){
					clickAstralAltar();
					runecraftProgress++;
				}
				tickTimer=tickDelay();
				break;
			case HANDLE_BREAK:
				chinBreakHandler.startBreak(this);
				tickTimer = 10;
				break;
			case EMPTYING_POUCHES:
				status = emptyPouches();
				break;
			case TELEPORT_MOONCLAN:
				teleToMoonclan();
				runecraftProgress++;
				tickTimer=tickDelay();
				break;
			case CLICKING_LADDER:
				//climbDownLadder();
				runecraftProgress++;
				//tickTimer=tickDelay();
				break;
			case DEPOSIT_INVENT:
				utils.depositAllExcept(REQUIRED_ITEMS);
				runecraftProgress=0;
				break;
			case POUCH_DEGRADED:
				fixDegradedPouch();
				tickTimer=tickDelay();
				break;
		}
	}

	@Subscribe
	private void onMenuOptionClicked(MenuOptionClicked event)
	{
		log.debug(event.toString());
		if(targetMenu!=null){
			event.consume();
			client.invokeMenuAction(targetMenu.getOption(), targetMenu.getTarget(), targetMenu.getIdentifier(), targetMenu.getOpcode(),
					targetMenu.getParam0(), targetMenu.getParam1());
			targetMenu = null;
		}

	}

	private long sleepDelay()
	{
		return utils.randomDelay(false, config.sleepMin(), config.sleepMax(), config.sleepDeviation(), config.sleepTarget());
	}

	private int tickDelay()
	{
		return (int) utils.randomDelay(false,config.tickMin(), config.tickMax(), config.tickDeviation(), config.tickTarget());
	}

	private ElAstralsState checkPlayerStatus()
	{
		Player player = client.getLocalPlayer();
		if(player==null){
			return NULL_PLAYER;
		}
		if(player.getPoseAnimation()!=813){
			if(player.getWorldArea().intersectsWith(FIRST_CLICK_AREA)){
				if(!firstClickOnAltar){
					firstClickOnAltar=true;
					clickAstralAltar();
					runecraftProgress++;
				}
			}
			return MOVING;
		}

		if(player.getAnimation()!=-1){
			if(player.getAnimation()!=420 || player.getAnimation()!=829){
				return ANIMATING;
			}

		}
		if(checkHitpoints()<config.pauseHealth()){
			return PLAYER_HP_LOW;
		}
		if(tickTimer>0)
		{
			tickTimer--;
			return TICK_TIMER;
		}
		if(!utils.inventoryContains(12791)){
			return MISSING_RUNE_POUCH;
		}
		if(utils.inventoryContains(DEGRADED_POUCHES)){
			return POUCH_DEGRADED;
		}
		if(!utils.inventoryContainsAllOf(REQUIRED_ITEMS)){
			if(!utils.isBankOpen()){
				return OPENING_BANK;
			} else {
				return MISSING_REQUIRED;
			}
		}
		if(utils.inventoryContainsAllOf(REQUIRED_ITEMS)){
			if(runecraftProgress<8){
				if (chinBreakHandler.shouldBreak(this))
				{
					return HANDLE_BREAK;
				}
				if(utils.inventoryContains(ASTRAL_ID)){
					if(!utils.isBankOpen()){
						return OPENING_BANK;
					} else {
						return DEPOSIT_INVENT;
					}
				}
				if(!utils.isBankOpen()){
					return OPENING_BANK;
				} else {
					return fillPouches();
				}
			} else if(runecraftProgress==8){
				return CLICKING_ALTAR;
			} else if(runecraftProgress<15){
				return EMPTYING_POUCHES;
			} else if(runecraftProgress==15){
				return TELEPORT_MOONCLAN;
			} else if(runecraftProgress==16){
				return CLICKING_LADDER;
			} else if(runecraftProgress==17){
				return OPENING_BANK;
			} else if(runecraftProgress==18){
				return DEPOSIT_INVENT;
			}
		}
		return UNKNOWN;
	}

	private void openLunarBank()
	{
		if(config.dreamMentor()){
			targetObject = utils.findNearestGameObject(16700);
			firstClickOnAltar=false;
			if(targetObject!=null){
				targetMenu = new MenuEntry("Bank", "<col=ffff>Bank booth", targetObject.getId(), 4, targetObject.getSceneMinLocation().getX(), targetObject.getSceneMinLocation().getY(), false);
				utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
			}
		} else {
			for(GameObject gameObject : utils.getGameObjects(16700)){
				if(gameObject.getWorldLocation().equals(new WorldPoint(2098,3920,0))){
					targetObject=gameObject;
					targetMenu = new MenuEntry("Bank", "<col=ffff>Bank booth", targetObject.getId(), 4, targetObject.getSceneMinLocation().getX(), targetObject.getSceneMinLocation().getY(), false);
					utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
				}
			}
		}
	}

	private Point getRandomNullPoint()
	{
		if(client.getWidget(161,34)!=null){
			Rectangle nullArea = client.getWidget(161,34).getBounds();
			return new Point ((int)nullArea.getX()+utils.getRandomIntBetweenRange(0,nullArea.width), (int)nullArea.getY()+utils.getRandomIntBetweenRange(0,nullArea.height));
		}

		return new Point(client.getCanvasWidth()-utils.getRandomIntBetweenRange(0,2),client.getCanvasHeight()-utils.getRandomIntBetweenRange(0,2));
	}

	private ElAstralsState fillPouches()
	{
		if(startEss==0){
			startEss = utils.getBankItemWidget(ESSENCE_ID).getItemQuantity();
		}
		currentEss = utils.getBankItemWidget(ESSENCE_ID).getItemQuantity();
		tickTimer=0;
		if(!config.noStams()){
			if(client.getVar(Varbits.RUN_SLOWED_DEPLETION_ACTIVE)==0 && checkRunEnergy()<config.minEnergy()){
				if(utils.inventoryContains(12631)){
					targetMenu = new MenuEntry("Drink","<col=ff9040>Stamina potion(1)</col>",9,1007,utils.getInventoryWidgetItem(12631).getIndex(),983043,false);
					utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
					return DRINKING_STAM;
				} else {
					if(utils.inventoryFull()) {
						return DEPOSIT_INVENT;
					} else {
						targetMenu = new MenuEntry("Withdraw-1", "<col=ff9040>Stamina potion(1)</col>", 1, 57, utils.getBankItemWidget(12631).getIndex(), 786444, false);
						utils.delayMouseClick(getRandomNullPoint(), sleepDelay());
						return WITHDRAW_STAM;
					}
				}
			} else if (checkRunEnergy()<25){
				if(utils.inventoryContains(12631)){
					targetMenu = new MenuEntry("Drink","<col=ff9040>Stamina potion(1)</col>",9,1007,utils.getInventoryWidgetItem(12631).getIndex(),983043,false);
					utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
					return DRINKING_STAM;
				} else {
					if(utils.inventoryFull()) {
						return DEPOSIT_INVENT;
					} else {
						targetMenu = new MenuEntry("Withdraw-1","<col=ff9040>Stamina potion(1)</col>",1,57,utils.getBankItemWidget(12631).getIndex(),786444,false);
						utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
						return WITHDRAW_STAM;
					}
				}
			} else if(utils.inventoryContains(12631)){
				return DEPOSIT_INVENT;
			}
		}
		if(checkHitpoints()<config.minHealth()){
			if(utils.inventoryContains(config.foodId())){
				targetMenu = new MenuEntry("Eat","<col=ff9040>"+itemManager.getItemDefinition(config.foodId()).getName()+"</col>",9,1007,utils.getInventoryWidgetItem(config.foodId()).getIndex(),983043,false);
				utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
				return EATING_FOOD;
			} else {
				if(utils.inventoryFull()){
					utils.depositAllExcept(REQUIRED_ITEMS);
					return DEPOSIT_INVENT;
				} else {
					targetMenu = new MenuEntry("Withdraw-1", "Withdraw-1", 1, 57, utils.getBankItemWidget(config.foodId()).getIndex(), 786444, false);
					utils.delayMouseClick(getRandomNullPoint(), sleepDelay());
					return WITHDRAW_FOOD;
				}
			}
		} else if(utils.inventoryContains(config.foodId())){
			return DEPOSIT_INVENT;
		}
		if(!config.giantPouch()){
			if(runecraftProgress==0){
				runecraftProgress=2;
			}
		}
		switch(runecraftProgress){
			case 0:
			case 2:
			case 6:
				targetMenu = new MenuEntry("Withdraw-All","<col=ff9040>"+itemManager.getItemDefinition(ESSENCE_ID).getName()+"</col>",7,1007,utils.getBankItemWidget(ESSENCE_ID).getIndex(),786444,false);
				utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
				runecraftProgress++;
				return WITHDRAW_ESS;
			case 1:
				targetMenu = new MenuEntry("Fill","<col=ff9040>Giant pouch</col>",9,1007,utils.getInventoryWidgetItem(5514).getIndex(),983043,false);
				utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
				runecraftProgress++;
				return FILL_GIANT;
			case 3:
				targetMenu = new MenuEntry("Fill","<col=ff9040>Large pouch</col>",9,1007,utils.getInventoryWidgetItem(5512).getIndex(),983043,false);
				utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
				runecraftProgress++;
				return FILL_LARGE;
			case 4:
				targetMenu = new MenuEntry("Fill","<col=ff9040>Medium pouch</col>",9,1007,utils.getInventoryWidgetItem(5510).getIndex(),983043,false);
				utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
				runecraftProgress++;
				return FILL_MEDIUM;
			case 5:
				targetMenu = new MenuEntry("Fill","<col=ff9040>Small pouch</col>",9,1007,utils.getInventoryWidgetItem(5509).getIndex(),983043,false);
				utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
				runecraftProgress++;
				return FILL_SMALL;
			case 7:
				targetMenu = new MenuEntry("Close","",1,57,11,786434,false);
				utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
				runecraftProgress++;
				tickTimer=tickDelay();
				return CLOSING_BANK;
		}
		return UNKNOWN;
	}

	private ElAstralsState emptyPouches()
	{
		if(config.giantPouch()){
			switch (runecraftProgress) {
				case 9:
					targetMenu = new MenuEntry("Empty", "<col=ff9040>Small pouch</col>", 5509, 34, utils.getInventoryWidgetItem(5509).getIndex(), 9764864, false);
					utils.delayMouseClick(getRandomNullPoint(), sleepDelay());
					runecraftProgress++;
					return EMPTY_SMALL;
				case 10:
					targetMenu = new MenuEntry("Empty", "<col=ff9040>Giant pouch</col>", 5514, 34, utils.getInventoryWidgetItem(5514).getIndex(), 9764864, false);
					utils.delayMouseClick(getRandomNullPoint(), sleepDelay());
					runecraftProgress++;
					return EMPTY_GIANT;
				case 11:
				case 14:
					clickAstralAltar();
					runecraftProgress++;
					return CLICKING_ALTAR;
				case 12:
					targetMenu = new MenuEntry("Empty", "<col=ff9040>Large pouch</col>", 5512, 34, utils.getInventoryWidgetItem(5512).getIndex(), 9764864, false);
					utils.delayMouseClick(getRandomNullPoint(), sleepDelay());
					runecraftProgress++;
					return EMPTY_LARGE;
				case 13:
					targetMenu = new MenuEntry("Empty", "<col=ff9040>Medium pouch</col>", 5510, 34, utils.getInventoryWidgetItem(5510).getIndex(), 9764864, false);
					utils.delayMouseClick(getRandomNullPoint(), sleepDelay());
					runecraftProgress++;
					return EMPTY_MEDIUM;
			}
			return UNKNOWN;
		} else {
			switch (runecraftProgress) {
				case 9:
					targetMenu = new MenuEntry("Empty", "<col=ff9040>Small pouch</col>", 5509, 34, utils.getInventoryWidgetItem(5509).getIndex(), 9764864, false);
					utils.delayMouseClick(getRandomNullPoint(), sleepDelay());
					runecraftProgress++;
					return EMPTY_SMALL;
				case 10:
					targetMenu = new MenuEntry("Empty", "<col=ff9040>Medium pouch</col>", 5510, 34, utils.getInventoryWidgetItem(5510).getIndex(), 9764864, false);
					utils.delayMouseClick(getRandomNullPoint(), sleepDelay());
					runecraftProgress++;
					return EMPTY_MEDIUM;
				case 11:
					targetMenu = new MenuEntry("Empty", "<col=ff9040>Large pouch</col>", 5512, 34, utils.getInventoryWidgetItem(5512).getIndex(), 9764864, false);
					utils.delayMouseClick(getRandomNullPoint(), sleepDelay());
					runecraftProgress=14;
					return EMPTY_LARGE;
				case 14:
					clickAstralAltar();
					runecraftProgress++;
					return CLICKING_ALTAR;
			}
			return UNKNOWN;
		}
	}

	private void withdrawRequiredItems()
	{
		try{
			if(!utils.inventoryContains(REQUIRED_ITEMS.get(0))){
				targetMenu = new MenuEntry("Withdraw-1","Withdraw-1",1,1007,utils.getBankItemWidget(REQUIRED_ITEMS.get(0)).getIndex(),786444,false);
				utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
				return;
			}
			else if(!utils.inventoryContains(REQUIRED_ITEMS.get(1))){
				targetMenu = new MenuEntry("Withdraw-1","Withdraw-1",1,1007,utils.getBankItemWidget(REQUIRED_ITEMS.get(1)).getIndex(),786444,false);
				utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
				return;
			}
			else if(!utils.inventoryContains(REQUIRED_ITEMS.get(2))){
				targetMenu = new MenuEntry("Withdraw-1","Withdraw-1",1,1007,utils.getBankItemWidget(REQUIRED_ITEMS.get(2)).getIndex(),786444,false);
				utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
				return;
			}
			else if(!utils.inventoryContains(REQUIRED_ITEMS.get(3))){
				targetMenu = new MenuEntry("Withdraw-1","Withdraw-1",1,1007,utils.getBankItemWidget(REQUIRED_ITEMS.get(3)).getIndex(),786444,false);
				utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
				return;
			}
		} catch (Exception ignored){

		}
	}

	private void clickAstralAltar()
	{
		GameObject targetObject = utils.findNearestGameObject(34771);
		if(targetObject!=null){
			targetMenu = new MenuEntry("Craft-rune","<col=ffff>Altar",targetObject.getId(),3,targetObject.getLocalLocation().getSceneX()-1,targetObject.getLocalLocation().getSceneY()-1,false);
			utils.setMenuEntry(null);
			utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
		}
	}

	private void teleToMoonclan()
	{
		targetMenu = new MenuEntry("Cast", "<col=00ff00>Tele Group Moonclan</col>", 1, 57, -1, 14286956, false);
		utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
	}

	private int checkRunEnergy()
	{
		try{
			return client.getEnergy();
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

	private void fixDegradedPouch()
	{
		if(utils.inventoryFull()){
			utils.depositAll();
			return;
		}
		if(utils.isBankOpen()){
			targetMenu = new MenuEntry("Close","",1,57,11,786434,false);
			utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
			return;
		}
		if(client.getWidget(231,4)!=null && client.getWidget(231,4).getText().contains("busy")){
			targetMenu = new MenuEntry("Continue","",0,30,-1,15138819,false);
			utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
			return;
		}
		if(client.getWidget(217,4)!=null && client.getWidget(217,4).getText().contains("essence")){
			targetMenu = new MenuEntry("Continue","",0,30,-1,14221315,false);
			utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
			return;
		}
		if(client.getWidget(231,2)==null || client.getWidget(231,2).isHidden()){
			targetMenu = new MenuEntry("Dark Mage","<col=00ff00>NPC Contact</col>",2,57,-1,14286952,false);
			utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
			return;
		}
	}

	private void shouldRun()
	{
		if(client.getWidget(160,23)!=null){ //if run widget is visible
			if(Integer.parseInt(client.getWidget(160,23).getText())>(30+utils.getRandomIntBetweenRange(0,20))){ //if run > 30+~20
				log.info(String.valueOf(client.getVarbitValue(173)));
				if(client.getWidget(160,27).getSpriteId()==1069){ //if run is off
					targetMenu = new MenuEntry("Toggle Run","",1,57,-1,10485782,false);
					utils.delayMouseClick(getRandomNullPoint(),sleepDelay());
					return;
				}
			}
		}
	}
}
