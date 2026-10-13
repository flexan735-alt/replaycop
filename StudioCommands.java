package com.mocapstudio.server;

import com.mocapstudio.data.Recording;
import com.mocapstudio.data.Scene;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** /studio ... — the dashboard simply runs these. */
public class StudioCommands {
	static String s(CommandContext<CommandSourceStack> c, String n) { return StringArgumentType.getString(c, n); }
	static int ok(CommandContext<CommandSourceStack> c, String m) { c.getSource().sendSuccess(() -> Component.literal(m), false); return 1; }
	static int fail(CommandContext<CommandSourceStack> c, String m) { c.getSource().sendFailure(Component.literal(m)); return 0; }

	public static void register(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("studio")
			.then(Commands.literal("record")
				.then(Commands.argument("name", StringArgumentType.word())
					.executes(c -> record(c, null))
					.then(Commands.argument("scene", StringArgumentType.word()).executes(c -> record(c, s(c, "scene"))))))
			.then(Commands.literal("stop").executes(StudioCommands::stop))
			.then(Commands.literal("play")
				.then(Commands.argument("name", StringArgumentType.word())
					.executes(c -> play(c, false))
					.then(Commands.literal("relative").executes(c -> play(c, true)))))
			.then(Commands.literal("playscene")
				.then(Commands.argument("scene", StringArgumentType.word())
					.executes(c -> playScene(c, false))
					.then(Commands.literal("relative").executes(c -> playScene(c, true)))))
			.then(Commands.literal("clear").executes(c -> { Playback.clearAll(); return ok(c, "Stopped all playback."); }))
			.then(Commands.literal("delete")
				.then(Commands.argument("name", StringArgumentType.word()).executes(c ->
					Store.deleteRecording(s(c, "name")) ? ok(c, "Deleted.") : fail(c, "No such recording."))))
			.then(Commands.literal("skin")
				.then(Commands.argument("name", StringArgumentType.word())
					.then(Commands.literal("file")
						.then(Commands.argument("skinfile", StringArgumentType.word())
							.then(Commands.argument("model", StringArgumentType.word())
								.executes(c -> skin(c, s(c, "skinfile"), s(c, "model").equals("slim"))))))
					.then(Commands.literal("user")
						.then(Commands.argument("account", StringArgumentType.word())
							.executes(c -> skin(c, "@" + s(c, "account"), false))))
					.then(Commands.literal("default").executes(c -> skin(c, "", false)))))
			.then(Commands.literal("scene")
				.then(Commands.literal("create").then(Commands.argument("scene", StringArgumentType.word()).executes(c -> {
					try { Store.saveScene(s(c, "scene"), new Scene()); } catch (Exception e) { return fail(c, "Could not save."); }
					return ok(c, "Scene created.");
				})))
				.then(Commands.literal("delete").then(Commands.argument("scene", StringArgumentType.word()).executes(c ->
					Store.deleteScene(s(c, "scene")) ? ok(c, "Deleted.") : fail(c, "No such scene."))))
				.then(Commands.literal("add").then(Commands.argument("scene", StringArgumentType.word())
					.then(Commands.argument("name", StringArgumentType.word())
						.executes(c -> sceneAdd(c, 0))
						.then(Commands.argument("delay", IntegerArgumentType.integer(0)).executes(c -> sceneAdd(c, IntegerArgumentType.getInteger(c, "delay")))))))
				.then(Commands.literal("remove").then(Commands.argument("scene", StringArgumentType.word())
					.then(Commands.argument("name", StringArgumentType.word()).executes(StudioCommands::sceneRemove))))));
	}

	static int record(CommandContext<CommandSourceStack> c, String scene) {
		try {
			ServerPlayer p = c.getSource().getPlayerOrException();
			String n = s(c, "name");
			if (!Store.validName(n)) return fail(c, "Bad name (letters, digits, _ - only).");
			if (Recorder.get(p) != null) return fail(c, "Already recording. /studio stop first.");
			if (scene != null) {
				Scene sc = Store.loadScene(scene);
				if (sc == null) return fail(c, "No such scene: " + scene);
				startScene(p, sc, false); // play existing actors while you act out the new one
			}
			Recorder.start(p, n, scene);
			return ok(c, "Recording '" + n + "'" + (scene != null ? " (scene '" + scene + "' playing along)" : "") + "...");
		} catch (Exception e) { return fail(c, "Players only."); }
	}

	static int stop(CommandContext<CommandSourceStack> c) {
		try {
			ServerPlayer p = c.getSource().getPlayerOrException();
			Recorder r = Recorder.get(p);
			if (r == null) return fail(c, "Not recording.");
			int n = r.stop();
			return ok(c, "Saved " + n + " ticks (" + n / 20 + "s).");
		} catch (Exception e) { return fail(c, "Players only."); }
	}

	static Vec3 offsetFor(ServerPlayer p, Recording first, boolean relative) {
		if (!relative || first.frames.isEmpty()) return Vec3.ZERO;
		Recording.Frame f = first.frames.get(0);
		return new Vec3(Math.round(p.getX() - f.x), Math.round(p.getY() - f.y), Math.round(p.getZ() - f.z));
	}

	static int play(CommandContext<CommandSourceStack> c, boolean relative) {
		try {
			ServerPlayer p = c.getSource().getPlayerOrException();
			Recording r = Store.loadRecording(s(c, "name"));
			if (r == null) return fail(c, "No such recording.");
			Playback pb = new Playback((ServerLevel) p.level(), offsetFor(p, r, relative));
			pb.addRecording(r, 0);
			Playback.SESSIONS.add(pb);
			return ok(c, "Playing '" + r.name + "'. Hit it — it gets knocked back and walks back to its path.");
		} catch (Exception e) { return fail(c, "Players only."); }
	}

	static void startScene(ServerPlayer p, Scene sc, boolean relative) {
		Recording first = null;
		for (Scene.Entry e : sc.entries) { first = Store.loadRecording(e.recording); if (first != null) break; }
		if (first == null) return;
		Playback pb = new Playback((ServerLevel) p.level(), offsetFor(p, first, relative));
		for (Scene.Entry e : sc.entries) {
			Recording r = Store.loadRecording(e.recording);
			if (r != null) pb.addRecording(r, e.delay);
		}
		Playback.SESSIONS.add(pb);
	}

	static int playScene(CommandContext<CommandSourceStack> c, boolean relative) {
		try {
			ServerPlayer p = c.getSource().getPlayerOrException();
			Scene sc = Store.loadScene(s(c, "scene"));
			if (sc == null) return fail(c, "No such scene.");
			startScene(p, sc, relative);
			return ok(c, "Scene playing (" + sc.entries.size() + " actors).");
		} catch (Exception e) { return fail(c, "Players only."); }
	}

	static int skin(CommandContext<CommandSourceStack> c, String skin, boolean slim) {
		Recording r = Store.loadRecording(s(c, "name"));
		if (r == null) return fail(c, "No such recording.");
		r.skin = skin; r.slim = slim;
		try { Store.saveRecording(r); } catch (Exception e) { return fail(c, "Could not save."); }
		return ok(c, "Skin set.");
	}

	static int sceneAdd(CommandContext<CommandSourceStack> c, int delay) {
		Scene sc = Store.loadScene(s(c, "scene"));
		if (sc == null) return fail(c, "No such scene.");
		if (Store.loadRecording(s(c, "name")) == null) return fail(c, "No such recording.");
		sc.entries.add(new Scene.Entry(s(c, "name"), delay));
		try { Store.saveScene(s(c, "scene"), sc); } catch (Exception e) { return fail(c, "Could not save."); }
		return ok(c, "Added to scene.");
	}

	static int sceneRemove(CommandContext<CommandSourceStack> c) {
		Scene sc = Store.loadScene(s(c, "scene"));
		if (sc == null) return fail(c, "No such scene.");
		sc.entries.removeIf(e -> e.recording.equals(s(c, "name")));
		try { Store.saveScene(s(c, "scene"), sc); } catch (Exception e) { return fail(c, "Could not save."); }
		return ok(c, "Removed.");
	}
}
