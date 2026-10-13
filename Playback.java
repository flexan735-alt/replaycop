package com.mocapstudio.server;

import com.mocapstudio.data.Recording;
import com.mocapstudio.data.Recording.Frame;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Plays recordings back using REAL entities (Mannequins for players, vanilla mobs for tracked mobs).
 * Gravity, knockback, collisions all come from vanilla. Each tick we steer the entity toward the
 * recorded position; if it was just hit we let physics play, then it walks back to its path.
 */
public class Playback {
	public static final List<Playback> SESSIONS = new ArrayList<>();
	public static final Set<UUID> GHOSTS = new HashSet<>();

	static class Actor {
		Recording rec; Recording.Track track; int delay;
		Entity entity; boolean finished; int displacedUntil; int equipIdx, blockIdx;
	}

	final MinecraftServer server;
	final ServerLevel level;
	final Vec3 off;
	final List<Actor> actors = new ArrayList<>();
	int tick = 0;

	public Playback(ServerLevel level, Vec3 offset) {
		this.level = level; this.server = level.getServer(); this.off = offset;
	}

	public void addRecording(Recording r, int delay) {
		Actor a = new Actor(); a.rec = r; a.delay = delay; actors.add(a);
		for (Recording.Track t : r.tracks) {
			if (t.frames.isEmpty()) continue;
			Actor m = new Actor(); m.track = t; m.rec = null; m.delay = delay; actors.add(m);
		}
	}

	public static void tickAll() {
		SESSIONS.removeIf(Playback::tickSession);
	}

	public static void clearAll() {
		for (Playback p : SESSIONS) p.discardAll();
		SESSIONS.clear();
	}

	void discardAll() {
		for (Actor a : actors) if (a.entity != null && !a.entity.isRemoved()) a.entity.discard();
	}

	/** @return true when finished */
	boolean tickSession() {
		boolean alive = false;
		for (Actor a : actors) {
			if (a.finished) continue;
			alive = true;
			int local = tick - a.delay;
			if (local < 0) continue;
			if (a.rec != null) stepPlayer(a, local); else stepTrack(a, local);
		}
		tick++;
		if (!alive) { discardAll(); }
		return !alive;
	}

	void cmd(String c) {
		CommandSourceStack src = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
		server.getCommands().performPrefixedCommand(src, c);
	}

	// ---------------------------------------------------------------- players
	void stepPlayer(Actor a, int local) {
		Recording r = a.rec;
		if (local >= r.frames.size()) {
			if (a.entity != null) { GHOSTS.remove(a.entity.getUUID()); a.entity.discard(); }
			a.finished = true; return;
		}
		Frame f = r.frames.get(local);
		if (a.entity == null) a.entity = spawnActor(r, f);
		if (a.entity == null || a.entity.isRemoved()) { a.finished = true; return; }
		LivingEntity e = (LivingEntity) a.entity;
		String id = e.getStringUUID();

		while (a.equipIdx < r.equip.size() && r.equip.get(a.equipIdx).tick <= local) {
			Recording.EquipEvent ev = r.equip.get(a.equipIdx++);
			cmd("item replace entity " + id + " " + ev.slot + " with " + ev.item);
		}
		while (a.blockIdx < r.blocks.size() && r.blocks.get(a.blockIdx).tick <= local) {
			Recording.BlockEvent b = r.blocks.get(a.blockIdx++);
			int bx = b.x + (int) off.x, by = b.y + (int) off.y, bz = b.z + (int) off.z;
			if (b.state == null) cmd("setblock " + bx + " " + by + " " + bz + " minecraft:air destroy");
			else cmd("setblock " + bx + " " + by + " " + bz + " " + b.state);
		}

		steer(a, e, f.x + off.x, f.y + off.y, f.z + off.z, f.yaw, f.pitch, f.head);
		e.setSprinting((f.flags & Recording.SPRINT) != 0);
		Pose[] poses = Pose.values();
		if (f.pose >= 0 && f.pose < poses.length) e.setPose(poses[f.pose]);
		if ((f.flags & Recording.SWING) != 0) e.swing(InteractionHand.MAIN_HAND);
		e.setHealth(e.getMaxHealth()); // actors can be hit/knocked back but never die
	}

	Entity spawnActor(Recording r, Frame f) {
		Entity m = EntityType.MANNEQUIN.create(level, EntitySpawnReason.COMMAND);
		if (m == null) return null;
		m.setPos(f.x + off.x, f.y + off.y, f.z + off.z);
		m.setYRot(f.yaw);
		m.addTag("mocapstudio");
		m.setCustomName(Component.literal(r.name));
		m.setCustomNameVisible(true);
		GHOSTS.add(m.getUUID());
		level.addFreshEntity(m);
		String profile;
		if (r.skin == null || r.skin.isEmpty()) profile = "";
		else if (r.skin.startsWith("@")) profile = "profile:\"" + r.skin.substring(1) + "\",";
		else profile = "profile:{texture:\"mocapstudio:skin/" + r.skin + "\",model:\"" + (r.slim ? "slim" : "wide") + "\"},";
		cmd("data merge entity " + m.getStringUUID() + " {" + profile + "hide_description:1b,immovable:0b}");
		return m;
	}

	// ------------------------------------------------------------------ mobs
	void stepTrack(Actor a, int local) {
		Recording.Track t = a.track;
		int idx = local - t.spawnTick;
		if (idx < 0) return;
		if (a.entity == null) {
			var type = EntityType.byString(t.type).orElse(null);
			Entity ent = type == null ? null : type.create(level, EntitySpawnReason.COMMAND);
			if (!(ent instanceof LivingEntity le)) { a.finished = true; return; }
			float[] f0 = t.frames.get(0);
			ent.setPos(f0[0] + off.x, f0[1] + off.y, f0[2] + off.z);
			ent.addTag("mocapstudio");
			if (ent instanceof Mob mob) {
				mob.goalSelector.removeAllGoals(g -> true);
				mob.targetSelector.removeAllGoals(g -> true);
				mob.setPersistenceRequired();
			}
			GHOSTS.add(ent.getUUID());
			level.addFreshEntity(ent);
			cmd("data merge entity " + ent.getStringUUID() + " {DeathLootTable:\"minecraft:empty\"}");
			a.entity = ent;
		}
		if (a.entity.isRemoved()) { a.finished = true; return; }
		LivingEntity e = (LivingEntity) a.entity;
		if (idx >= t.frames.size()) {
			if (t.died) {
				e.hurtServer(level, level.damageSources().generic(), 10000f); // dies at the recorded moment
			} else {
				e.discard();
			}
			GHOSTS.remove(e.getUUID());
			a.finished = true; return;
		}
		float[] f = t.frames.get(idx);
		steer(a, e, f[0] + off.x, f[1] + off.y, f[2] + off.z, f[3], f[4], f[3]);
	}

	// --------------------------------------------------------------- physics
	void steer(Actor a, LivingEntity e, double tx, double ty, double tz, float yaw, float pitch, float head) {
		e.setYRot(yaw); e.setXRot(pitch); e.setYHeadRot(head); e.setYBodyRot(yaw);

		if (e.hurtTime > 0) a.displacedUntil = tick + 12;       // just hit: let knockback happen
		Vec3 p = e.position();
		double dx = tx - p.x, dy = ty - p.y, dz = tz - p.z;
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist > 10 || Math.abs(dy) > 8) {                       // way off path: snap back
			e.setPos(tx, ty, tz); e.setDeltaMovement(Vec3.ZERO); return;
		}
		if (tick < a.displacedUntil) return;                       // physics only while being knocked around

		double cap = 0.8;                                          // max walk-back speed (blocks/tick)
		double k = dist > cap ? cap / dist : 1.0;
		Vec3 v = e.getDeltaMovement();
		double vy = v.y;                                           // gravity stays vanilla
		if (e.onGround() && dy > 0.6 && dist < 3) vy = 0.42;       // jump up to the path
		else if (dy > 2.0) vy = 0.5;                               // far below the path: climb/swim up
		e.setDeltaMovement(dx * k, vy, dz * k);
	}
}
