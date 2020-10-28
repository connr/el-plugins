/*
 * Copyright (c) 2020, dekvall <https://github.com/dekvall>
 * Copyright (c) 2020, Jordan <nightfirecat@protonmail.com>
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
package net.runelite.client.plugins.ElHerbiboar;

import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
enum ElHerbiboarRule
{
	A_SOUTH(ElHerbiboarSearchSpot.Group.A, ElHerbiboarStart.MIDDLE),
	C_WEST(ElHerbiboarSearchSpot.Group.C, ElHerbiboarStart.MIDDLE),
	D_WEST_1(ElHerbiboarSearchSpot.Group.D, ElHerbiboarStart.MIDDLE),
	D_WEST_2(ElHerbiboarSearchSpot.Group.D, ElHerbiboarSearchSpot.Group.C),
	E_NORTH(ElHerbiboarSearchSpot.Group.E, ElHerbiboarSearchSpot.Group.A),
	F_EAST(ElHerbiboarSearchSpot.Group.F, ElHerbiboarSearchSpot.Group.G),
	G_NORTH(ElHerbiboarSearchSpot.Group.G, ElHerbiboarSearchSpot.Group.F),
	H_NORTH(ElHerbiboarSearchSpot.Group.H, ElHerbiboarSearchSpot.Group.D),
	H_EAST(ElHerbiboarSearchSpot.Group.H, ElHerbiboarStart.DRIFTWOOD),
	I_EAST(ElHerbiboarSearchSpot.Group.I, ElHerbiboarStart.LEPRECHAUN),
	I_SOUTH_1(ElHerbiboarSearchSpot.Group.I, ElHerbiboarStart.GHOST_MUSHROOM),
	I_SOUTH_2(ElHerbiboarSearchSpot.Group.I, ElHerbiboarStart.CAMP_ENTRANCE),
	I_WEST(ElHerbiboarSearchSpot.Group.I, ElHerbiboarSearchSpot.Group.E),
	;

	private final ElHerbiboarSearchSpot.Group to;
	private final ElHerbiboarStart fromStart;
	private final ElHerbiboarSearchSpot.Group fromGroup;

	ElHerbiboarRule(ElHerbiboarSearchSpot.Group to, ElHerbiboarSearchSpot.Group from)
	{
		this(to, null, from);
	}

	ElHerbiboarRule(ElHerbiboarSearchSpot.Group to, ElHerbiboarStart fromStart)
	{
		this(to, fromStart, null);
	}

	/**
	 * Returns whether the next {@link ElHerbiboarSearchSpot} can be deterministically selected based on the starting
	 * location and the path taken so far, based on the rules defined on the OSRS wiki.
	 *
	 * {@see https://oldschool.runescape.wiki/w/Herbiboar#Guaranteed_tracks}
	 *
	 * @param start       Herbiboar's starting spot where the tracking path begins
	 * @param currentPath A list of {@link ElHerbiboarSearchSpot}s which have been searched thus far, and the next one to search
	 * @return {@code true} if a rule can be applied, {@code false} otherwise
	 */
	static boolean canApplyRule(ElHerbiboarStart start, List<ElHerbiboarSearchSpot> currentPath)
	{
		if (start == null || currentPath.isEmpty())
		{
			return false;
		}

		int lastIndex = currentPath.size() - 1;
		ElHerbiboarSearchSpot.Group goingTo = currentPath.get(lastIndex).getGroup();

		for (ElHerbiboarRule rule : values())
		{
			if (lastIndex > 0 && rule.matches(currentPath.get(lastIndex - 1).getGroup(), goingTo)
			|| lastIndex == 0 && rule.matches(start, goingTo))
			{
				return true;
			}
		}

		return false;
	}

	boolean matches(ElHerbiboarStart from, ElHerbiboarSearchSpot.Group to)
	{
		return this.matches(from, null, to);
	}

	boolean matches(ElHerbiboarSearchSpot.Group from, ElHerbiboarSearchSpot.Group to)
	{
		return this.matches(null, from, to);
	}

	boolean matches(ElHerbiboarStart fromStart, ElHerbiboarSearchSpot.Group fromGroup, ElHerbiboarSearchSpot.Group to)
	{
		return this.to == to
			&& (fromStart != null && this.fromStart == fromStart || fromGroup != null && this.fromGroup == fromGroup);
	}
}
