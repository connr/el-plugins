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
package net.runelite.client.plugins.ElAirs;

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
import static net.runelite.client.plugins.ElAirs.ElAirsState.*;


@Extension
@PluginDependency(BotUtils.class)
@PluginDescriptor(
	name = "El Airs",
	enabledByDefault = false,
	description = "Crafts at the air altar.",
	tags = {"rune, craft, runecraft, air, el"},
	type = PluginType.SKILLING
)
@Slf4j
public class ElAirsPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ElAirsConfiguration config;

	@Inject
	private BotUtils utils;

	@Inject
	private ConfigManager configManager;

	@Inject
	PluginManager pluginManager;

	@Inject
	OverlayManager overlayManager;

	@Inject
	private ElAirsOverlay overlay;

	@Inject
	private ChinBreakHandler chinBreakHandler;


	ElAirsState state;
	GameObject targetObject;
	NPC targetNPC;
	MenuEntry targetMenu;
	WorldPoint skillLocation;
	Instant botTimer;
	LocalPoint beforeLoc;
	Player player;
	Rectangle altRect = new Rectangle(-100,-100, 10, 10);

	WorldArea SOUTH_SHORTCUT = new WorldArea(new WorldPoint(3713,3800,0),new WorldPoint(3744,3817,0));

	int timeout = 0;
	int teaksCut;
	long sleepLength;
	boolean startTeaks;
	int oldInventCount = -1;


	@Provides
	ElAirsConfiguration provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ElAirsConfiguration.class);
	}

	@Override
	protected void startUp()
	{
		resetVals();
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
		startTeaks = false;
		oldInventCount=-1;
	}

	@Subscribe
	private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked)
	{
		if (!configButtonClicked.getGroup().equalsIgnoreCase("ElAirs"))
		{
			return;
		}
		log.info("button {} pressed!", configButtonClicked.getKey());
		if (configButtonClicked.getKey().equals("startButton"))
		{
			if (!startTeaks)
			{
				startTeaks = true;
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
		if (!event.getGroup().equals("ElAirs"))
		{
			return;
		}
		startTeaks = false;
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

	private void openBank()
	{
		targetObject = utils.findNearestGameObject(31427);
		if (targetObject != null)
		{
			targetMenu=new MenuEntry("","",targetObject.getId(),3,targetObject.getSceneMinLocation().getX(),targetObject.getSceneMinLocation().getY(),false);
			utils.setMenuEntry(targetMenu);
			utils.delayMouseClick(targetObject.getConvexHull().getBounds(), sleepDelay());
		}
		else
		{
			log.info("Banker is null");
		}
	}

	private ElAirsState getBankState()
	{
		if(utils.inventoryFull()){
			return DEPOSIT_ITEMS;
		}
		if(!utils.inventoryFull()){
			return USE_SHORTCUT;
		}
		return UNHANDLED_STATE;
	}

	public ElAirsState getState()
	{
		if (timeout > 0)
		{
			return TIMEOUT;
		}
		else if (utils.isMoving(beforeLoc))
		{
			timeout = 2 + tickDelay();
			return MOVING;
		}
		else if (chinBreakHandler.shouldBreak(this) && !player.getWorldArea().intersectsWith(SOUTH_SHORTCUT))
		{
			return HANDLE_BREAK;
		}
		else if(utils.isBankOpen()){
			return getBankState();
		}
		else if(client.getLocalPlayer().getAnimation()!=-1){
			return ANIMATING;
		}
		else {
			return getAirsState();
		}
	}

	@Subscribe
	private void onGameTick(GameTick tick)
	{
		if (!startTeaks || chinBreakHandler.isBreakActive(this))
		{
			return;
		}
		player = client.getLocalPlayer();
		if (client != null && player != null && skillLocation != null)
		{
			if (!client.isResized())
			{
				utils.sendGameMessage("client must be set to resizable");
				startTeaks = false;
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
				case FIND_TEAK:
					chopTeak();
					timeout = tickDelay();
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
				case USE_SHORTCUT:
					useShortcut();
					break;
			}
		}
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN && startTeaks)
		{
			state = TIMEOUT;
			timeout = 2;
		}
	}

	private ElAirsState getAirsState()
	{
		if(!utils.inventoryFull()){
			if(player.getWorldArea().intersectsWith(SOUTH_SHORTCUT)){
				return USE_SHORTCUT;
			} else {
				return FIND_TEAK;
			}
		} else {
			if(player.getWorldArea().intersectsWith(SOUTH_SHORTCUT)){
				return FIND_BANK;
			} else {
				return USE_SHORTCUT;
			}
		}
	}

	@Subscribe
	private void onMenuOptionClicked(MenuOptionClicked event){
		log.debug(event.toString());
	}

	private void useShortcut()
	{
		if(player.getWorldArea().intersectsWith(SOUTH_SHORTCUT)){
			targetObject = utils.findNearestGameObject(31481);
			if(targetObject!=null){
				targetMenu=new MenuEntry("","",targetObject.getId(),3,targetObject.getSceneMinLocation().getX(),targetObject.getSceneMinLocation().getY(),false);
				utils.setMenuEntry(targetMenu);
				utils.delayMouseClick(targetObject.getConvexHull().getBounds(),sleepDelay());
			}
		} else {
			targetObject = utils.findNearestGameObject(31482);
			if(targetObject!=null){
				targetMenu=new MenuEntry("","",targetObject.getId(),3,targetObject.getSceneMinLocation().getX(),targetObject.getSceneMinLocation().getY(),false);
				utils.setMenuEntry(targetMenu);
				utils.delayMouseClick(targetObject.getConvexHull().getBounds(),sleepDelay());
			}
		}
	}

	private void chopTeak()
	{
		if(client.getVarbitValue(4953)==0){
			targetObject=utils.findNearestGameObject(30482);
			if(targetObject!=null){
				targetMenu=new MenuEntry("","",targetObject.getId(),3,targetObject.getSceneMinLocation().getX(),targetObject.getSceneMinLocation().getY(),false);
				utils.setMenuEntry(targetMenu);
				utils.delayMouseClick(targetObject.getConvexHull().getBounds(),sleepDelay());
			}
		} else if(client.getVarbitValue(4955)==0){
			targetObject=utils.findNearestGameObject(30480);
			if(targetObject!=null){
				targetMenu=new MenuEntry("","",targetObject.getId(),3,targetObject.getSceneMinLocation().getX(),targetObject.getSceneMinLocation().getY(),false);
				utils.setMenuEntry(targetMenu);
				utils.delayMouseClick(targetObject.getConvexHull().getBounds(),sleepDelay());
			}
		}
	}

	@Subscribe
	private void onItemContainerChanged(ItemContainerChanged event){
		if (event.getContainerId() != 93 || !startTeaks)
		{
			return;
		} else {
			if(oldInventCount==-1){
				event.getItemContainer().count(6333);
			}
			if(event.getItemContainer().count(6333)>oldInventCount){
				teaksCut++;
			}
			oldInventCount=event.getItemContainer().count(6333);
		}
	}
}
