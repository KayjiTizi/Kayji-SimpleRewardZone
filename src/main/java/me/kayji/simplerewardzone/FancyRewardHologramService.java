package me.kayji.simplerewardzone;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FancyRewardHologramService implements RewardHologramService {
    private static final Pattern HEX_PATTERN = Pattern.compile("#[A-Fa-f0-9]{6}");

    private final KayjiSimpleRewardZone plugin;
    private final String hologramName;
    private Hologram hologram;

    FancyRewardHologramService(KayjiSimpleRewardZone plugin) {
        this.plugin = plugin;
        this.hologramName = "kayji_rewardzone_active";
    }

    @Override
    public void show(RewardTemplate template, Location location) {
        remove();
        if (!plugin.getConfig().getBoolean("hologram.enabled", true)) {
            return;
        }

        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        Location hologramLocation = location.clone().add(0.5, plugin.getConfig().getDouble("hologram.height", 1.55), 0.5);
        TextHologramData data = new TextHologramData(hologramName, hologramLocation);
        data.setPersistent(false);
        data.setVisibilityDistance(Math.max(16, plugin.getConfig().getInt("hologram.visibility-distance", 96)));
        data.setText(lines(template));
        data.setTextAlignment(TextDisplay.TextAlignment.CENTER);
        data.setBillboard(Display.Billboard.CENTER);
        data.setTextShadow(plugin.getConfig().getBoolean("hologram.text-shadow", true));
        data.setSeeThrough(plugin.getConfig().getBoolean("hologram.see-through", false));
        data.setBackground(Color.fromARGB(0, 0, 0, 0));

        hologram = manager.create(data);
        manager.addHologram(hologram);
        hologram.forceUpdate();
    }

    @Override
    public void update(RewardTemplate template) {
        if (hologram == null || !(hologram.getData() instanceof TextHologramData data)) {
            return;
        }
        data.setText(lines(template));
        hologram.queueUpdate();
    }

    @Override
    public void remove() {
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        if (hologram != null) {
            manager.removeHologram(hologram);
            hologram = null;
        }

        Optional<Hologram> existing = manager.getHologram(hologramName);
        existing.ifPresent(manager::removeHologram);
    }

    private List<String> lines(RewardTemplate template) {
        List<String> lines = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("hologram.lines")) {
            lines.add(toMiniMessage(plugin.replaceHologram(line, template)));
        }
        return lines;
    }

    private String toMiniMessage(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        String converted = convertHexColors(message);
        converted = converted
                .replace("&0", "<black>")
                .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")
                .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")
                .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")
                .replace("&9", "<blue>")
                .replace("&a", "<green>")
                .replace("&b", "<aqua>")
                .replace("&c", "<red>")
                .replace("&d", "<light_purple>")
                .replace("&e", "<yellow>")
                .replace("&f", "<white>")
                .replace("&A", "<green>")
                .replace("&B", "<aqua>")
                .replace("&C", "<red>")
                .replace("&D", "<light_purple>")
                .replace("&E", "<yellow>")
                .replace("&F", "<white>")
                .replace("&l", "<bold>")
                .replace("&L", "<bold>")
                .replace("&o", "<italic>")
                .replace("&O", "<italic>")
                .replace("&n", "<underlined>")
                .replace("&N", "<underlined>")
                .replace("&m", "<strikethrough>")
                .replace("&M", "<strikethrough>")
                .replace("&k", "<obfuscated>")
                .replace("&K", "<obfuscated>")
                .replace("&r", "<reset>")
                .replace("&R", "<reset>");
        return converted.replace("§", "");
    }

    private String convertHexColors(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, "<" + matcher.group() + ">");
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
