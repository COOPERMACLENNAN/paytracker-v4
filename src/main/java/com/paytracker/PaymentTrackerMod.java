package com.paytracker;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PaymentTrackerMod implements ClientModInitializer {
    private static KeyBinding openKey;
    private static String lastOverlay = "";
    private static long lastOverlayTime = 0;

    @Override
    public void onInitializeClient() {
        TrackerData.load();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.paytracker.open_panel", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT,
                "key.categories.paytracker"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new PaymentScreen());
            }
        });

        // Only server/system messages (chat box + action bar), not player chat,
        // so other players can't fake a payment.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> handle(message.getString(), overlay));
    }

    private static void handle(String text, boolean overlay) {
        if (overlay) {
            // action bar text can be re-sent repeatedly; ignore repeats for 5s
            long now = System.currentTimeMillis();
            boolean repeat = text.equals(lastOverlay) && now - lastOverlayTime < 5000;
            lastOverlay = text;
            lastOverlayTime = now;
            if (repeat) return;
        }

        TrackerData data = TrackerData.INSTANCE;
        for (String regex : data.patterns) {
            Matcher m;
            try {
                m = Pattern.compile(regex).matcher(text);
            } catch (Exception e) {
                continue; // bad regex in config, skip it
            }
            if (!m.find()) continue;

            double amount;
            try {
                amount = Money.toValue(m.group("amount"), group(m, "suffix"));
            } catch (Exception e) {
                continue;
            }
            if (amount < data.threshold) return;
            if (data.maximum > 0 && amount > data.maximum) return;

            String player = group(m, "player");
            if (player == null) player = "Unknown";

            data.entries.add(new TrackerData.Entry(player, amount, System.currentTimeMillis()));
            TrackerData.save();

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("[PayTracker] ").formatted(Formatting.LIGHT_PURPLE)
                        .append(Text.literal("Logged $" + Money.format(amount) + " from " + player)
                                .formatted(Formatting.WHITE)), false);
            }
            return;
        }
    }

    private static String group(Matcher m, String name) {
        try {
            return m.group(name);
        } catch (IllegalArgumentException e) {
            return null; // this pattern has no such group
        }
    }
}
