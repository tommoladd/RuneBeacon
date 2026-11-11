package com.runebeacon;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * Builds the local player's state each tick and produces patches containing only what changed
 * since the last send. Must be used on the client thread.
 */
@Singleton
class StateCollector
{
	/** Client idle cycles (20ms each) before the player counts as AFK: 5 minutes. */
	private static final int IDLE_CYCLES = 5 * 60 * 50;
	/** How long after the last xp drop a skill still counts as "training". */
	private static final long TRAINING_TIMEOUT_MS = 90_000;
	private static final int STATUS_MAX = 64;

	private static final int[] RUNE_POUCH_TYPE = {
		VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3, VarbitID.RUNE_POUCH_TYPE_4
	};
	private static final int[] RUNE_POUCH_QTY = {
		VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3, VarbitID.RUNE_POUCH_QUANTITY_4
	};

	/** Every real skill (Skill.values() also contains the deprecated OVERALL pseudo-skill). */
	private static final Skill[] TRAINABLE_SKILLS = {
		Skill.ATTACK, Skill.DEFENCE, Skill.STRENGTH, Skill.HITPOINTS, Skill.RANGED, Skill.PRAYER,
		Skill.MAGIC, Skill.COOKING, Skill.WOODCUTTING, Skill.FLETCHING, Skill.FISHING, Skill.FIREMAKING,
		Skill.CRAFTING, Skill.SMITHING, Skill.MINING, Skill.HERBLORE, Skill.AGILITY, Skill.THIEVING,
		Skill.SLAYER, Skill.FARMING, Skill.RUNECRAFT, Skill.HUNTER, Skill.CONSTRUCTION, Skill.SAILING,
	};

	private final Client client;
	private final RuneBeaconConfig config;
	private final RegionData regionData;
	private final Gson gson;

	private final Map<String, JsonElement> lastSent = new HashMap<>();
	private final Map<Skill, Integer> lastXp = new EnumMap<>(Skill.class);
	private Skill trainingSkill;
	private long trainingAt;

	@Inject
	StateCollector(Client client, RuneBeaconConfig config, RegionData regionData, Gson gson)
	{
		this.client = client;
		this.config = config;
		this.regionData = regionData;
		this.gson = gson;
	}

	/** Forget what was sent so the next patch contains the full state (e.g. after reconnecting). */
	void reset()
	{
		lastSent.clear();
	}

	/** Called when logging in/hopping so the initial burst of StatChanged events isn't counted as training. */
	void resetTraining()
	{
		lastXp.clear();
		trainingSkill = null;
	}

	void onStatChanged(StatChanged e)
	{
		Skill skill = e.getSkill();
		Integer prev = lastXp.put(skill, e.getXp());
		// Hitpoints xp accompanies every combat xp drop, so it would mask the real skill.
		if (prev != null && e.getXp() > prev && skill != Skill.HITPOINTS)
		{
			trainingSkill = skill;
			trainingAt = System.currentTimeMillis();
		}
	}

	/**
	 * @return a patch of changed keys (null value = key removed), or null if nothing changed.
	 */
	JsonObject buildPatch()
	{
		Map<String, JsonElement> current = collect();
		JsonObject patch = new JsonObject();

		for (Map.Entry<String, JsonElement> e : current.entrySet())
		{
			JsonElement prev = lastSent.get(e.getKey());
			if (!e.getValue().equals(prev))
			{
				patch.add(e.getKey(), e.getValue());
			}
		}
		// Keys we sent before but no longer share (sharing disabled, left an activity, etc.)
		for (String key : lastSent.keySet())
		{
			if (!current.containsKey(key))
			{
				patch.add(key, JsonNull.INSTANCE);
			}
		}

		if (patch.size() == 0)
		{
			return null;
		}
		lastSent.clear();
		lastSent.putAll(current);
		return patch;
	}

	private Map<String, JsonElement> collect()
	{
		Map<String, JsonElement> s = new HashMap<>();
		GameState gameState = client.getGameState();
		s.put("gameState", gson.toJsonTree(gameStateName(gameState)));

		Player local = client.getLocalPlayer();
		if (gameState != GameState.LOGGED_IN || local == null)
		{
			// Keep whatever we last sent for everything else; just report the state change.
			for (Map.Entry<String, JsonElement> e : lastSent.entrySet())
			{
				s.putIfAbsent(e.getKey(), e.getValue());
			}
			return s;
		}

		if (local.getName() != null)
		{
			s.put("rsn", gson.toJsonTree(local.getName()));
		}
		s.put("world", gson.toJsonTree(client.getWorld()));
		JsonArray types = new JsonArray();
		for (WorldType t : client.getWorldType())
		{
			types.add(t.name());
		}
		s.put("worldTypes", types);
		s.put("idle", gson.toJsonTree(Math.min(client.getMouseIdleTicks(), client.getKeyboardIdleTicks()) > IDLE_CYCLES));

		String status = config.customStatus() == null ? "" : config.customStatus().trim();
		if (!status.isEmpty())
		{
			s.put("status", gson.toJsonTree(status.length() > STATUS_MAX ? status.substring(0, STATUS_MAX) : status));
		}

		WorldPoint wp = playerLocation(local);
		int region = wp == null ? -1 : wp.getRegionID();

		if (config.shareLocation() && wp != null)
		{
			JsonObject loc = new JsonObject();
			loc.addProperty("x", wp.getX());
			loc.addProperty("y", wp.getY());
			loc.addProperty("plane", wp.getPlane());
			loc.addProperty("instanced", client.getTopLevelWorldView().isInstance());
			s.put("loc", loc);
			String area = regionData.areaName(region);
			if (area != null)
			{
				s.put("area", gson.toJsonTree(area));
			}
		}

		if (config.shareActivity())
		{
			String[] activity = region == -1 ? null : regionData.activity(region);
			if (activity != null)
			{
				JsonObject a = new JsonObject();
				a.addProperty("type", activity[0]);
				a.addProperty("name", activity[1]);
				s.put("activity", a);
			}
			if (trainingSkill != null && System.currentTimeMillis() - trainingAt < TRAINING_TIMEOUT_MS)
			{
				s.put("training", gson.toJsonTree(trainingSkill.getName()));
			}
		}

		if (config.shareVitals())
		{
			s.put("combatLevel", gson.toJsonTree(local.getCombatLevel()));
			s.put("hp", pair(client.getBoostedSkillLevel(Skill.HITPOINTS), client.getRealSkillLevel(Skill.HITPOINTS)));
			s.put("prayer", pair(client.getBoostedSkillLevel(Skill.PRAYER), client.getRealSkillLevel(Skill.PRAYER)));
			s.put("spec", gson.toJsonTree(client.getVarpValue(VarPlayerID.SA_ENERGY) / 10));
			s.put("run", gson.toJsonTree(client.getEnergy() / 100));
			s.put("stamina", gson.toJsonTree(client.getVarbitValue(VarbitID.STAMINA_ACTIVE) == 1));
			int poison = client.getVarpValue(VarPlayerID.POISON);
			s.put("poison", gson.toJsonTree(poison >= 1_000_000 ? "venom" : poison > 0 ? "poison" : "none"));
			s.put("weight", gson.toJsonTree(client.getWeight()));
			s.put("spellbook", gson.toJsonTree(spellbookName(client.getVarbitValue(VarbitID.SPELLBOOK))));
			s.put("vengeance", gson.toJsonTree(client.getVarbitValue(VarbitID.VENGEANCE_REBOUND) == 1));
		}

		if (config.shareSkills())
		{
			JsonObject skills = new JsonObject();
			for (Skill skill : TRAINABLE_SKILLS)
			{
				JsonArray v = new JsonArray();
				v.add(client.getBoostedSkillLevel(skill));
				v.add(client.getRealSkillLevel(skill));
				v.add(client.getSkillExperience(skill));
				skills.add(skill.name(), v);
			}
			s.put("skills", skills);
			s.put("totalLevel", gson.toJsonTree(client.getTotalLevel()));
		}

		if (config.shareInventory())
		{
			s.put("inventory", items(client.getItemContainer(InventoryID.INV), 28));
			JsonArray pouch = runePouch();
			if (pouch != null)
			{
				s.put("runePouch", pouch);
			}
		}

		if (config.shareEquipment())
		{
			ItemContainer worn = client.getItemContainer(InventoryID.WORN);
			s.put("equipment", items(worn, 14));
			// The quiver's extra ammo slot only counts while the quiver is worn in the cape slot.
			int quiverItem = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO);
			int quiverQty = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT);
			if (wearingQuiver(worn) && quiverItem > 0 && quiverQty > 0)
			{
				s.put("quiver", pair(quiverItem, quiverQty));
			}
		}

		if (config.sharePrayers())
		{
			JsonArray prayers = new JsonArray();
			for (Prayer p : Prayer.values())
			{
				if (client.getVarbitValue(p.getVarbit()) == 1)
				{
					prayers.add(p.name());
				}
			}
			s.put("prayers", prayers);
		}

		return s;
	}

	private WorldPoint playerLocation(Player local)
	{
		LocalPoint lp = local.getLocalLocation();
		if (lp == null)
		{
			return null;
		}
		// Map instances (raids, POH, boss rooms) back to the real-world template location.
		return WorldPoint.fromLocalInstance(client, lp);
	}

	private static String spellbookName(int varbit)
	{
		switch (varbit)
		{
			case 1:
				return "ancient";
			case 2:
				return "lunar";
			case 3:
				return "arceuus";
			default:
				return "standard";
		}
	}

	private static boolean wearingQuiver(ItemContainer worn)
	{
		if (worn == null)
		{
			return false;
		}
		Item cape = worn.getItem(EquipmentInventorySlot.CAPE.getSlotIdx());
		if (cape == null)
		{
			return false;
		}
		switch (cape.getId())
		{
			case ItemID.DIZANAS_QUIVER_UNCHARGED:
			case ItemID.DIZANAS_QUIVER_UNCHARGED_TROUVER:
			case ItemID.DIZANAS_QUIVER_CHARGED:
			case ItemID.DIZANAS_QUIVER_CHARGED_TROUVER:
			case ItemID.DIZANAS_QUIVER_INFINITE:
			case ItemID.DIZANAS_QUIVER_INFINITE_TROUVER:
				return true;
			default:
				return false;
		}
	}

	private JsonArray runePouch()
	{
		EnumComposition runeEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		JsonArray out = new JsonArray();
		for (int i = 0; i < RUNE_POUCH_TYPE.length; i++)
		{
			int type = client.getVarbitValue(RUNE_POUCH_TYPE[i]);
			int qty = client.getVarbitValue(RUNE_POUCH_QTY[i]);
			if (type == 0 || qty == 0)
			{
				continue;
			}
			out.add(runeEnum.getIntValue(type));
			out.add(qty);
		}
		return out.size() == 0 ? null : out;
	}

	private static JsonArray items(ItemContainer container, int size)
	{
		JsonArray out = new JsonArray();
		Item[] items = container == null ? new Item[0] : container.getItems();
		for (int i = 0; i < size; i++)
		{
			if (i < items.length && items[i].getId() > -1 && items[i].getQuantity() > 0)
			{
				out.add(items[i].getId());
				out.add(items[i].getQuantity());
			}
			else
			{
				out.add(-1);
				out.add(0);
			}
		}
		return out;
	}

	private static JsonArray pair(int a, int b)
	{
		JsonArray arr = new JsonArray();
		arr.add(a);
		arr.add(b);
		return arr;
	}

	private static String gameStateName(GameState state)
	{
		switch (state)
		{
			case LOGGED_IN:
				return "LOGGED_IN";
			case LOADING:
				return "LOADING";
			case HOPPING:
				return "HOPPING";
			default:
				return "LOGIN_SCREEN";
		}
	}
}
