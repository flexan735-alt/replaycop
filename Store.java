package com.mocapstudio.server;

import com.google.gson.Gson;
import com.mocapstudio.data.Recording;
import com.mocapstudio.data.Scene;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Store {
	static final Gson GSON = new Gson();

	public static Path root() { return FabricLoader.getInstance().getGameDir().resolve("mocapstudio"); }
	static Path recDir() { return root().resolve("recordings"); }
	static Path sceneDir() { return root().resolve("scenes"); }

	public static boolean validName(String s) { return s != null && s.matches("[A-Za-z0-9_-]{1,40}"); }

	public static Recording loadRecording(String n) {
		try { return GSON.fromJson(Files.readString(recDir().resolve(n + ".json")), Recording.class); }
		catch (Exception e) { return null; }
	}
	public static void saveRecording(Recording r) throws IOException {
		Files.createDirectories(recDir());
		Files.writeString(recDir().resolve(r.name + ".json"), GSON.toJson(r));
	}
	public static boolean deleteRecording(String n) {
		try { return Files.deleteIfExists(recDir().resolve(n + ".json")); } catch (IOException e) { return false; }
	}

	public static Scene loadScene(String n) {
		try { return GSON.fromJson(Files.readString(sceneDir().resolve(n + ".json")), Scene.class); }
		catch (Exception e) { return null; }
	}
	public static void saveScene(String n, Scene s) throws IOException {
		Files.createDirectories(sceneDir());
		Files.writeString(sceneDir().resolve(n + ".json"), GSON.toJson(s));
	}
	public static boolean deleteScene(String n) {
		try { return Files.deleteIfExists(sceneDir().resolve(n + ".json")); } catch (IOException e) { return false; }
	}

	public static List<String> list(Path dir) {
		List<String> out = new ArrayList<>();
		if (!Files.isDirectory(dir)) return out;
		try (Stream<Path> s = Files.list(dir)) {
			s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json"))
				.map(n -> n.substring(0, n.length() - 5)).sorted().forEach(out::add);
		} catch (IOException ignored) {}
		return out;
	}
	public static List<String> listRecordings() { return list(recDir()); }
	public static List<String> listScenes() { return list(sceneDir()); }
}
