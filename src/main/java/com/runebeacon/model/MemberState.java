package com.runebeacon.model;

import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * Typed view of a member's state (see PROTOCOL.md "State"). Every field may be null when the
 * member hasn't shared it.
 */
@Data
public class MemberState
{
	private String rsn;
	private String gameState;
	private Boolean idle;
	private Integer world;
	private List<String> worldTypes;
	private Location loc;
	private String area;
	private Activity activity;
	private String status;
	private String training;

	private Integer combatLevel;
	private int[] hp;
	private int[] prayer;
	private Integer spec;
	private Integer run;
	private Boolean stamina;
	private String poison;
	private Integer weight;
	private String spellbook;
	private Boolean vengeance;

	/** skill name -> [boosted, real, xp] */
	private Map<String, long[]> skills;
	private Integer totalLevel;

	private int[] inventory;
	private int[] equipment;
	private List<String> prayers;
	private int[] runePouch;
	private int[] quiver;

	@Data
	public static class Location
	{
		private int x;
		private int y;
		private int plane;
		private boolean instanced;
	}

	@Data
	public static class Activity
	{
		private String type;
		private String name;
	}
}
