package com.runebeacon;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Maps map region ids to an area name and (optionally) an activity.
 *
 * <p>regions.json is derived from RuneLite's Discord plugin region table
 * (net.runelite.client.plugins.discord.DiscordGameEventType), Copyright (c) 2018, Tomas Slusny,
 * used under the BSD 2-Clause license.
 */
@Slf4j
@Singleton
public class RegionData
{
	private static final class Entry
	{
		String type;
		String name;
		int[] regions;
	}

	private final Map<Integer, String> areaByRegion = new HashMap<>();
	private final Map<Integer, String[]> activityByRegion = new HashMap<>();

	@Inject
	RegionData(Gson gson)
	{
		try (InputStream in = RegionData.class.getResourceAsStream("regions.json");
			Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			List<Entry> entries = gson.fromJson(reader, new TypeToken<List<Entry>>(){}.getType());
			// Load broad areas first so more specific entries override them.
			for (String pass : new String[]{"REGIONS", "DUNGEONS", "CITIES"})
			{
				for (Entry e : entries)
				{
					if (pass.equals(e.type))
					{
						for (int r : e.regions)
						{
							areaByRegion.put(r, e.name);
						}
					}
				}
			}
			for (Entry e : entries)
			{
				String type = activityType(e.type);
				if (type == null)
				{
					continue;
				}
				for (int r : e.regions)
				{
					activityByRegion.put(r, new String[]{type, e.name});
					areaByRegion.putIfAbsent(r, e.name);
				}
			}
		}
		catch (Exception ex)
		{
			log.warn("Unable to load RuneBeacon region data", ex);
		}
	}

	private static String activityType(String areaType)
	{
		switch (areaType)
		{
			case "BOSSES":
				return "boss";
			case "RAIDS":
				return "raid";
			case "MINIGAMES":
				return "minigame";
			default:
				return null;
		}
	}

	public String areaName(int regionId)
	{
		return areaByRegion.get(regionId);
	}

	/** @return [type, name] or null */
	public String[] activity(int regionId)
	{
		return activityByRegion.get(regionId);
	}
}
