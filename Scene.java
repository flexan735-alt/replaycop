package com.mocapstudio.data;

import java.util.ArrayList;
import java.util.List;

public class Scene {
	public List<Entry> entries = new ArrayList<>();

	public static class Entry {
		public String recording;
		public int delay; // ticks after scene start
		public Entry() {}
		public Entry(String r, int d) { recording = r; delay = d; }
	}
}
