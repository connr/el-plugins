/*
 * Copyright (c) 2017, Kronos <https://github.com/KronosDesign>
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
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

import java.awt.GridLayout;
import java.awt.TrayIcon;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.JButton;
import javax.swing.JPanel;
import net.runelite.api.Client;
import net.runelite.api.MenuOpcode;
import net.runelite.client.Notifier;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.infobox.Counter;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.ImageUtil;

class ElBankStanderPanel extends PluginPanel
{
	private final Client client;
	private final Notifier notifier;
	private final ElBankStanderPlugin plugin;

	private final InfoBoxManager infoBoxManager;
	private final ScheduledExecutorService scheduledExecutorService;

	@Inject
	private ElBankStanderPanel(
			Client client,
			ElBankStanderPlugin plugin,
			Notifier notifier,
			InfoBoxManager infoBoxManager,
			ScheduledExecutorService scheduledExecutorService)
	{
		super();
		this.client = client;
		this.plugin = plugin;
		this.notifier = notifier;
		this.infoBoxManager = infoBoxManager;
		this.scheduledExecutorService = scheduledExecutorService;

		setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(createOptionsPanel());
	}

	private JPanel createOptionsPanel()
	{
		final JPanel container = new JPanel();
		container.setBackground(ColorScheme.DARK_GRAY_COLOR);
		container.setLayout(new GridLayout(0, 2, 3, 3));

		final JButton notificationBtn = new JButton("Notification");
		notificationBtn.addActionListener(e ->
		{
			scheduledExecutorService.schedule(() -> notifier.notify("Wow!", TrayIcon.MessageType.ERROR), 3, TimeUnit.SECONDS);
		});
		container.add(notificationBtn);

		final JButton newInfoboxBtn = new JButton("Infobox");
		newInfoboxBtn.addActionListener(e ->
		{
			Counter counter = new Counter(ImageUtil.getResourceStreamFromClass(getClass(), "el.png"), plugin, 42);
			counter.getMenuEntries().add(new OverlayMenuEntry(MenuOpcode.RUNELITE_INFOBOX, "Test", "ElBankStander"));
			infoBoxManager.addInfoBox(counter);
		});
		container.add(newInfoboxBtn);

		final JButton clearInfoboxBtn = new JButton("Clear Infobox");
		clearInfoboxBtn.addActionListener(e -> infoBoxManager.removeIf(i -> true));
		container.add(clearInfoboxBtn);

		return container;
	}
}