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
package net.runelite.client.plugins.ElTickCooker;

import com.google.inject.Provides;
import com.owain.chinbreakhandler.ChinBreakHandler;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.PluginType;
import net.runelite.client.plugins.botutils.BotUtils;
import net.runelite.client.ui.overlay.OverlayManager;
import org.pf4j.Extension;
import static net.runelite.client.plugins.ElTickCooker.ElTickCookerState.*;


@Extension
@PluginDependency(BotUtils.class)
@PluginDescriptor(
	name = "El Tick Cooker",
	enabledByDefault = false,
	description = "Cooks karambwans.",
	tags = {"tick, cook, food, cooking, el"},
	type = PluginType.SKILLING
)
@Slf4j
public class ElTickCookerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ElTickCookerConfiguration config;

	@Inject
	private BotUtils utils;

	@Inject
	private ConfigManager configManager;

	@Inject
	PluginManager pluginManager;

	@Inject
	OverlayManager overlayManager;

	@Inject
	private ElTickCookerOverlay overlay;

	@Inject
	private ChinBreakHandler chinBreakHandler;


	ElTickCookerState state;
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

	int timeout = 0;
	long sleepLength;
	boolean startTickCooker;

	int startRaw;
	int currentRaw;

	static final int garbageValue = 1292618906;
	static final String className = "ln";
	static final String methodName = "hs";


	@Provides
	ElTickCookerConfiguration provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ElTickCookerConfiguration.class);
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
		startTickCooker = false;
		startRaw=0;
		currentRaw=0;
		firstTime=true;
		clientTickDelay=0;

	}

	@Subscribe
	private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked)
	{
		if (!configButtonClicked.getGroup().equalsIgnoreCase("tickcooker"))
		{
			return;
		}
		log.debug("button {} pressed!", configButtonClicked.getKey());
		if (configButtonClicked.getKey().equals("startButton"))
		{
			if (!startTickCooker)
			{
				startTickCooker = true;
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
		if (!event.getGroup().equals("tickcooker"))
		{
			return;
		}
		startTickCooker = false;
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
		targetObject = utils.findNearestGameObjectWithin(client.getLocalPlayer().getWorldLocation(),25,config.rangeObjectId());
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

	private void interactFire()
	{
		targetObject = utils.findNearestGameObjectWithin(player.getWorldLocation(),25,26185);
		if(targetObject!=null){
			targetMenu = new MenuEntry("","",targetObject.getId(),1,targetObject.getSceneMinLocation().getX(),targetObject.getSceneMinLocation().getY(),false);
			utils.setModifiedMenuEntry(targetMenu,rawKarambwanId,utils.getInventoryWidgetItem(rawKarambwanId).getIndex(),1);
			if(targetObject.getConvexHull()!=null) {
				utils.delayMouseClick(targetObject.getConvexHull().getBounds(), sleepDelay());
			} else {
				utils.delayMouseClick(new Point(0,0),sleepDelay());
			}
		}
	}

	private void openBank()
	{
		targetObject = utils.findNearestGameObjectWithin(player.getWorldLocation(),25,config.bankObjectId());
		if (targetObject != null)
		{
			targetMenu = new MenuEntry("", "", targetObject.getId(), config.bankOpCode(),
					targetObject.getSceneMinLocation().getX(), targetObject.getSceneMinLocation().getY(), false);
			utils.setMenuEntry(targetMenu);
			utils.delayMouseClick(targetObject.getConvexHull().getBounds(), sleepDelay());
		}
		else
		{
			log.debug("Cooker is null.");
		}
	}

	private void openBankRogues()
	{
		targetNpc = utils.findNearestNpcWithin(player.getWorldLocation(),5, Collections.singleton(3194));
		if(targetNpc!=null){
			targetMenu = new MenuEntry("Bank", "<col=ffff00>Emerald Benedict", 15682, 11,
					0, 0, false);
			utils.setMenuEntry(targetMenu);
			utils.delayMouseClick(targetNpc.getConvexHull().getBounds(), sleepDelay());
		}
	}

	private ElTickCookerState getBankState()
	{
		if (startRaw == 0) {
			startRaw=utils.getBankItemWidget(rawKarambwanId).getItemQuantity();
		}
		currentRaw = utils.getBankItemWidget(rawKarambwanId).getItemQuantity();
		if(utils.inventoryEmpty()){
			return WITHDRAW_ITEMS;
		}
		if(!utils.inventoryFull() && !utils.inventoryEmpty()){
			return DEPOSIT_ITEMS;
		}
		if(utils.inventoryContains(rawKarambwanId)){ //inventory contains raw food
			if(utils.getInventoryItemCount(rawKarambwanId,false)==28){ //contains 28 raw food
				return FIND_OBJECT;
			}
		}
		if(!utils.inventoryContains(rawKarambwanId)){ //inventory doesnt contain raw food
			return DEPOSIT_ITEMS;
		}
		if(!utils.bankContains(rawKarambwanId,28)){
			return MISSING_ITEMS;
		}
		return UNHANDLED_STATE;
	}

	public ElTickCookerState getState()
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
		if (chinBreakHandler.shouldBreak(this))
		{
			return HANDLE_BREAK;
		}
		if(utils.isBankOpen()){ //if bank is open
			return getBankState(); //check bank state
		}
		if (!utils.inventoryFull()) //if invent is not full
		{
			return FIND_BANK; //find a bank
		}
		if (utils.inventoryFull()) //if invent is not full
		{
			return getTickCookerState();
		}
		return UNHANDLED_STATE;
	}

	@Subscribe
	private void onClientTick(ClientTick tick) {
		if (!startTickCooker || chinBreakHandler.isBreakActive(this))
		{
			return;
		}
		if(client.getWidget(270,15)!=null){
			if(!client.getWidget(270,15).isHidden()){
				utils.pressKey(KeyEvent.VK_SPACE);
				clientTickDelay=25;
			}
		}
	}

	@Subscribe
	private void onGameTick(GameTick tick)
	{
		if (!startTickCooker || chinBreakHandler.isBreakActive(this))
		{
			return;
		}
		player = client.getLocalPlayer();
		if (client != null && player != null && skillLocation != null)
		{
			if (!client.isResized())
			{
				utils.sendGameMessage("client must be set to resizable");
				startTickCooker = false;
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
					if(config.roguesDen()){
						interactFire();
					} else {
						interactCooker();
					}
					break;
				case MISSING_ITEMS:
					startTickCooker = false;
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
				case FIND_BANK:
					if(config.roguesDen()){
						openBankRogues();
					} else {
						openBank();
					}
					timeout = tickDelay();
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
		if (event.getGameState() == GameState.LOGGED_IN && startTickCooker)
		{
			state = TIMEOUT;
			timeout = 2;
		}
	}

	private ElTickCookerState getTickCookerState()
	{
		log.debug("getting cooker state");
		if(utils.inventoryContains(rawKarambwanId))
		{
			return FIND_OBJECT;
		} else {
			return FIND_BANK;
		}
	}


	@Subscribe
	private void onMenuOptionClicked(MenuOptionClicked event){
		log.debug(event.toString());
		if(config.valueFinder()){
			utils.sendGameMessage("Id: " + event.getIdentifier() + ", Op Code: " + event.getOpcode() + ".");
		}
		if(targetMenu!=null){
			event.consume();
			client.setSelectedItemWidget(WidgetInfo.INVENTORY.getId());
			client.setSelectedItemSlot(27);
			client.setSelectedItemID(3142);
			client.invokeMenuAction(targetMenu.getOption(),targetMenu.getTarget(),targetMenu.getIdentifier(),targetMenu.getOpcode(),targetMenu.getParam0(),targetMenu.getParam1());
			targetMenu=null;
		}
	}
}
