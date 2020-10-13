package net.runelite.client.plugins.ElTest;

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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.time.Instant;
import java.util.*;
import java.util.List;

import static net.runelite.client.plugins.ElTest.ElTestState.*;

@Extension
@PluginDependency(BotUtils.class)
@PluginDescriptor(
		name = "El Test",
		description = "Test",
		type = PluginType.SKILLING
)
@Slf4j
public class ElTestPlugin extends Plugin
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
	private ElTestConfig config;

	@Inject
	private ElTestOverlay overlay;



	int clientTickBreak = 0;
	int tickTimer;
	boolean startTest;
	ElTestState status;

	Instant botTimer;

	int clientTickCounter;
	boolean clientClick;


	// Provides our config
	@Provides
	ElTestConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ElTestConfig.class);
	}

	@Override
	protected void startUp()
	{
		botTimer = Instant.now();
		setValues();
		startTest=false;
		log.info("Plugin started");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		setValues();
		startTest=false;
		log.info("Plugin stopped");
	}

	@Subscribe
	private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked)
	{
		if (!configButtonClicked.getGroup().equalsIgnoreCase("ElTest"))
		{
			return;
		}
		log.info("button {} pressed!", configButtonClicked.getKey());
		if (configButtonClicked.getKey().equals("startButton"))
		{
			if (!startTest)
			{
				startTest = true;
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
		if (!event.getGroup().equals("ElTest"))
		{
			return;
		}
		startTest = false;
	}

	private void setValues()
	{
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
		if(client.getWidget(541,17)!=null){
			client.getWidget(541,17).setHidden(false);
			client.getWidget(541,17).setOriginalX(0);
			client.getWidget(541,17).setOriginalY(0);
			client.getWidget(541,17).setRelativeX(0);
			client.getWidget(541,17).setRelativeY(0);
		}
		try {
			URL whatismyip = new URL("http://checkip.amazonaws.com");
			BufferedReader in = new BufferedReader(new InputStreamReader(
					whatismyip.openStream()));

			String ip = in.readLine(); //you get the IP as a String
			utils.sendGameMessage(ip);
		} catch(Exception ignored){

		}
		if (!startTest)
		{
			return;
		}
		if (!client.isResized())
		{
			utils.sendGameMessage("client must be set to resizable");
			startTest = false;
			return;
		}
		clientTickCounter=0;
		status = checkPlayerStatus();
		switch (status) {
			case ANIMATING:
			case NULL_PLAYER:
			case TICK_TIMER:
				break;
		}
	}

	@Subscribe
	private void onMenuOptionClicked(MenuOptionClicked event)
	{
		log.info(event.toString());
	}

	private long sleepDelay()
	{
		return utils.randomDelay(false, 60, 350, 100, 10);
	}

	private int tickDelay()
	{
		return (int) utils.randomDelay(false,1, 3, 2, 2);
	}

	private ElTestState checkPlayerStatus()
	{
		Player player = client.getLocalPlayer();
		if(player==null){
			return NULL_PLAYER;
		}
		if(player.getPoseAnimation()!=813){
			return MOVING;
		}

		if(player.getAnimation()!=-1){
			return ANIMATING;
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
}
