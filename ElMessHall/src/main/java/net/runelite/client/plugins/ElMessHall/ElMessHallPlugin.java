/*
 * Copyright (c) 2018, SomeoneWithAnInternetConnection
 * Copyright (c) 2018, oplosthee <https://github.com/oplosthee>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.ElMessHall;

import com.google.inject.Provides;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginType;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;
import org.pf4j.Extension;
import com.owain.chinbreakhandler.ChinBreakHandler;
import java.awt.event.KeyEvent;
import java.util.function.Function;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.botutils.BotUtils;
import static net.runelite.client.plugins.ElMessHall.ElMessHallState.*;


@Extension
@PluginDependency(BotUtils.class)
@PluginDescriptor(
	name = "El Mess Hall",
	enabledByDefault = false,
	description = "Does Mess Hall Minigame.",
	tags = {"cook, food, cooking, el"},
	type = PluginType.SKILLING
)
@Slf4j
public class ElMessHallPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ElMessHallConfiguration config;

	@Inject
	private BotUtils utils;

	@Inject
	private WorldService worldService;

	@Inject
	private ConfigManager configManager;

	@Inject
	PluginManager pluginManager;

	@Inject
	OverlayManager overlayManager;

	@Inject
	private ElMessHallOverlay overlay;

	@Inject
	private ChinBreakHandler chinBreakHandler;

	@Inject
	private ClientThread clientThread;


	private net.runelite.api.World quickHopTargetWorld = null;
	private int displaySwitcherAttempts = 0;

	ElMessHallState state;
	GameObject targetObject;
	NPC targetNpc;
	MenuEntry targetMenu;
	WorldPoint skillLocation;
	Instant botTimer;
	LocalPoint beforeLoc;
	Player player;
	boolean firstTime;
	int rawKarambwanId = 3142;
	int clientTickDelay;
	int pizzaProgress;

	int timeout = 0;
	long sleepLength;
	boolean startMessHall;

	int startRaw;
	int currentRaw;

	static final int garbageValue = 1292618906;
	static final String className = "ln";
	static final String methodName = "hs";


	@Provides
	ElMessHallConfiguration provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ElMessHallConfiguration.class);
	}

	@Override
	protected void startUp()
	{
		chinBreakHandler.registerPlugin(this);
		clientTickDelay=100;
	}

	@Override
	protected void shutDown()
	{
		resetVals();
		chinBreakHandler.unregisterPlugin(this);
	}

	private void resetVals()
	{
		overlayManager.remove(overlay);
		chinBreakHandler.stopPlugin(this);
		state = null;
		timeout = 0;
		botTimer = null;
		skillLocation = null;
		startMessHall = false;
		startRaw=0;
		currentRaw=0;
		firstTime=true;
		clientTickDelay=0;

	}

	@Subscribe
	private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked)
	{
		if (!configButtonClicked.getGroup().equalsIgnoreCase("ElMessHall"))
		{
			return;
		}
		log.debug("button {} pressed!", configButtonClicked.getKey());
		if (configButtonClicked.getKey().equals("startButton"))
		{
			if (!startMessHall)
			{
				startMessHall = true;
				chinBreakHandler.startPlugin(this);
				state = null;
				targetMenu = null;
				botTimer = Instant.now();
				setLocation();
				overlayManager.add(overlay);
				firstTime=true;
			}
			else
			{
				resetVals();
			}
		}
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("ElMessHall"))
		{
			return;
		}
		startMessHall = false;
	}

	public void setLocation()
	{
		if (client != null && client.getLocalPlayer() != null && client.getGameState().equals(GameState.LOGGED_IN))
		{
			skillLocation = client.getLocalPlayer().getWorldLocation();
			beforeLoc = client.getLocalPlayer().getLocalLocation();
		}
		else
		{
			log.debug("Tried to start bot before being logged in");
			skillLocation = null;
			resetVals();
		}
	}

	private long sleepDelay()
	{
		sleepLength = utils.randomDelay(config.sleepWeightedDistribution(), config.sleepMin(), config.sleepMax(), config.sleepDeviation(), config.sleepTarget());
		return sleepLength;
	}

	private int tickDelay()
	{
		int tickLength = (int) utils.randomDelay(config.tickDelayWeightedDistribution(), config.tickDelayMin(), config.tickDelayMax(), config.tickDelayDeviation(), config.tickDelayTarget());
		log.debug("tick delay for {} ticks", tickLength);
		return tickLength;
	}

	private void interactCooker()
	{
		//targetObject = utils.findNearestGameObjectWithin(client.getLocalPlayer().getWorldLocation(),25,config.rangeObjectId());
		if(targetObject!=null){
			targetMenu = new MenuEntry("","",targetObject.getId(),1,targetObject.getSceneMinLocation().getX(),targetObject.getSceneMinLocation().getY(),false);
			utils.setModifiedMenuEntry(targetMenu,rawKarambwanId,utils.getInventoryWidgetItem(rawKarambwanId).getIndex(),1);
			if(targetObject.getConvexHull()!=null) {
				utils.delayMouseClick(targetObject.getConvexHull().getBounds(), sleepDelay());
			} else {
				utils.delayMouseClick(new Point(0,0),sleepDelay());
			}
		} else {
			utils.sendGameMessage("cooker is null.");
		}
	}

	public ElMessHallState getState()
	{
		if (timeout > 0)
		{
			return TIMEOUT;
		}
		if (utils.isMoving(beforeLoc))
		{
			timeout = tickDelay();
			return MOVING;
		}
		if(player.getAnimation()!=-1){
			return ANIMATING;
		}
		if (chinBreakHandler.shouldBreak(this))
		{
			return HANDLE_BREAK;
		}
		switch (config.type()){
			case PINEAPPLE_PIZZA:
				return getPizzaState();
			default:
				return UNHANDLED_STATE;
		}
	}

	@Subscribe
	private void onClientTick(ClientTick tick) {
		if (!startMessHall || chinBreakHandler.isBreakActive(this))
		{
			return;
		}
		if(client.getWidget(219,1)!=null){
			if(!client.getWidget(219,1).isHidden()){
				utils.pressKey('2');
			}
		}
		if(clientTickDelay>0){
			clientTickDelay--;
			return;
		}
		switch(pizzaProgress){
			case 4:
				makeDough();
				break;
			case 7:
				addTomatoes();
				break;
			case 9:
				addCheese();
				break;
			case 12:
				cutPineapples();
				break;
			case 13:
				addPineapples();
				break;
		}
	}

	@Subscribe
	private void onGameTick(GameTick tick)
	{
		if(quickHopTargetWorld!=null){
			client.hopToWorld(quickHopTargetWorld);
			resetQuickHopper();
		}
		if (!startMessHall || chinBreakHandler.isBreakActive(this))
		{
			return;
		}
		player = client.getLocalPlayer();
		if (client != null && player != null && skillLocation != null)
		{
			if (!client.isResized())
			{
				utils.sendGameMessage("client must be set to resizable");
				startMessHall = false;
				return;
			}
			state = getState();
			beforeLoc = player.getLocalLocation();
			switch (state)
			{
				case TIMEOUT:
					utils.handleRun(30, 20);
					timeout--;
					break;
				case FIND_OBJECT:
					interactCooker();
					break;
				case MISSING_ITEMS:
					startMessHall = false;
					utils.sendGameMessage("OUT OF FOOD");
					resetVals();
					break;
				case HANDLE_BREAK:
					firstTime=true;
					chinBreakHandler.startBreak(this);
					timeout = 10;
					break;
				//case ANIMATING:
				case MOVING:
					firstTime=true;
					utils.handleRun(30, 20);
					timeout = 1+tickDelay();
					break;
				case DEPOSIT_ITEMS:
					utils.depositAll();
					timeout = tickDelay();
					break;
				case WITHDRAW_ITEMS:
					utils.withdrawAllItem(rawKarambwanId);
					timeout = tickDelay();
					break;
			}
		}
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN && startMessHall)
		{
			state = TIMEOUT;
			timeout = 2;
		}
	}

	private ElMessHallState getPizzaState()
	{
		if(utils.inventoryEmpty()){
			pizzaProgress=0;
		}
		switch (pizzaProgress) {
			case 0:
				getKnife();
				break;
			case 1:
				getFlour();
				break;
			case 2:
				getBowls();
				break;
			case 3:
				fillBowls();
				break;
			case 4:
				//makeDough();
				break;
			case 5:
				ridBowls();
				break;
			case 6:
				getTomatoes();
				break;
			case 7:
				//addTomatoes();
				break;
			case 8:
				getCheese();
				break;
			case 9:
				//addCheese();
				break;
			case 10:
				cookPizza();
				break;
			case 11:
				getPineapples();
				break;
			case 12:
				//cutPineapples();
				break;
			case 13:
				//addPineapples();
				break;
			case 14:
				serveFood();
				break;
			case 15:
				hopWorlds();
				break;
		}
		 return UNHANDLED_STATE;
	}


	@Subscribe
	private void onMenuOptionClicked(MenuOptionClicked event){
		log.debug(event.toString());
		if(targetMenu!=null){
			event.consume();
			client.invokeMenuAction(targetMenu.getOption(),targetMenu.getTarget(),targetMenu.getIdentifier(),targetMenu.getOpcode(),targetMenu.getParam0(),targetMenu.getParam1());
			targetMenu=null;
		}
	}

	private void getKnife(){
		if(!utils.inventoryItemContainsAmount(946,2,false,true)){
			if(client.getWidget(242,3)!=null && !client.getWidget(242,3).isHidden()){
				targetMenu = new MenuEntry("","",1,57,2,15859715,false);
				utils.delayMouseClick(client.getWidget(242,3).getChild(2).getBounds(), sleepDelay());
			} else {
				targetObject = utils.findNearestGameObject(27376);
				if(targetObject!=null){
					targetMenu = new MenuEntry("","",targetObject.getId(),3,targetObject.getSceneMinLocation().getX(),targetObject.getSceneMinLocation().getY(),false);
					utils.delayMouseClick(targetObject.getConvexHull().getBounds(), sleepDelay());
				}
			}
		} else {
			pizzaProgress++;
		}
	}

	private void getFlour(){
		if(client.getWidget(149,0)==null || client.getWidget(149,0).isHidden()){
			targetMenu=new MenuEntry("","",1,57,-1,10551353,false);
			utils.delayMouseClick(client.getWidget(161,64).getBounds(),sleepDelay());
			return;
		}
		if(!utils.inventoryItemContainsAmount(1923,13,false,true)){
			if(client.getWidget(162,45)!=null&&!client.getWidget(162,45).isHidden()){
				client.setVar(VarClientInt.INPUT_TYPE,7);
				client.setVar(VarClientStr.INPUT_TEXT,String.valueOf(13-utils.getInventoryItemCount(1923,false)));
				client.runScript(681);
				client.runScript(ScriptID.MESSAGE_LAYER_CLOSE);
			}else if(client.getWidget(242,3)!=null&&!client.getWidget(242,3).isHidden()){
				targetMenu=new MenuEntry("","",5,57,1,15859715,false);
				utils.delayMouseClick(client.getWidget(242,3).getChild(1).getBounds(),sleepDelay());
			}else{
				targetObject=utils.findNearestGameObject(27376);
				if(targetObject!=null){
					targetMenu=new MenuEntry("","",targetObject.getId(),3,targetObject.getSceneMinLocation().getX(),targetObject.getSceneMinLocation().getY(),false);
					utils.delayMouseClick(targetObject.getConvexHull().getBounds(),sleepDelay());
				}
				timeout++;
			}
		} else {
			pizzaProgress++;
		}
	}

	private void getBowls(){
		if(!utils.inventoryItemContainsAmount(13397,13,false,true)) {
			if (client.getWidget(162, 45) != null && !client.getWidget(162, 45).isHidden()) {
				client.setVar(VarClientInt.INPUT_TYPE, 7);
				client.setVar(VarClientStr.INPUT_TEXT, String.valueOf(13 - utils.getInventoryItemCount(13397, false)));
				client.runScript(681);
				client.runScript(ScriptID.MESSAGE_LAYER_CLOSE);
			} else if (client.getWidget(242, 3) != null && !client.getWidget(242, 3).isHidden()) {
				if (client.getWidget(242, 3).getChild(1).getName().contains("Bowl")) {
					targetMenu = new MenuEntry("", "", 1, 57, 11, 15859713, false);
					utils.delayMouseClick(client.getWidget(242, 1).getChild(11).getBounds(), sleepDelay());
				} else {
					targetMenu = new MenuEntry("", "", 5, 57, 0, 15859715, false);
					utils.delayMouseClick(client.getWidget(242, 3).getChild(0).getBounds(), sleepDelay());
				}
			} else {
				targetObject = utils.findNearestGameObject(27375);
				if (targetObject != null) {
					targetMenu = new MenuEntry("", "", targetObject.getId(), 3, targetObject.getSceneMinLocation().getX(), targetObject.getSceneMinLocation().getY(), false);
					utils.delayMouseClick(targetObject.getConvexHull().getBounds(), sleepDelay());
				}
				timeout++;
			}
		} else {
			pizzaProgress++;
		}
	}

	private void fillBowls(){
		if(utils.inventoryContains(1923)){
			targetObject = utils.findNearestGameObject(9684);
			if(targetObject!=null){
				client.setSelectedItemWidget(WidgetInfo.INVENTORY.getId());
				client.setSelectedItemSlot(utils.getInventoryWidgetItem(1923).getIndex());
				client.setSelectedItemID(1923);
				targetMenu = new MenuEntry("","",targetObject.getId(),1,targetObject.getSceneMinLocation().getX(),targetObject.getSceneMinLocation().getY(),false);
				utils.delayMouseClick(targetObject.getConvexHull().getBounds(), sleepDelay());
			}
		} else {
			pizzaProgress++;
		}
	}

	private void makeDough(){
		if(utils.inventoryContains(1921)){
			client.setSelectedItemWidget(WidgetInfo.INVENTORY.getId());
			client.setSelectedItemSlot(14);
			client.setSelectedItemID(1921);
			targetMenu = new MenuEntry("","",13397,31,27,9764864,false);
			utils.delayMouseClick(utils.getInventoryWidgetItem(13397).getCanvasBounds(),0);
			clientTickDelay=15;
		} else {
			pizzaProgress++;
		}
	}

	private void ridBowls(){
		if(utils.inventoryContains(1923)){
			targetObject=utils.findNearestGameObject(27376);
			if(targetObject!=null){
				client.setSelectedItemWidget(WidgetInfo.INVENTORY.getId());
				client.setSelectedItemSlot(utils.getInventoryWidgetItem(1923).getIndex());
				client.setSelectedItemID(1923);
				targetMenu = new MenuEntry("","",targetObject.getId(),1,targetObject.getSceneMinLocation().getX(),targetObject.getSceneMinLocation().getY(),false);
				utils.delayMouseClick(targetObject.getConvexHull().getBounds(),sleepDelay());
			}
		} else {
			pizzaProgress++;
		}

	}

	private void getTomatoes(){
		if(!utils.inventoryItemContainsAmount(13405,13,false,true)){
			if(client.getWidget(162,45)!=null&&!client.getWidget(162,45).isHidden()){
				client.setVar(VarClientInt.INPUT_TYPE,7);
				client.setVar(VarClientStr.INPUT_TEXT,String.valueOf(13-utils.getInventoryItemCount(13405,false)));
				client.runScript(681);
				client.runScript(ScriptID.MESSAGE_LAYER_CLOSE);
			}else if(client.getWidget(242,3)!=null&&!client.getWidget(242,3).isHidden()){
				targetMenu=new MenuEntry("","",5,57,4,15859715,false);
				utils.delayMouseClick(client.getWidget(242,3).getChild(5).getBounds(),sleepDelay());
			}else{
				targetObject=utils.findNearestGameObject(27375);
				if(targetObject!=null){
					targetMenu=new MenuEntry("","",targetObject.getId(),3,targetObject.getSceneMinLocation().getX(),targetObject.getSceneMinLocation().getY(),false);
					utils.delayMouseClick(targetObject.getConvexHull().getBounds(),sleepDelay());
				}
				timeout++;
			}
		} else {
			pizzaProgress++;
		}
	}

	private void addTomatoes(){
		if(client.getWidget(242,1)!=null && !client.getWidget(242,1).isHidden()){
			targetMenu=new MenuEntry("","",1,57,11,15859713,false);
			utils.delayMouseClick(client.getWidget(242,1).getChild(11).getBounds(),0);
			clientTickDelay=25;
		} else if(utils.inventoryContains(13405)){
			client.setSelectedItemWidget(WidgetInfo.INVENTORY.getId());
			client.setSelectedItemSlot(14);
			client.setSelectedItemID(13404);
			targetMenu = new MenuEntry("","",13405,31,27,9764864,false);
			utils.delayMouseClick(utils.getInventoryWidgetItem(13405).getCanvasBounds(),0);
			clientTickDelay=15;
		} else {
			pizzaProgress++;
		}
	}

	private void getCheese(){
		if(!utils.inventoryItemContainsAmount(13407,13,false,true)){
			if(client.getWidget(162,45)!=null&&!client.getWidget(162,45).isHidden()){
				client.setVar(VarClientInt.INPUT_TYPE,7);
				client.setVar(VarClientStr.INPUT_TEXT,String.valueOf(13-utils.getInventoryItemCount(13407,false)));
				client.runScript(681);
				client.runScript(ScriptID.MESSAGE_LAYER_CLOSE);
			}else if(client.getWidget(242,3)!=null&&!client.getWidget(242,3).isHidden()){
				targetMenu=new MenuEntry("","",5,57,3,15859715,false);
				utils.delayMouseClick(client.getWidget(242,3).getChild(4).getBounds(),sleepDelay());
			}else{
				targetObject=utils.findNearestGameObject(27375);
				if(targetObject!=null){
					targetMenu=new MenuEntry("","",targetObject.getId(),3,targetObject.getSceneMinLocation().getX(),targetObject.getSceneMinLocation().getY(),false);
					utils.delayMouseClick(targetObject.getConvexHull().getBounds(),sleepDelay());
				}
				timeout++;
			}
		} else {
			pizzaProgress++;
		}
	}

	private void addCheese(){
		if(client.getWidget(242,1)!=null && !client.getWidget(242,1).isHidden()){
			targetMenu=new MenuEntry("","",1,57,11,15859713,false);
			utils.delayMouseClick(client.getWidget(242,1).getChild(11).getBounds(),0);
			clientTickDelay=25;
		} else if(utils.inventoryContains(13406)){
			client.setSelectedItemWidget(WidgetInfo.INVENTORY.getId());
			client.setSelectedItemSlot(14);
			client.setSelectedItemID(13406);
			targetMenu = new MenuEntry("","",13407,31,27,9764864,false);
			utils.delayMouseClick(utils.getInventoryWidgetItem(13407).getCanvasBounds(),0);
			clientTickDelay=15;
		} else {
			pizzaProgress++;
		}
	}

	private void cookPizza(){
		if(utils.inventoryContains(13408)){
			if(client.getWidget(WidgetInfo.MULTI_SKILL_MENU)!=null && !client.getWidget(WidgetInfo.MULTI_SKILL_MENU).isHidden()){
				utils.pressKey(KeyEvent.VK_SPACE);
			} else {
				targetObject=utils.findNearestGameObject(21302);
				if(targetObject!=null){
					client.setSelectedItemWidget(WidgetInfo.INVENTORY.getId());
					client.setSelectedItemSlot(14);
					client.setSelectedItemID(13408);
					targetMenu = new MenuEntry("","",targetObject.getId(),1,targetObject.getSceneMinLocation().getX(),targetObject.getSceneMinLocation().getY(),false);
					utils.delayMouseClick(targetObject.getConvexHull().getBounds(),0);
				}
			}
		} else {
			pizzaProgress++;
		}
	}

	private void getPineapples(){
		if(!utils.inventoryContains(13410)){
			if(client.getWidget(162,45)!=null&&!client.getWidget(162,45).isHidden()){
				client.setVar(VarClientInt.INPUT_TYPE,7);
				client.setVar(VarClientStr.INPUT_TEXT,String.valueOf(13-utils.getInventoryItemCount(13407,false)));
				client.runScript(681);
				client.runScript(ScriptID.MESSAGE_LAYER_CLOSE);
			}else if(client.getWidget(242,3)!=null&&!client.getWidget(242,3).isHidden()){
				targetMenu=new MenuEntry("","",5,57,1,15859715,false);
				utils.delayMouseClick(client.getWidget(242,3).getChild(1).getBounds(),sleepDelay());
			}else{
				targetObject=utils.findNearestGameObject(27375);
				if(targetObject!=null){
					targetMenu=new MenuEntry("","",targetObject.getId(),3,targetObject.getSceneMinLocation().getX(),targetObject.getSceneMinLocation().getY(),false);
					utils.delayMouseClick(targetObject.getConvexHull().getBounds(),sleepDelay());
				}
				timeout++;
			}
		} else {
			pizzaProgress++;
		}
	}

	private void cutPineapples(){
		if(client.getWidget(242,1)!=null && !client.getWidget(242,1).isHidden()){
			targetMenu=new MenuEntry("","",1,57,11,15859713,false);
			utils.delayMouseClick(client.getWidget(242,1).getChild(11).getBounds(),0);
			clientTickDelay=25;
		} else if(utils.inventoryContains(13410)){
			client.setSelectedItemWidget(WidgetInfo.INVENTORY.getId());
			client.setSelectedItemSlot(1);
			client.setSelectedItemID(946);
			targetMenu = new MenuEntry("","",13410,31,27,9764864,false);
			utils.delayMouseClick(utils.getInventoryWidgetItem(13410).getCanvasBounds(),0);
			clientTickDelay=15;
		} else {
			pizzaProgress++;
		}
	}

	private void addPineapples(){
		if(utils.inventoryContains(13409)){
			client.setSelectedItemWidget(WidgetInfo.INVENTORY.getId());
			client.setSelectedItemSlot(14);
			client.setSelectedItemID(13409);
			targetMenu = new MenuEntry("","",13411,31,27,9764864,false);
			utils.delayMouseClick(utils.getInventoryWidgetItem(13411).getCanvasBounds(),0);
			clientTickDelay=15;
		} else {
			pizzaProgress++;
		}
	}

	private void serveFood(){
		if(utils.inventoryContains(13412)){
			targetObject=utils.findNearestGameObject(27378);
			if(targetObject!=null){
				targetMenu=new MenuEntry("","",targetObject.getId(),3,targetObject.getSceneMinLocation().getX(),targetObject.getSceneMinLocation().getY(),false);
				utils.delayMouseClick(targetObject.getConvexHull().getBounds(),0);
			}
		} else {
			pizzaProgress++;
		}
	}

	private void hopWorlds(){
		hop(false);
		timeout=6;
		pizzaProgress=1;
	}

	private void hop(boolean previous)
	{
		WorldResult worldResult = worldService.getWorlds();
		if (worldResult == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		World currentWorld = worldResult.findWorld(client.getWorld());

		if (currentWorld == null)
		{
			return;
		}

		EnumSet<WorldType> currentWorldTypes = currentWorld.getTypes().clone();

		currentWorldTypes.remove(WorldType.SKILL_TOTAL);
		currentWorldTypes.remove(WorldType.LAST_MAN_STANDING);
		currentWorldTypes.remove(WorldType.PVP);
		currentWorldTypes.remove(WorldType.HIGH_RISK);
		currentWorldTypes.remove(WorldType.BOUNTY);

		List<World> worlds = worldResult.getWorlds();

		int worldIdx = worlds.indexOf(currentWorld);
		int totalLevel = client.getTotalLevel();

		World world;
		do
		{
			/*
				Get the previous or next world in the list,
				starting over at the other end of the list
				if there are no more elements in the
				current direction of iteration.
			 */
			if (previous)
			{
				worldIdx--;

				if (worldIdx < 0)
				{
					worldIdx = worlds.size() - 1;
				}
			}
			else
			{
				worldIdx++;

				if (worldIdx >= worlds.size())
				{
					worldIdx = 0;
				}
			}

			world = worlds.get(worldIdx);

			EnumSet<WorldType> types = world.getTypes().clone();

			types.remove(WorldType.BOUNTY);
			// Treat LMS world like casual world
			types.remove(WorldType.LAST_MAN_STANDING);

			if (types.contains(WorldType.SKILL_TOTAL))
			{
				try
				{
					int totalRequirement = Integer.parseInt(world.getActivity().substring(0, world.getActivity().indexOf(" ")));

					if (totalLevel >= totalRequirement)
					{
						types.remove(WorldType.SKILL_TOTAL);
					}
				}
				catch (NumberFormatException ex)
				{
					log.warn("Failed to parse total level requirement for target world", ex);
				}
			}

			// Break out if we've found a good world to hop to
			if (currentWorldTypes.equals(types))
			{
				break;
			}
		}
		while (world != currentWorld);

		if (world == currentWorld)
		{
			String chatMessage = new ChatMessageBuilder()
					.append(ChatColorType.NORMAL)
					.append("Couldn't find a world to quick-hop to.")
					.build();
		}
		else
		{
			hop(world.getId());
		}
	}

	private void hop(int worldId)
	{
		WorldResult worldResult = worldService.getWorlds();
		// Don't try to hop if the world doesn't exist
		@SuppressWarnings("ConstantConditions")
		World world = worldResult.findWorld(worldId);
		if (world == null)
		{
			return;
		}

		final net.runelite.api.World rsWorld = client.createWorld();
		rsWorld.setActivity(world.getActivity());
		rsWorld.setAddress(world.getAddress());
		rsWorld.setId(world.getId());
		rsWorld.setPlayerCount(world.getPlayers());
		rsWorld.setLocation(world.getLocation());
		rsWorld.setTypes(WorldUtil.toWorldTypes(world.getTypes()));

		quickHopTargetWorld = rsWorld;
		displaySwitcherAttempts = 0;
	}

	private void resetQuickHopper()
	{
		displaySwitcherAttempts = 0;
		quickHopTargetWorld = null;
	}
}
