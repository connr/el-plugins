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
package net.runelite.client.plugins.ElBloods;

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
import static net.runelite.client.plugins.ElBloods.ElBloodsState.*;


@Extension
@PluginDependency(BotUtils.class)
@PluginDescriptor(
	name = "El Bloods",
	enabledByDefault = false,
	description = "Crafts blood runes",
	tags = {"craft, blood, runecraft, el"},
	type = PluginType.SKILLING
)
@Slf4j
public class ElBloodsPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ElBloodsConfiguration config;

	@Inject
	private BotUtils utils;

	@Inject
	private ConfigManager configManager;

	@Inject
	PluginManager pluginManager;

	@Inject
	OverlayManager overlayManager;

	@Inject
	private ElBloodsOverlay overlay;

	@Inject
	private ChinBreakHandler chinBreakHandler;


	ElBloodsState state;
	GameObject targetObject;
	GroundObject targetGroundObject;
	MenuEntry targetMenu;
	WorldPoint skillLocation;
	Instant botTimer;
	LocalPoint beforeLoc;
	Player player;
	Rectangle altRect = new Rectangle(-100,-100, 10, 10);
	WorldArea DENSE_ESSENCE_AREA = new WorldArea(new WorldPoint(1751, 3845, 0), new WorldPoint(1770, 3862, 0));
	WorldArea DARK_ALTAR_AREA = new WorldArea(new WorldPoint(1717, 3881, 0), new WorldPoint(1720, 3884, 0));
	WorldArea FIRST_CLICK_BLOOD_ALTAR_AREA = new WorldArea(new WorldPoint(1719, 3854, 0), new WorldPoint(1729, 3860, 0));
	WorldArea OBSTACLE_AFTER_CHISELING_AREA = new WorldArea(new WorldPoint(1745, 3871, 0), new WorldPoint(1752, 3881, 0));
	WorldArea START_CHISELING_AREA = new WorldArea(new WorldPoint(1720, 3874, 0), new WorldPoint(1734, 3881, 0));


	WorldArea FIRST_CLICK_SOUL_ALTAR_AREA = new WorldArea(new WorldPoint(1763,3894,0),new WorldPoint(1768,3899,0));
	WorldPoint FIRST_CLICK_SOUL_ALTAR_POINT = new WorldPoint(1763,3896,0);

	WorldArea SECOND_CLICK_SOUL_ALTAR_AREA = new WorldArea(new WorldPoint(1801,3890,0),new WorldPoint(1806,3895,0));
	WorldPoint SECOND_CLICK_SOUL_ALTAR_POINT = new WorldPoint(1803,3892,0);

	WorldPoint SOUL_ALTAR_POINT = new WorldPoint(1816,3856,0);

	int timeout = 0;
	long sleepLength;
	boolean startBloodRunecrafter;
	boolean firstTimeUsingChisel;
	private final Set<Integer> objectIds = new HashSet<>();
	private final int requiredIds = 1755;
	int startBloodRunes;
	int currentBloodRunes;


	@Provides
	ElBloodsConfiguration provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ElBloodsConfiguration.class);
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
		startBloodRunecrafter = false;
		firstTimeUsingChisel = true;
		objectIds.clear();
	}

	@Subscribe
	private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked)
	{
		if (!configButtonClicked.getGroup().equalsIgnoreCase("ElBloods"))
		{
			return;
		}
		log.info("button {} pressed!", configButtonClicked.getKey());
		if (configButtonClicked.getKey().equals("startButton"))
		{
			if (!startBloodRunecrafter)
			{
				startBloodRunecrafter = true;
				chinBreakHandler.startPlugin(this);
				state = null;
				targetMenu = null;
				botTimer = Instant.now();
				setLocation();
				overlayManager.add(overlay);
				if(utils.inventoryContains(565)){
					startBloodRunes=utils.getInventoryItemCount(565,true);
					currentBloodRunes=startBloodRunes;
				} else {
					startBloodRunes=0;
					currentBloodRunes=0;
				}

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
		if (!event.getGroup().equals("ElBloods"))
		{
			return;
		}
		startBloodRunecrafter = false;
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

	private void interactObject()
	{
		targetObject = getDenseEssence();
		if (targetObject != null)
		{
			targetMenu = new MenuEntry("", "", targetObject.getId(), 3,
					targetObject.getSceneMinLocation().getX(), targetObject.getSceneMinLocation().getY(), false);
			utils.setMenuEntry(targetMenu);
			if(targetObject.getConvexHull()!=null){
				utils.delayMouseClick(targetObject.getConvexHull().getBounds(), sleepDelay());
			} else {
				log.info("convex hull is null");
				utils.delayMouseClick(new Point(0,0), sleepDelay());
			}

		}
		else
		{
			log.info("Game Object is null, ids are: {}", objectIds.toString());
		}
	}

	private GameObject getDenseEssence()
	{
		assert client.isClientThread();

		if (client.getVarbitValue(4927) == 0)
		{
			return utils.findNearestGameObject(NullObjectID.NULL_8981);
		}
		if (client.getVarbitValue(4928) == 0)
		{
			return utils.findNearestGameObject(NullObjectID.NULL_10796);
		}
		return null;
	}

    private void handleDropExcept()
    {
        switch (config.mode()) {
			case BLOOD_RUNES:
			case SOUL_RUNES:
				utils.inventoryItemsCombine(Collections.singleton(13446), 1755,38, false,true, config.sleepMin(), config.sleepMax());
		}
    }

	public ElBloodsState getState()
	{
		if (timeout > 0)
		{
			return TIMEOUT;
		}
		if (utils.iterating)
		{
			return ITERATING;
		}
		if (!utils.inventoryContains(requiredIds))
		{
			return MISSING_ITEMS;
		}
		if (utils.isMoving(beforeLoc))
		{
			timeout = 2 + tickDelay();
			return MOVING;
		}
		if (chinBreakHandler.shouldBreak(this) && player.getWorldArea().intersectsWith(DENSE_ESSENCE_AREA))
		{
			return HANDLE_BREAK;
		}
		if (utils.inventoryFull())
		{
			switch (config.mode()){
				case BLOOD_RUNES:
					return getBloodRunecraftState();
				case SOUL_RUNES:
					return getSoulRunecraftState();
				default:
					return WAIT_DENSE_ESSENCE;
			}
		}
		if (client.getLocalPlayer().getAnimation() == -1)
		{
			switch (config.mode()){
				case BLOOD_RUNES:
					return (DENSE_ESSENCE_AREA.distanceTo(client.getLocalPlayer().getWorldLocation()) == 0) ?
							FIND_GAME_OBJECT : getBloodRunecraftState();
				case SOUL_RUNES:
					return (DENSE_ESSENCE_AREA.distanceTo(client.getLocalPlayer().getWorldLocation()) == 0) ?
							FIND_GAME_OBJECT : getSoulRunecraftState();
				default:
					return (DENSE_ESSENCE_AREA.distanceTo(client.getLocalPlayer().getWorldLocation()) == 0) ?
							FIND_GAME_OBJECT : WAIT_DENSE_ESSENCE;
			}
		}
		return ANIMATING;
	}

	@Subscribe
	private void onGameTick(GameTick tick)
	{
		if (!startBloodRunecrafter || chinBreakHandler.isBreakActive(this))
		{
			return;
		}
		player = client.getLocalPlayer();
		if (client != null && player != null && skillLocation != null)
		{
			if (!client.isResized())
			{
				utils.sendGameMessage("client must be set to resizable");
				startBloodRunecrafter = false;
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
				case DROP_EXCEPT:
					handleDropExcept();
					timeout = tickDelay();
					break;
				case FIND_GAME_OBJECT:
					interactObject();
					timeout = tickDelay();
					break;
				case MISSING_ITEMS:
					startBloodRunecrafter = false;
					utils.sendGameMessage("Missing required items IDs: " + requiredIds + " from inventory. Stopping.");
					resetVals();
					break;
				case HANDLE_BREAK:
					chinBreakHandler.startBreak(this);
					timeout = 10;
					break;
				case ANIMATING:
				case MOVING:
					switch (config.mode()) {
						case BLOOD_RUNES:
							bloodRunecraftFunction();
							break;
						case SOUL_RUNES:
							soulRunecraftFunction();
							break;
						default:
							utils.handleRun(30, 20);
							break;
					}
					timeout = tickDelay();
					break;
				case WAIT_DENSE_ESSENCE:
					if(player.getWorldLocation().equals(new WorldPoint(1761,3872,0))){
						interactObject();
					} else {
						return;
					}
				default:
					switch (config.mode()){
						case BLOOD_RUNES:
							bloodRunecraftFunction();
							timeout = tickDelay();
							break;
						case SOUL_RUNES:
							soulRunecraftFunction();
							timeout = tickDelay();
							break;
					}
			}
		}
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN && startBloodRunecrafter)
		{
			state = TIMEOUT;
			timeout = 2;
		}
	}

	private void bloodRunecraftFunction()
	{
		targetMenu=null;
		utils.setMenuEntry(null);
		switch (state){
			case OBSTACLE_1:
				targetGroundObject = utils.findNearestGroundObject(34741);
				if(targetGroundObject!=null){
					targetMenu = new MenuEntry("Climb", "<col=ffff>Rocks", targetGroundObject.getId(), 3,
							targetGroundObject.getLocalLocation().getSceneX(), targetGroundObject.getLocalLocation().getSceneY(), false);
					utils.setMenuEntry(targetMenu);
					if(targetGroundObject.getConvexHull()!=null){
						utils.delayMouseClick(targetGroundObject.getConvexHull().getBounds(), sleepDelay());
					} else {
						utils.delayMouseClick(new Point(0,0), sleepDelay());
					}
				}
				break;
			case CLICK_DARK_ALTAR:
				targetObject = utils.findNearestGameObject(27979);
				if (targetObject != null)
				{
					targetMenu = new MenuEntry("Venerate", "<col=ffff>Dark Altar", targetObject.getId(), 3,
							targetObject.getSceneMinLocation().getX(), targetObject.getSceneMinLocation().getY(), false);
					utils.setMenuEntry(targetMenu);
					if(targetObject.getConvexHull()!=null){
						utils.delayMouseClick(targetObject.getConvexHull().getBounds(), sleepDelay());
					} else {
						utils.delayMouseClick(new Point(0,0), sleepDelay());
					}
				} else {
					utils.walk(new WorldPoint(1718,3883,0),2,sleepDelay());
				}
				break;
			case WALK_BLOOD_ALTAR:
				if(player.getWorldArea().intersectsWith(DARK_ALTAR_AREA)){
					utils.walk(new WorldPoint(1724,3857,0),3,sleepDelay());
					break;
				} else if(player.getWorldArea().intersectsWith(FIRST_CLICK_BLOOD_ALTAR_AREA)){
					targetObject = utils.findNearestGameObject(27978);
					if (targetObject != null)
					{
						targetMenu = new MenuEntry("Bind", "<col=ffff>Blood Altar", targetObject.getId(), 3,
								targetObject.getSceneMinLocation().getX(), targetObject.getSceneMinLocation().getY(), false);
						utils.setMenuEntry(targetMenu);
						if(targetObject.getConvexHull()!=null){
							utils.delayMouseClick(targetObject.getConvexHull().getBounds(), sleepDelay());
						} else {
							utils.delayMouseClick(new Point(0,0), sleepDelay());
						}
					}
				}
				break;
			case CHISEL_AT_ALTAR:
			case CHISEL_WHILE_RUNNING:
				handleDropExcept();
				break;
			case CLICK_BLOOD_ALTAR:
				targetObject = utils.findNearestGameObject(27978);
				if (targetObject != null)
				{
					targetMenu = new MenuEntry("Bind", "<col=ffff>Blood Altar", targetObject.getId(), 3,
							targetObject.getSceneMinLocation().getX(), targetObject.getSceneMinLocation().getY(), false);
					utils.setMenuEntry(targetMenu);
					if(targetObject.getConvexHull()!=null){
						utils.delayMouseClick(targetObject.getConvexHull().getBounds(), sleepDelay());
					} else {
						utils.delayMouseClick(new Point(0,0), sleepDelay());
					}

				}
				break;
			case BLOOD_OBSTACLE_2:
				currentBloodRunes=utils.getInventoryItemCount(565,true);
				targetGroundObject = utils.findNearestGroundObject(27984);
				if(targetGroundObject!=null){
					targetMenu = new MenuEntry("Climb", "<col=ffff>Rocks", targetGroundObject.getId(), 3,
							targetGroundObject.getLocalLocation().getSceneX(), targetGroundObject.getLocalLocation().getSceneY(), false);
					utils.setMenuEntry(targetMenu);
					utils.delayMouseClick(targetGroundObject.getConvexHull().getBounds(), sleepDelay());
				}
				break;
			case WALK_TO_ESSENCE:
				if(utils.inventoryContains(13446)){
					handleDropExcept();
				} else {
					interactObject();
				}
				break;
			case MOVING:
				if (player.getWorldArea().intersectsWith(START_CHISELING_AREA)){ //running back from dark altar
					if(utils.inventoryContains(13446)){
						state = CHISEL_WHILE_RUNNING;
						bloodRunecraftFunction();
						break;
					}
				} else if (player.getWorldArea().intersectsWith(OBSTACLE_AFTER_CHISELING_AREA)){
					if(utils.inventoryContains(7938) && utils.getInventorySpace()>20){
						state = OBSTACLE_1;
						bloodRunecraftFunction();
						break;
					}
				} else if (player.getWorldArea().intersectsWith(FIRST_CLICK_BLOOD_ALTAR_AREA)){
					state = CLICK_BLOOD_ALTAR;
					bloodRunecraftFunction();
					break;
				}
				utils.handleRun(30, 20);
				break;
		}
	}

	private void soulRunecraftFunction()
	{
		switch (state){
			case OBSTACLE_1:
				targetGroundObject = utils.findNearestGroundObject(34741);
				if(targetGroundObject!=null){
					targetMenu = new MenuEntry("Climb", "<col=ffff>Rocks", targetGroundObject.getId(), 3,
							targetGroundObject.getLocalLocation().getSceneX(), targetGroundObject.getLocalLocation().getSceneY(), false);
					utils.setMenuEntry(targetMenu);
					if(targetGroundObject.getConvexHull()!=null){
						utils.delayMouseClick(targetGroundObject.getConvexHull().getBounds(), sleepDelay());
					} else {
						utils.delayMouseClick(new Point(0,0), sleepDelay());
					}
				}
				break;
			case CLICK_DARK_ALTAR:
				targetObject = utils.findNearestGameObject(27979);
				if (targetObject != null)
				{
					targetMenu = new MenuEntry("Venerate", "<col=ffff>Dark Altar", targetObject.getId(), 3,
							targetObject.getSceneMinLocation().getX(), targetObject.getSceneMinLocation().getY(), false);
					utils.setMenuEntry(targetMenu);
					if(targetObject.getConvexHull()!=null){
						utils.delayMouseClick(targetObject.getConvexHull().getBounds(), sleepDelay());
					} else {
						utils.delayMouseClick(new Point(0,0), sleepDelay());
					}
				} else {
					utils.walk(new WorldPoint(1718,3883,0),2,sleepDelay());
				}
				break;
			case WALK_SOUL_ALTAR_1:
				if(player.getWorldArea().intersectsWith(DARK_ALTAR_AREA)){
					utils.walk(FIRST_CLICK_SOUL_ALTAR_POINT,1,sleepDelay());
					break;
				} else if(player.getWorldArea().intersectsWith(FIRST_CLICK_BLOOD_ALTAR_AREA)){
					targetObject = utils.findNearestGameObject(27978);
					if (targetObject != null)
					{
						targetMenu = new MenuEntry("Bind", "<col=ffff>Blood Altar", targetObject.getId(), 3,
								targetObject.getSceneMinLocation().getX(), targetObject.getSceneMinLocation().getY(), false);
						utils.setMenuEntry(targetMenu);
						if(targetObject.getConvexHull()!=null){
							utils.delayMouseClick(targetObject.getConvexHull().getBounds(), sleepDelay());
						} else {
							utils.delayMouseClick(new Point(0,0), sleepDelay());
						}
					}
					break;
				} else if(player.getWorldArea().intersectsWith(SECOND_CLICK_SOUL_ALTAR_AREA)){
					targetObject = utils.findNearestGameObject(27990);
					if (targetObject != null)
					{
						targetMenu = new MenuEntry("Jump", "<col=ffff>Boulder", targetObject.getId(), 3,
								targetObject.getSceneMinLocation().getX(), targetObject.getSceneMinLocation().getY(), false);
						utils.setMenuEntry(targetMenu);
						if(targetObject.getConvexHull()!=null){
							utils.delayMouseClick(targetObject.getConvexHull().getBounds(), sleepDelay());
						} else {
							utils.delayMouseClick(new Point(0,0), sleepDelay());
						}
					}
					break;
				}
				break;
			case WALK_SOUL_ALTAR_2:
				utils.walk(SECOND_CLICK_SOUL_ALTAR_POINT,1,sleepDelay());
				break;
			case CHISEL_AT_ALTAR:
			case CHISEL_WHILE_RUNNING:
				handleDropExcept();
				break;
			case CLICK_SOUL_ALTAR:
				targetObject = utils.findNearestGameObject(27980);
				if (targetObject != null)
				{
					targetMenu = new MenuEntry("Bind", "<col=ffff>Soul Altar", targetObject.getId(), 3,
							targetObject.getSceneMinLocation().getX(), targetObject.getSceneMinLocation().getY(), false);
					utils.setMenuEntry(targetMenu);
					if(targetObject.getConvexHull()!=null){
						utils.delayMouseClick(targetObject.getConvexHull().getBounds(), sleepDelay());
					} else {
						utils.delayMouseClick(new Point(0,0), sleepDelay());
					}

				}
				break;
			case BLOOD_OBSTACLE_2:
				targetGroundObject = utils.findNearestGroundObject(27984);
				if(targetGroundObject!=null){
					targetMenu = new MenuEntry("Climb", "<col=ffff>Rocks", targetGroundObject.getId(), 3,
							targetGroundObject.getLocalLocation().getSceneX(), targetGroundObject.getLocalLocation().getSceneY(), false);
					utils.setMenuEntry(targetMenu);
					utils.delayMouseClick(targetGroundObject.getConvexHull().getBounds(), sleepDelay());
				}
				break;
			case WALK_TO_ESSENCE:
				if(utils.inventoryContains(13446)){
					handleDropExcept();
				} else {
					interactObject();
				}
				break;
			case MOVING:
				if (player.getWorldArea().intersectsWith(START_CHISELING_AREA)){ //running back from dark altar
					if(utils.inventoryContains(13446)){
						state = CHISEL_WHILE_RUNNING;
						bloodRunecraftFunction();
						break;
					}
				} else if (player.getWorldArea().intersectsWith(OBSTACLE_AFTER_CHISELING_AREA)){
					if(utils.inventoryContains(7938) && utils.getInventorySpace()>20){
						state = OBSTACLE_1;
						bloodRunecraftFunction();
						break;
					}
				} else if (player.getWorldArea().intersectsWith(SECOND_CLICK_SOUL_ALTAR_AREA)){
					state = CLICK_SOUL_ALTAR;
					bloodRunecraftFunction();
					break;
				}
				utils.handleRun(30, 20);
				break;
		}
	}

	private ElBloodsState getBloodRunecraftState()
	{
		if(utils.inventoryFull()){
			if(utils.inventoryContains(13445)) { //mined blocks
				if (player.getWorldArea().intersectsWith(DENSE_ESSENCE_AREA)) { //dense essence area
					return OBSTACLE_1;
				} else if (player.getWorldLocation().equals(new WorldPoint(1761,3874,0))){ //just after shortcut
					return CLICK_DARK_ALTAR;
				} else if (player.getWorldArea().intersectsWith(DARK_ALTAR_AREA)) {
					return CLICK_DARK_ALTAR;
				}
			}

			if(utils.inventoryContains(13446)){ //altered blocks
				if(player.getWorldLocation().equals(new WorldPoint(1718,3882,0))) { //dark altar
					if (!utils.inventoryContains(7938)) { //essence fragments
						return OBSTACLE_1;
					} else {
						return WALK_BLOOD_ALTAR;
					}
				} else if (player.getWorldLocation().equals(new WorldPoint(1719,3828,0))){ //blood altar
					return CHISEL_AT_ALTAR;
				} else if (player.getWorldArea().intersectsWith(FIRST_CLICK_BLOOD_ALTAR_AREA)){ //blood altar
					return CLICK_BLOOD_ALTAR;
				}
			}
		}
		if(client.getLocalPlayer().getAnimation()==-1){
			if(player.getWorldLocation().equals(new WorldPoint(1719,3828,0))) { //blood altar
				if(utils.inventoryContains(7938)){ //fragments
					return CLICK_BLOOD_ALTAR;
				} else if(utils.inventoryContains(13446)) {
					return CHISEL_AT_ALTAR;
				} else {
					return BLOOD_OBSTACLE_2;
				}
			} else if(player.getWorldLocation().equals(new WorldPoint(1761,3874,0))) {
				return OBSTACLE_1;
			} else if(player.getWorldLocation().equals(new WorldPoint(1761,3872,0))) {
				return WALK_TO_ESSENCE;
			}
		}
		return WAIT_DENSE_ESSENCE;
	}

	private ElBloodsState getSoulRunecraftState()
	{
		if(utils.inventoryFull()){
			if(utils.inventoryContains(13445)) { //mined blocks
				if (player.getWorldArea().intersectsWith(DENSE_ESSENCE_AREA)) { //dense essence area
					return OBSTACLE_1;
				} else if (player.getWorldLocation().equals(new WorldPoint(1761,3874,0))){ //just after shortcut
					return CLICK_DARK_ALTAR;
				} else if (player.getWorldArea().intersectsWith(DARK_ALTAR_AREA)) {
					return CLICK_DARK_ALTAR;
				}
			}

			if(utils.inventoryContains(13446)){ //altered blocks
				if(player.getWorldLocation().equals(new WorldPoint(1718,3882,0))) { //dark altar
					if (!utils.inventoryContains(7938)) { //essence fragments
						return OBSTACLE_1;
					} else {
						return WALK_SOUL_ALTAR_1;
					}
				} else if (player.getWorldLocation().equals(new WorldPoint(1719,3828,0))){ //blood altar
					return CHISEL_AT_ALTAR;
				} else if (player.getWorldArea().intersectsWith(FIRST_CLICK_SOUL_ALTAR_AREA)){ //blood altar
						return WALK_SOUL_ALTAR_2;
				} else if (player.getWorldArea().intersectsWith(SECOND_CLICK_SOUL_ALTAR_AREA)){ //blood altar
					return CLICK_SOUL_ALTAR;
				}
			}
		}
		if(client.getLocalPlayer().getAnimation()==-1){
			if(player.getWorldLocation().equals(SOUL_ALTAR_POINT)) { //soul altar
				if(utils.inventoryContains(7938)){ //fragments
					return CLICK_SOUL_ALTAR;
				} else if(utils.inventoryContains(13446)) { //shards
					return CHISEL_AT_ALTAR;
				} else {
					return WALK_SOUL_ALTAR_2;
				}
			} else if(player.getWorldLocation().equals(new WorldPoint(1761,3874,0))) {
				return OBSTACLE_1;
			} else if(player.getWorldLocation().equals(new WorldPoint(1761,3872,0))) {
				return WALK_TO_ESSENCE;
			} else if(player.getWorldLocation().equals(new WorldPoint(1776,3880,0))) {
				return OBSTACLE_1;
			} else if(player.getWorldArea().intersectsWith(SECOND_CLICK_SOUL_ALTAR_AREA)) {
				return WALK_SOUL_ALTAR_1;
			}
		}
		return WAIT_DENSE_ESSENCE;
	}
}
