package com.paytracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TrackerData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("paytracker.json");
    public static TrackerData INSTANCE = new TrackerData();

    public double threshold = 25_000_000d;
    /** Maximum payment to log. 0 = no maximum. */
    public double maximum = 0d;

    /**
     * Regexes matched against incoming SYSTEM chat messages.
     * Named groups: amount (required), suffix (optional k/m/b/t), player (optional).
     * Edit these in config/paytracker.json if your server words payments differently.
     */
    public List<String> patterns = new ArrayList<>(List.of(
            // DonutSMP: "You were paid $ 60K by Name"
            "(?i)you were paid\\s*\\$?\\s*(?<amount>[\\d,]+(?:\\.\\d+)?)\\s*(?<suffix>[kmbt])?\\s+by\\s+(?<player>\\.?\\w{2,16})",
            // "Name paid you $ 25M"
            "(?i)(?<player>\\.?\\w{2,16})\\s+(?:has\\s+|just\\s+)?paid you\\s*\\$?\\s*(?<amount>[\\d,]+(?:\\.\\d+)?)\\s*(?<suffix>[kmbt])?\\b",
            // "You received $25M from Name"
            "(?i)you (?:have )?received\\s*\\$?\\s*(?<amount>[\\d,]+(?:\\.\\d+)?)\\s*(?<suffix>[kmbt])?\\b(?: from (?<player>\\.?\\w{2,16}))?"
    ));

    public List<Entry> entries = new ArrayList<>();

    public static class Entry {
        public String player;
        public double amount;
        public long time;

        public Entry(String player, double amount, long time) {
            this.player = player;
            this.amount = amount;
            this.time = time;
        }
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                TrackerData d = GSON.fromJson(Files.readString(FILE), TrackerData.class);
                if (d != null) {
                    if (d.entries == null) d.entries = new ArrayList<>();
                    if (d.patterns == null || d.patterns.isEmpty()) d.patterns = new TrackerData().patterns;
                    INSTANCE = d;
                }
            } else {
                save();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
