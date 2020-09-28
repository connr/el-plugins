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
package net.runelite.client.plugins.cooker;

import com.google.inject.Provides;
import com.owain.chinbreakhandler.ChinBreakHandler;
import java.awt.Rectangle;
import java.time.Instant;
import java.util.*;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
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
import static net.runelite.client.plugins.cooker.cookerState.*;


@Extension
@PluginDependency(BotUtils.class)
@PluginDescriptor(
	name = "El Cooker",
	enabledByDefault = false,
	description = "Cooks food.",
	tags = {"cook, food, cooking, el"},
	type = PluginType.SKILLING
)
@Slf4j
public class cookerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private cookerConfiguration config;

	@Inject
	private BotUtils utils;

	@Inject
	private ConfigManager configManager;

	@Inject
	PluginManager pluginManager;

	@Inject
	OverlayManager overlayManager;

	@Inject
	private cookerOverlay overlay;

	@Inject
	private ChinBreakHandler chinBreakHandler;


	cookerState state;
	GameObject targetObject;
	MenuEntry targetMenu;
	WorldPoint skillLocation;
	Instant botTimer;
	LocalPoint beforeLoc;
	Player player;
	Rectangle altRect = new Rectangle(-100,-100, 10, 10);

	WorldPoint HOSIDIUS_BANK = new WorldPoint(1676,3615,0);
	WorldPoint HOSIDIUS_RANGE = new WorldPoint(1677,3621,0);

	WorldArea HOSIDIUS_HOUSE = new WorldArea(new WorldPoint(1673,3613,0),new WorldPoint(1685,3624,0));

	int timeout = 0;
	long sleepLength;
	boolean startCooker;
	private final Set<Integer> itemIds = new HashSet<>();

	int startRaw;
	int currentRaw;


	@Provides
	cookerConfiguration provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(cookerConfiguration.class);
	}

	@Override
	protected void startUp()
	{
		chinBreakHandler.registerPlugin(this);
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
		startCooker = false;
		startRaw=0;
		currentRaw=0;
	}

	@Subscribe
	private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked)
	{
		if (!configButtonClicked.getGroup().equalsIgnoreCase("cooker"))
		{
			return;
		}
		log.info("button {} pressed!", configButtonClicked.getKey());
		if (configButtonClicked.getKey().equals("startButton"))
		{
			if (!startCooker)
			{
				startCooker = true;
				chinBreakHandler.startPlugin(this);
				state = null;
				targetMenu = null;
				botTimer = Instant.now();
				setLocation();
				overlayManager.add(overlay);
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
		if (!event.getGroup().equals("karambwanfisher"))
		{
			return;
		}
		startCooker = false;
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
		targetObject = utils.findNearestGameObjectWithin(player.getWorldLocation(),10,21302);
		if (targetObject != null)
		{
			targetMenu = new MenuEntry("", "", targetObject.getId(), 3,
					targetObject.getSceneMinLocation().getX(), targetObject.getSceneMinLocation().getY(), false);
			utils.setMenuEntry(targetMenu);
			utils.delayMouseClick(targetObject.getConvexHull().getBounds(), sleepDelay());
		}
		else
		{
			log.info("Cooker is null.");
		}
	}

	private void openBank()
	{
		targetObject = utils.findNearestGameObjectWithin(player.getWorldLocation(),10,21301);
		if (targetObject != null)
		{
			targetMenu = new MenuEntry("", "", targetObject.getId(), 3,
					targetObject.getSceneMinLocation().getX(), targetObject.getSceneMinLocation().getY(), false);
			utils.setMenuEntry(targetMenu);
			utils.delayMouseClick(targetObject.getConvexHull().getBounds(), sleepDelay());
		}
		else
		{
			log.info("Cooker is null.");
		}
	}

	private cookerState getBankState()
	{
		if (startRaw == 0) {
			startRaw=utils.getBankItemWidget(config.rawFoodId()).getItemQuantity();
		}
		currentRaw = utils.getBankItemWidget(config.rawFoodId()).getItemQuantity();
		if(utils.inventoryEmpty()){
			return WITHDRAW_ITEMS;
		}
		if(!utils.inventoryFull() && !utils.inventoryEmpty()){
			return DEPOSIT_ITEMS;
		}
		if(utils.inventoryContains(config.rawFoodId())){ //inventory contains raw food
			if(utils.getInventoryItemCount(config.rawFoodId(),false)==28){ //contains 28 raw food
				return FIND_OBJECT;
			}
		}
		if(!utils.inventoryContains(config.rawFoodId())){ //inventory doesnt contain raw food
			return DEPOSIT_ITEMS;
		}
		if(!utils.bankContains(config.rawFoodId(),28)){
			return MISSING_ITEMS;
		}
		return UNHANDLED_STATE;
	}

	public cookerState getState()
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
		if(client.getLocalPlayer().getAnimation()!=-1){
			return ANIMATING;
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
			return getCookerState(); //find a bank
		}
		return UNHANDLED_STATE;
	}

	@Subscribe
	private void onGameTick(GameTick tick)
	{
		if (!startCooker || chinBreakHandler.isBreakActive(this))
		{
			return;
		}
		player = client.getLocalPlayer();
		if (client != null && player != null && skillLocation != null)
		{
			if (!client.isResized())
			{
				utils.sendGameMessage("client must be set to resizable");
				startCooker = false;
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
					timeout = tickDelay();
					break;
				case MISSING_ITEMS:
					startCooker = false;
					utils.sendGameMessage("OUT OF FOOD");
					resetVals();
					break;
				case HANDLE_BREAK:
					chinBreakHandler.startBreak(this);
					timeout = 10;
					break;
				case ANIMATING:
				case MOVING:
					utils.handleRun(30, 20);
					timeout = tickDelay();
					break;
				case FIND_BANK:
					openBank();
					timeout = tickDelay();
					break;
				case DEPOSIT_ITEMS:
					utils.depositAll();
					timeout = tickDelay();
					break;
				case WITHDRAW_ITEMS:
					utils.withdrawAllItem(config.rawFoodId());
					timeout = tickDelay();
					break;
			}
		}
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN && startCooker)
		{
			state = TIMEOUT;
			timeout = 2;
		}
	}

	private cookerState getCookerState()
	{
		log.info("getting cooker state");
		if(utils.inventoryContains(config.rawFoodId()))
		{
			if(client.getWidget(270,15)!=null){
				if(client.getWidget(270,15).getName().equals("<col=ff9040>Cooked karambwan</col>")){
					targetMenu=new MenuEntry("Cook","<col=ff9040>Cooked karambwan</col>",1,57,-1,17694735,false);
					utils.setMenuEntry(targetMenu);
					if(client.getWidget(270,15).getBounds()!=null){
						utils.delayMouseClick(client.getWidget(270,15).getBounds(), sleepDelay());
					} else {
						utils.delayMouseClick(new Point(0,0), sleepDelay());
					}
				}
			} else if(!utils.inventoryItemContainsAmount(config.rawFoodId(),28,false,true)){
				return FIND_OBJECT;
			}
		} else {
			return FIND_BANK;
		}
		return TIMEOUT;
	}

	@Subscribe
	private void onMenuOptionClicked(MenuOptionClicked event){
		log.info(event.toString());
	}
}
