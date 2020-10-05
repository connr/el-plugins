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
package net.runelite.client.plugins.ElBankStander;

import net.runelite.client.config.*;

@ConfigGroup("ElBankStanderConfig")

public interface ElBankStanderConfig extends Config
{
	@ConfigTitleSection(
			keyName = "instructionsTitle",
			name = "Instructions",
			description = "",
			position = 0
	)
	default Title instructionsTitle()
	{
		return new Title();
	}
	@ConfigItem(
			keyName = "instructions",
			name = "",
			description = "Instructions.",
			position = 1,
			titleSection = "instructionsTitle"
	)
	default String instructions()
	{
		return "Please select what activity you would like to do below. "+
				"Then enter the item IDs you would like to use for this activity.";
	}

	@ConfigTitleSection(
			keyName = "generalTitle",
			name = "General Config",
			description = "",
			position = 10
	)
	default Title generalTitle()
	{
		return new Title();
	}

	@ConfigItem(
			keyName = "type",
			name = "Type",
			description = "Select what activity you would like to do.",
			position = 11,
			titleSection = "generalTitle"
	)
	default ElBankStanderType type()
	{
		return ElBankStanderType.USE_ITEM;
	}

	@ConfigItem(
			keyName = "firstId",
			name = "First Item ID",
			description = "Enter the Id of the first item you will use.",
			position = 12,
			titleSection = "generalTitle"
	)
	default int firstId() { return 0; }

	@ConfigItem(
			keyName = "secondId",
			name = "Second Item ID",
			description = "Enter the Id of the second item you will use.",
			position = 13,
			hidden = true,
			unhide = "type",
			unhideValue = "USE_ITEM_ON_ITEM",
			titleSection = "generalTitle"
	)
	default int secondId()
	{
		return 0;
	}

	@ConfigItem(
			keyName = "toolId",
			name = "Tool ID",
			description = "Enter the Id of the tool you will be using.",
			position = 14,
			hidden = true,
			unhide = "type",
			unhideValue = "USE_TOOL_ON_ITEM",
			titleSection = "generalTitle"
	)
	default int toolId()
	{
		return 0;
	}

	@ConfigTitleSection(
			keyName = "menuTitle",
			name = "Menu Config",
			description = "",
			position = 20
	)
	default Title menuTitle()
	{
		return new Title();
	}

	@ConfigItem(
			keyName = "menuOp",
			name = "Menu OpCode",
			description = "Enter the menu opcode here.",
			position = 21,
			titleSection = "menuTitle"
	)
	default int menuOp()
	{
		return 0;
	}

	@ConfigItem(
			keyName = "menuParam1",
			name = "Menu Param1",
			description = "Enter the menu param1 here.",
			position = 22,
			titleSection = "menuTitle"
	)
	default int menuParam1()
	{
		return 0;
	}

	@ConfigItem(
			keyName = "startButton",
			name = "Start/Stop",
			description = "Test button that changes variable value",
			position = 100
	)
	default Button startButton()
	{
		return new Button();
	}
}