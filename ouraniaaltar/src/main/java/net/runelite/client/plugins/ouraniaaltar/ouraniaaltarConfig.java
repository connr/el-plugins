/*
 * Copyright (c) 2018, Andrew EP | ElPinche256 <https://github.com/ElPinche256>
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
package net.runelite.client.plugins.ouraniaaltar;

import net.runelite.client.config.Button;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("ouraniaaltarConfig")

public interface ouraniaaltarConfig extends Config
{
	@ConfigItem(
			keyName = "instructions",
			name = "",
			description = "Instructions. Don't enter anything into this field",
			position = 0,
			titleSection = "instructionsTitle"
	)
	default String instructions()
	{
		return "Make sure you have Stamina (1)s, food, Essence, Air Runes and Cosmic Runes in your bank. " +
				"Equip a staff that gives earth runes and make sure you have ourania teleport and banking runes in your pouch.";
	}

	@ConfigItem(
			keyName = "giantPouch",
			name = "Use Giant Pouch",
			description = "Use giant pouch",
			position = 1
	)
	default boolean giantPouch() { return false; }

	@ConfigItem(
			keyName = "daeyalt",
			name = "Use Daeyalt Essence",
			description = "Use daeyalt essence",
			position = 2
	)
	default boolean daeyalt() { return false; }

	@ConfigItem(
			keyName = "dropRunes",
			name = "Drop Runes",
			description = "Drop runes at altar",
			position = 3
	)
	default boolean dropRunes() { return false; }

	@ConfigItem(
			keyName = "dropRunesString",
			name = "Runes To Drop",
			description = "Runes you would like to drop.",
			position = 4,
			hidden = true,
			unhide = "dropRunes"
	)
	default String dropRunesString() { return "554,555,556,557,558,559"; }

	@ConfigItem(
			keyName = "noStams",
			name = "No Staminas",
			description = "Tick this if you don't have any stamina potions.",
			position = 5
	)
	default boolean noStams() { return false; }

	@ConfigItem(
			keyName = "minEnergy",
			name = "Minimum Energy",
			description = "Minimum energy before stam pot drank",
			position = 13,
			hidden = false,
			hide = "noStams"
	)
	default int minEnergy() { return 35; }

	@ConfigItem(
			keyName = "instructions2",
			name = "",
			description = "Instructions. Don't enter anything into this field",
			position = 14
	)
	default String instructions2()
	{
		return "Common food IDs: " +
				"Karambwan: 3144, Shark: 385, Monkfish: 7946.";
	}

	@ConfigItem(
			keyName = "foodType",
			name = "Food ID",
			description = "ID of food to eat",
			position = 15
	)
	default int foodId() { return 7946; }


	@ConfigItem(
			keyName = "minHealth",
			name = "Minimum Health",
			description = "Minimum health before food eaten",
			position = 16
	)
	default int minHealth() { return 65; }

	@ConfigItem(
			keyName = "pauseHealth",
			name = "Pause Health",
			description = "Health below which the plugin will pause.",
			position = 17
	)
	default int pauseHealth() { return 40; }

	@ConfigItem(
			keyName = "enableUI",
			name = "Enable UI",
			description = "Enable to turn on in game UI",
			position = 140
	)
	default boolean enableUI()
	{
		return true;
	}

	@ConfigItem(
			keyName = "startButton",
			name = "Start/Stop",
			description = "Test button that changes variable value",
			position = 150
	)
	default Button startButton()
	{
		return new Button();
	}
}