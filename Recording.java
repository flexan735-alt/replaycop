package com.mocapstudio.data;

import java.util.ArrayList;
import java.util.List;

/** Everything captured in one take. Serialized to JSON with Gson. */
public class Recording {
	public static final int CROUCH = 1, SPRINT = 2, SWING = 4, GROUND = 8;

	public String name;
	/** "" = default skin, "@Name" = Mojang account name, otherwise an imported skin file name. */
	public String skin = "";
	public boolean slim = false;
	public int length;
	public List<Frame> frames = new ArrayList<>();
	public List<EquipEvent> equip = new ArrayList<>();
	public List<BlockEvent> blocks = new ArrayList<>();
	/** Mobs / animals that were near the player while recording (the "cow that gets killed"). */
	public List<Track> tracks = new ArrayList<>();

	public static class Frame {
		public double x, y, z;
		public float yaw, pitch, head;
		public int pose;
		public int flags;
	}

	public static class EquipEvent {
		public int tick;
		public String slot;  // item-command slot name, e.g. weapon.mainhand
		public String item;  // e.g. minecraft:diamond_sword
	}

	public static class BlockEvent {
		public int tick, x, y, z;
		public String state; // null = block was broken
	}

	public static class Track {
		public String type;
		public int spawnTick;
		public boolean died;
		public int endTick = -1;
		public List<float[]> frames = new ArrayList<>(); // x,y,z,yaw,pitch
	}
}
