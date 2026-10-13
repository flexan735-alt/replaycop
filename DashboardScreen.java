package com.mocapstudio.client;

import com.mocapstudio.server.Store;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** The dashboard. Every button just runs a /studio command (or imports a file locally). */
public class DashboardScreen extends Screen {
	static final int PER_PAGE = 7;
	static String selected = "", status = "";
	static String tName = "take1", tScene = "scene1", tPath = "", tSkin = "", tUser = "";
	static int page = 0;
	static boolean slim = false;

	EditBox recName, sceneName, skinPath, skinName, userName;

	public DashboardScreen() { super(Component.literal("Mocap Studio")); }

	void run(String cmd) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.connection.sendCommand(cmd);
	}

	void saveFields() {
		if (recName != null) { tName = recName.getValue(); tScene = sceneName.getValue(); tPath = skinPath.getValue();
			tSkin = skinName.getValue(); tUser = userName.getValue(); }
	}

	void refresh() { saveFields(); rebuildWidgets(); }

	EditBox box(int x, int y, int w, String label, String val) {
		EditBox b = new EditBox(font, x, y, w, 18, Component.literal(label));
		b.setMaxLength(200); b.setValue(val); b.setHint(Component.literal(label));
		return addRenderableWidget(b);
	}

	Button btn(String t, int x, int y, int w, Runnable r) {
		return addRenderableWidget(Button.builder(Component.literal(t), b -> r.run()).bounds(x, y, w, 20).build());
	}

	@Override
	protected void init() {
		int L = width / 2 - 215, R = width / 2 + 5, y = 28;

		// ---- LEFT: recordings
		recName = box(L, y, 120, "recording name", tName);
		btn("Record", L + 124, y - 1, 48, () -> { saveFields(); run("studio record " + tName); status = "Recording " + tName; });
		btn("Stop", L + 174, y - 1, 40, () -> { run("studio stop"); status = "Stopped & saved"; });

		List<String> recs = Store.listRecordings();
		int pages = Math.max(1, (recs.size() + PER_PAGE - 1) / PER_PAGE);
		page = Math.min(page, pages - 1);
		y += 34;
		for (int i = page * PER_PAGE; i < Math.min(recs.size(), (page + 1) * PER_PAGE); i++) {
			String n = recs.get(i);
			Button b = btn((n.equals(selected) ? "> " : "") + n, L, y, 214, () -> { selected = n; refresh(); });
			y += 22;
		}
		btn("<", L, 28 + 34 + PER_PAGE * 22, 20, () -> { page = Math.max(0, page - 1); refresh(); });
		btn(">", L + 194, 28 + 34 + PER_PAGE * 22, 20, () -> { page++; refresh(); });
		int by = 28 + 34 + PER_PAGE * 22 + 24;
		btn("Play", L, by, 68, () -> run("studio play " + selected));
		btn("Play here", L + 73, by, 68, () -> run("studio play " + selected + " relative"));
		btn("Delete", L + 146, by, 68, () -> { run("studio delete " + selected); selected = ""; status = "Deleted"; refresh(); });
		btn("Stop all playback", L, by + 24, 214, () -> run("studio clear"));

		// ---- RIGHT: scenes
		y = 28;
		sceneName = box(R, y, 120, "scene name", tScene);
		btn("Create", R + 124, y - 1, 90, () -> { saveFields(); run("studio scene create " + tScene); status = "Scene created"; });
		y += 24;
		btn("Add selected", R, y, 105, () -> { saveFields(); run("studio scene add " + tScene + " " + selected); });
		btn("Remove selected", R + 109, y, 105, () -> { saveFields(); run("studio scene remove " + tScene + " " + selected); });
		y += 24;
		btn("Play scene", R, y, 105, () -> { saveFields(); run("studio playscene " + tScene); });
		btn("Play scene here", R + 109, y, 105, () -> { saveFields(); run("studio playscene " + tScene + " relative"); });
		y += 24;
		btn("Record new actor INTO scene", R, y, 214, () -> {
			saveFields(); run("studio record " + tName + " " + tScene); status = "Recording " + tName + " into " + tScene; });
		y += 24;
		btn("Delete scene", R, y, 214, () -> { saveFields(); run("studio scene delete " + tScene); });

		// ---- RIGHT: skins
		y += 40;
		skinPath = box(R, y, 214, "path to skin .png (or drag a file here)", tPath);
		y += 22;
		skinName = box(R, y, 120, "skin name (lowercase)", tSkin);
		btn(slim ? "Model: slim" : "Model: wide", R + 124, y - 1, 90, () -> { slim = !slim; refresh(); });
		y += 24;
		btn("Import skin file", R, y, 105, () -> { saveFields(); importSkin(Path.of(tPath.replace("\"", ""))); });
		btn("Apply to selected", R + 109, y, 105, () -> {
			saveFields(); run("studio skin " + selected + " file " + tSkin.toLowerCase() + " " + (slim ? "slim" : "wide")); status = "Skin applied"; });
		y += 24;
		userName = box(R, y, 120, "Minecraft account name", tUser);
		btn("Use account skin", R + 124, y - 1, 90, () -> { saveFields(); run("studio skin " + selected + " user " + tUser); status = "Skin applied"; });

		btn("Close", width / 2 - 40, height - 28, 80, this::onClose);
	}

	@Override
	public void onFilesDrop(List<Path> paths) {
		if (paths.isEmpty()) return;
		saveFields();
		Path p = paths.get(0);
		tPath = p.toString();
		if (tSkin.isEmpty()) {
			String n = p.getFileName().toString().toLowerCase().replaceAll("\\.png$", "").replaceAll("[^a-z0-9_-]", "_");
			tSkin = n;
		}
		importSkin(p);
		rebuildWidgets();
	}

	void importSkin(Path src) {
		saveFields();
		try {
			String n = tSkin.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
			if (n.isEmpty()) { status = "Give the skin a name first"; return; }
			BufferedImage img = ImageIO.read(src.toFile());
			if (img == null || img.getWidth() != 64 || img.getHeight() != 64) { status = "Skin must be a 64x64 PNG"; return; }
			Path pack = FabricLoader.getInstance().getGameDir().resolve("resourcepacks/MocapStudioSkins");
			Path dir = pack.resolve("assets/mocapstudio/textures/skin");
			Files.createDirectories(dir);
			Path meta = pack.resolve("pack.mcmeta");
			if (!Files.exists(meta)) Files.writeString(meta, "{\"pack\":{\"description\":\"Mocap Studio skins\",\"min_format\":75,\"max_format\":75}}");
			Files.copy(src, dir.resolve(n + ".png"), StandardCopyOption.REPLACE_EXISTING);
			tSkin = n;
			status = "Imported '" + n + "'. Enable pack 'MocapStudioSkins' once (Options > Resource Packs), or press F3+T.";
		} catch (Exception e) { status = "Import failed: " + e.getMessage(); }
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		super.render(g, mx, my, pt);
		int L = width / 2 - 215, R = width / 2 + 5;
		g.drawString(font, "Mocap Studio", width / 2 - 36, 10, 0xFFFFFFFF);
		g.drawString(font, "Recordings", L, 18, 0xFFAAAAAA);
		g.drawString(font, "Scenes & skins", R, 18, 0xFFAAAAAA);
		g.drawString(font, "Selected: " + (selected.isEmpty() ? "-" : selected), L, height - 50, 0xFFFFFF55);
		g.drawString(font, status, L, height - 38, 0xFF55FF55);
	}

	@Override public boolean isPauseScreen() { return false; }
}
