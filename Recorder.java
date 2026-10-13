package com.mocapstudio.server;

import com.mocapstudio.data.Recording;
import com.mocapstudio.data.Recording.Frame;
import com.mocapstudio.data.Scene;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;

import java.util.*;

/** Records one player: movement, rotation, pose, swings, equipment, blocks, and nearby mobs. */
public class Recorder {
	public static final Map<UUID, Recorder> ACTIVE = new HashMap<>();
	static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
		EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	static final String[] SLOT_NAMES = {"weapon.mainhand", "weapon.offhand", "armor.head", "armor.chest", "armor.legs", "armor.feet"};

	final ServerPlayer player;
	final Recording rec = new Recording();
	final String scene;
	int tick = 0;
	boolean wasSwinging;
	final Map<UUID, Recording.Track> tracks = new HashMap<>();
	final Set<UUID> done = new HashSet<>();
	final String[] lastEquip = new String[SLOTS.length];

	public Recorder(ServerPlayer p, String name, String scene) {
		this.player = p; this.scene = scene; rec.name = name;
	}

	public static void start(ServerPlayer p, String name, String scene) {
		ACTIVE.put(p.getUUID(), new Recorder(p, name, scene));
	}

	public static Recorder get(ServerPlayer p) { return ACTIVE.get(p.getUUID()); }

	static double r(double v) { return Math.round(v * 1000.0) / 1000.0; }
	static float rf(float v) { return Math.round(v * 100f) / 100f; }

	public static void tickAll() {
		for (Recorder rc : new ArrayList<>(ACTIVE.values())) {
			if (rc.player.isRemoved() || rc.player.hasDisconnected()) { rc.stop(); continue; }
			rc.tick();
		}
	}

	void tick() {
		ServerLevel level = (ServerLevel) player.level();
		Frame f = new Frame();
		f.x = r(player.getX()); f.y = r(player.getY()); f.z = r(player.getZ());
		f.yaw = rf(player.getYRot()); f.pitch = rf(player.getXRot()); f.head = rf(player.getYHeadRot());
		f.pose = player.getPose().ordinal();
		int fl = 0;
		if (player.isCrouching()) fl |= Recording.CROUCH;
		if (player.isSprinting()) fl |= Recording.SPRINT;
		if (player.onGround()) fl |= Recording.GROUND;
		boolean sw = player.swinging;
		if (sw && !wasSwinging) fl |= Recording.SWING;
		wasSwinging = sw;
		f.flags = fl;
		rec.frames.add(f);

		// equipment changes
		for (int i = 0; i < SLOTS.length; i++) {
			String id = BuiltInRegistries.ITEM.getKey(player.getItemBySlot(SLOTS[i]).getItem()).toString();
			if (!id.equals(lastEquip[i])) {
				lastEquip[i] = id;
				Recording.EquipEvent ev = new Recording.EquipEvent();
				ev.tick = tick; ev.slot = SLOT_NAMES[i]; ev.item = id;
				rec.equip.add(ev);
			}
		}

		// nearby living entities (mobs/animals) -> tracks
		AABB box = player.getBoundingBox().inflate(48);
		Set<UUID> seen = new HashSet<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
				x -> !(x instanceof Player) && !x.getTags().contains("mocapstudio"))) {
			UUID id = e.getUUID();
			if (done.contains(id)) continue;
			seen.add(id);
			Recording.Track t = tracks.get(id);
			if (t == null) {
				t = new Recording.Track();
				t.type = EntityType.getKey(e.getType()).toString();
				t.spawnTick = tick;
				tracks.put(id, t);
				rec.tracks.add(t);
			}
			if (e.isDeadOrDying()) {
				t.died = true; t.endTick = tick; done.add(id);
			} else {
				t.frames.add(new float[]{(float) r(e.getX()), (float) r(e.getY()), (float) r(e.getZ()), rf(e.getYRot()), rf(e.getXRot())});
			}
		}
		for (Map.Entry<UUID, Recording.Track> en : tracks.entrySet()) {
			if (!seen.contains(en.getKey()) && !done.contains(en.getKey())) {
				en.getValue().endTick = tick; done.add(en.getKey());
			}
		}
		tick++;
	}

	public static void onBreak(ServerPlayer p, BlockPos pos) {
		Recorder rc = get(p);
		if (rc == null) return;
		Recording.BlockEvent b = new Recording.BlockEvent();
		b.tick = rc.tick; b.x = pos.getX(); b.y = pos.getY(); b.z = pos.getZ(); b.state = null;
		rc.rec.blocks.add(b);
	}

	public static void onPlace(ServerPlayer p, BlockPos pos, BlockState st) {
		Recorder rc = get(p);
		if (rc == null) return;
		Recording.BlockEvent b = new Recording.BlockEvent();
		b.tick = rc.tick; b.x = pos.getX(); b.y = pos.getY(); b.z = pos.getZ(); b.state = stateString(st);
		rc.rec.blocks.add(b);
	}

	static <T extends Comparable<T>> String pv(Property<T> p, BlockState s) { return p.getName(s.getValue(p)); }

	static String stateString(BlockState s) {
		StringBuilder sb = new StringBuilder(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());
		if (!s.getProperties().isEmpty()) {
			sb.append('[');
			boolean first = true;
			for (Property<?> p : s.getProperties()) {
				if (!first) sb.append(',');
				first = false;
				sb.append(p.getName()).append('=').append(pv(p, s));
			}
			sb.append(']');
		}
		return sb.toString();
	}

	/** Stops, saves. Returns the number of ticks recorded. */
	public int stop() {
		ACTIVE.remove(player.getUUID());
		for (Map.Entry<UUID, Recording.Track> en : tracks.entrySet()) {
			if (!done.contains(en.getKey())) en.getValue().endTick = tick;
		}
		rec.length = rec.frames.size();
		try {
			Store.saveRecording(rec);
			if (scene != null) {
				Scene s = Store.loadScene(scene);
				if (s == null) s = new Scene();
				s.entries.add(new Scene.Entry(rec.name, 0));
				Store.saveScene(scene, s);
			}
		} catch (Exception e) { e.printStackTrace(); }
		return rec.length;
	}
}
