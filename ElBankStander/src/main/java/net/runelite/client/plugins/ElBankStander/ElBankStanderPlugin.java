package net.runelite.client.plugins.ElBankStander;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ConfigButtonClicked;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.queries.GameObjectQuery;
import net.runelite.api.queries.NPCQuery;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.plugins.PluginType;
import net.runelite.client.plugins.botutils.BotUtils;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.http.api.ge.GrandExchangeClient;
import net.runelite.http.api.osbuddy.OSBGrandExchangeClient;
import okhttp3.OkHttpClient;
import org.pf4j.Extension;

import static net.runelite.client.plugins.ElBankStander.ElBankStanderState.*;
import static net.runelite.client.plugins.botutils.Banks.BANK_SET;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.*;

@Extension
@PluginDependency(BotUtils.class)
@PluginDescriptor(
	name = "El Bank Stander",
	description = "Performs various bank standing activities",
	type = PluginType.MISCELLANEOUS
)
@Slf4j
public class ElBankStanderPlugin extends Plugin
{

	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private BotUtils utils;

	@Inject
	private ConfigManager configManager;

	@Inject
	OverlayManager overlayManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ElBankStanderConfig config;

	@Inject
	private ElBankStanderOverlay overlay;

	ElBankStanderState state;
	MenuEntry targetMenu;
	Instant botTimer;
	Player player;
	boolean firstTime;

	int timeout = 0;
	long sleepLength;
	boolean startBankStander;
	private final Set<Integer> requiredIds = new HashSet<>();
	Rectangle clickBounds;

	private NavigationButton navButton;

	// Provides our config
	@Provides
	ElBankStanderConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ElBankStanderConfig.class);
	}

	@Override
	protected void startUp()
	{
		// runs on plugin startup
		log.info("Plugin started");
		/*final ElBankStanderPanel panel = injector.getInstance(ElBankStanderPanel.class);

		final BufferedImage icon = ImageUtil.getResourceStreamFromClass(getClass(), "el.png");

		navButton = NavigationButton.builder()
				.tooltip("El Bank Stander")
				.icon(icon)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);*/

		// example how to use config items
	}

	@Override
	protected void shutDown()
	{
		// runs on plugin shutdown
		log.info("Plugin stopped");
		//clientToolbar.removeNavigation(navButton);
		resetVals();
	}

	private void resetVals()
	{
		overlayManager.remove(overlay);
		state = null;
		timeout = 0;
		botTimer = null;
		startBankStander = false;
		firstTime=true;
		requiredIds.clear();
	}

	@Subscribe
	private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked)
	{
		if (!configButtonClicked.getGroup().equalsIgnoreCase("ElBankStander"))
		{
			return;
		}
		log.info("button {} pressed!", configButtonClicked.getKey());
		if (configButtonClicked.getKey().equals("startButton"))
		{
			if (!startBankStander)
			{
				startBankStander = true;
				state = null;
				targetMenu = null;
				botTimer = Instant.now();
				overlayManager.add(overlay);
				requiredIds.clear();
				switch (config.type()){
					case USE_ITEM:
						requiredIds.add(config.firstId());
						break;
					case USE_ITEM_ON_ITEM:
						requiredIds.add(config.firstId());
						requiredIds.add(config.secondId());
						break;
					case USE_TOOL_ON_ITEM:
						requiredIds.add(config.firstId());
						requiredIds.add(config.toolId());
						break;
				}
				if(config.placeholder1Id()!=0){
					requiredIds.add(config.placeholder1Id());
				}
				if(config.placeholder2Id()!=0){
					requiredIds.add(config.placeholder2Id());
				}
				if(config.placeholder3Id()!=0){
					requiredIds.add(config.placeholder3Id());
				}
			}
			else
			{
				resetVals();
			}
		}
	}

	private long sleepDelay()
	{
		sleepLength = utils.randomDelay(false, config.sleepMin(),config.sleepMax(),config.sleepDeviation(),config.sleepTarget());
		return sleepLength;
	}

	private int tickDelay()
	{
		int tickLength = (int) utils.randomDelay(false,config.tickMin(),config.tickMax(),config.tickDeviation(),config.tickTarget());
		log.debug("tick delay for {} ticks", tickLength);
		return tickLength;
	}

	private ElBankStanderState getBankState()
	{
		if (!utils.isBankOpen() && !utils.isDepositBoxOpen())
		{
			return FIND_BANK;
		}
		if(config.type() == ElBankStanderType.USE_ITEM){
			if(utils.inventoryContains(config.firstId())){
				return CLOSE_BANK;
			} else if (!utils.inventoryEmpty()){
				return DEPOSIT_ALL;
			}
			if(!utils.inventoryFull()){
				return WITHDRAW_ITEMS;
			}
		}
		if(config.type() == ElBankStanderType.USE_ITEM_ON_ITEM){
			if(utils.inventoryContains(config.firstId()) && utils.inventoryContains(config.secondId())){
				return CLOSE_BANK;
			} else if(!utils.inventoryEmpty()){
				return DEPOSIT_ALL;
			}
			if(!utils.inventoryFull())
			{
				return WITHDRAW_ITEMS;
			}
		}
		if(config.type() == ElBankStanderType.USE_TOOL_ON_ITEM){
			if(utils.inventoryContains(config.toolId()) && utils.inventoryContains(config.firstId())){
				return CLOSE_BANK;
			} else if(!utils.inventoryEmpty()){
				return DEPOSIT_ALL;
			}
			if(!utils.inventoryFull())
			{
				return WITHDRAW_ITEMS;
			}
		}
		return BANK_NOT_FOUND;
	}

	public ElBankStanderState getState()
	{
		if (utils.iterating)
		{
			return ITERATING;
		}
		if(player.getAnimation()!=-1){
			return ANIMATING;
		}
		if (timeout > 0)
		{
			return TIMEOUT;
		}
		if(config.type() == ElBankStanderType.USE_ITEM_ON_ITEM){
			if(utils.inventoryContains(config.firstId()) && utils.inventoryContains(config.secondId())){
				if(client.getWidget(270,0)!=null){
					return USING_MENU;
				} else {
					return utils.isBankOpen() ? CLOSE_BANK : USING_ITEM_ON_ITEM;
				}
			}
			else {
				return utils.isBankOpen() ? WITHDRAW_ITEMS : FIND_BANK;
			}
		}
		if(config.type() == ElBankStanderType.USE_ITEM){
			if(utils.inventoryContains(config.firstId())) {
				return utils.isBankOpen() ? CLOSE_BANK : USING_ITEM;
			} else {
				return utils.isBankOpen() ? WITHDRAW_ITEMS : FIND_BANK;
			}
		}
		if(config.type() == ElBankStanderType.USE_TOOL_ON_ITEM){
			if(utils.inventoryContains(config.toolId())){ //contains tool
				if(utils.inventoryContains(config.firstId())){ //contains firstid
					if(client.getWidget(270,0)!=null){
						return USING_MENU;
					} else {
						return utils.isBankOpen() ? CLOSE_BANK : USING_TOOL_ON_ITEM;
					}
				} else if(utils.getInventorySpace()==29-requiredIds.size()) {
					return utils.isBankOpen() ? WITHDRAW_ITEMS : FIND_BANK;
				} else {
					return utils.isBankOpen() ? DEPOSIT_EXCEPT : FIND_BANK;
				}
			} else {
				return utils.isBankOpen() ? WITHDRAW_ITEMS : FIND_BANK;
			}
		}
		return ANIMATING;
	}

	@Subscribe
	private void onGameTick(GameTick gameTick)
	{
		if (!startBankStander)
		{
			return;
		}
		player = client.getLocalPlayer();
		if (client != null && player != null)
		{
			if (!client.isResized())
			{
				utils.sendGameMessage("elli-tt - client must be set to resizable");
				startBankStander = false;
				return;
			}
			state = getState();
			switch (state)
			{
				case TIMEOUT:
					timeout--;
					break;
				case ITERATING:
					timeout = tickDelay();
					break;
				case FIND_BANK:
					openNearestBank();
					timeout = tickDelay();
					break;
				case DEPOSIT_ALL:
					utils.depositAll();
					timeout = tickDelay();
					break;
				case DEPOSIT_EXCEPT:
					utils.depositAllExcept(requiredIds);
					timeout = tickDelay();
					break;
				case MISSING_ITEMS:
					startBankStander = false;
					utils.sendGameMessage("Missing required items IDs: " + String.valueOf(config.toolId()) + " from inventory. Stopping.");
					resetVals();
					break;
				case USING_ITEM:
					useItem();
					timeout = tickDelay();
					break;
				case USING_ITEM_ON_ITEM:
					useItemOnItem();
					timeout = tickDelay();
					break;
				case USING_TOOL_ON_ITEM:
					useToolOnItem();
					timeout = tickDelay();
				case ANIMATING:
					timeout=1+tickDelay();
					break;
				case WITHDRAW_ITEMS:
					handleWithdraw();
					timeout += 1+tickDelay();
					break;
				case USING_MENU:
					handleMenu();
					timeout = tickDelay();
					break;
				case CLOSE_BANK:
					utils.closeBank();
					break;
			}
		}
	}

	@Subscribe
	private void onMenuOptionClicked(MenuOptionClicked e)
	{
		log.debug(e.toString());
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN && startBankStander)
		{
			state = TIMEOUT;
			timeout = 2;
		}
	}

	private void openNearestBank()
	{
		if(config.grandExchange()){
			NPC targetNPC = new NPCQuery()
					.idEquals(1634,3089,1633,1613)
					.result(client)
					.nearestTo(client.getLocalPlayer());
			if(targetNPC!=null){
				targetMenu=new MenuEntry("","",targetNPC.getIndex(),11,0,0,false);
				utils.setMenuEntry(targetMenu);
				clickBounds = targetNPC.getConvexHull().getBounds()!=null ? targetNPC.getConvexHull().getBounds() : new Rectangle(client.getCenterX() - 50, client.getCenterY() - 50, 100, 100);
				utils.delayMouseClick(clickBounds,sleepDelay());
			} else {
				utils.sendGameMessage("G.E Banker is null.");
			}
		} else {
			GameObject targetObject = new GameObjectQuery()
					.idEquals(BANK_SET)
					.result(client)
					.nearestTo(client.getLocalPlayer());
			if(targetObject!=null) {
				targetMenu = new MenuEntry("", "", targetObject.getId(), 4, targetObject.getLocalLocation().getSceneX(), targetObject.getLocalLocation().getSceneY(), false);
				utils.setMenuEntry(targetMenu);
				clickBounds = targetObject.getClickbox().getBounds() != null ? targetObject.getClickbox().getBounds() : new Rectangle(client.getCenterX() - 50, client.getCenterY() - 50, 100, 100);
				utils.delayMouseClick(clickBounds, sleepDelay());
			} else {
				utils.sendGameMessage("Bank Booth is null.");
			}
		}
	}

	private void handleAll()
	{

	}

	private void handleWithdraw()
	{
		switch (config.type()) {
			case USE_ITEM:
				if(utils.inventoryEmpty()){
					utils.withdrawAllItem(utils.getBankItemWidget(config.firstId()));
				} else if(!utils.inventoryContains(config.firstId())){
					utils.depositAll();
				}
				break;
			case USE_TOOL_ON_ITEM:
				if(!utils.inventoryContains(config.toolId())){
					utils.withdrawItemAmount(config.toolId(),1);
				} else {
					utils.withdrawAllItem(utils.getBankItemWidget(config.firstId()));
				}
				break;
			case USE_ITEM_ON_ITEM:
				if(utils.inventoryContainsExcept(requiredIds)){
					utils.depositAll();
				} else if(!utils.inventoryContains(config.firstId())){
					//utils.withdrawItemAmount(config.firstId(),14);
					withdrawX(config.firstId());
				} else {
					//utils.withdrawItemAmount(config.secondId(),14);
					withdrawX(config.secondId());
				}
				break;
		}
	}

	private void useItem(){
		utils.inventoryItemsInteract(Collections.singleton(config.firstId()), config.inventoryOp(), false,true, 60, 350);
	}

	private void useItemOnItem(){
		targetMenu = new MenuEntry("","",config.secondId(),31,utils.getInventoryWidgetItem(config.secondId()).getIndex(),9764864,false);
		utils.setModifiedMenuEntry(targetMenu,config.firstId(),utils.getInventoryWidgetItem(config.firstId()).getIndex(),31);
		clickBounds = utils.getInventoryWidgetItem(config.secondId()).getCanvasBounds()!=null ? utils.getInventoryWidgetItem(config.secondId()).getCanvasBounds() : new Rectangle(client.getCenterX() - 50, client.getCenterY() - 50, 100, 100);
		utils.delayMouseClick(clickBounds,sleepDelay());
	}

	private void useToolOnItem(){
		targetMenu = new MenuEntry("","",config.firstId(),31,utils.getInventoryWidgetItem(config.firstId()).getIndex(),9764864,false);
		utils.setModifiedMenuEntry(targetMenu,config.toolId(),utils.getInventoryWidgetItem(config.toolId()).getIndex(),31);
		clickBounds = utils.getInventoryWidgetItem(config.firstId()).getCanvasBounds()!=null ? utils.getInventoryWidgetItem(config.firstId()).getCanvasBounds() : new Rectangle(client.getCenterX() - 50, client.getCenterY() - 50, 100, 100);
		utils.delayMouseClick(clickBounds,sleepDelay());
	}

	private void handleMenu(){
		targetMenu = new MenuEntry("","",1,config.menuOp(),-1,config.menuParam1(),false);
		utils.setMenuEntry(targetMenu);
		clickBounds = client.getWidget(270,0).getBounds()!=null ? client.getWidget(270,0).getBounds() : new Rectangle(client.getCenterX() - 50, client.getCenterY() - 50, 100, 100);
		utils.delayMouseClick(clickBounds,sleepDelay());
	}

	private void withdrawX(int ID){
		if(client.getVarbitValue(3960)!=14){
			utils.withdrawItemAmount(ID,14);
			timeout+=3;
		} else {
			targetMenu = new MenuEntry("", "", (client.getVarbitValue(6590) == 3) ? 1 : 5, MenuOpcode.CC_OP.getId(), utils.getBankItemWidget(ID).getIndex(), 786444, false);
			utils.setMenuEntry(targetMenu);
			clickBounds = utils.getBankItemWidget(ID).getBounds()!=null ? utils.getBankItemWidget(ID).getBounds() : new Rectangle(client.getCenterX() - 50, client.getCenterY() - 50, 100, 100);
			utils.delayMouseClick(clickBounds,sleepDelay());
		}
	}
}
