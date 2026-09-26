package me.kayji.simplerewardzone;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class RewardDataStore {
    private final KayjiSimpleRewardZone plugin;
    private final File file;
    private YamlConfiguration data;
    private final Map<String, RewardTemplate> templates = new LinkedHashMap<>();

    RewardDataStore(KayjiSimpleRewardZone plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    void load() {
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
        }

        data = YamlConfiguration.loadConfiguration(file);
        templates.clear();

        ConfigurationSection section = data.getConfigurationSection("templates");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                String path = "templates." + id + ".";
                String name = data.getString(path + "display-name", "#D473EFRuong thinh " + id);
                Material icon = Material.matchMaterial(data.getString(path + "icon", "CHEST"));
                List<ItemStack> items = readItems(path + "items");
                templates.put(id, new RewardTemplate(id, name, icon, items));
            }
        }

        if (templates.isEmpty()) {
            RewardTemplate template = new RewardTemplate("default", "#D473EFRuong thinh mac dinh", Material.CHEST, List.of(
                    new ItemStack(Material.DIAMOND, 5),
                    new ItemStack(Material.EMERALD, 12),
                    new ItemStack(Material.GOLDEN_APPLE, 3)
            ));
            templates.put(template.id(), template);
            save();
        }
    }

    private List<ItemStack> readItems(String path) {
        List<ItemStack> items = new ArrayList<>();
        List<?> rawItems = data.getList(path);
        if (rawItems == null) {
            return items;
        }

        for (Object rawItem : rawItems) {
            if (rawItem instanceof ItemStack item && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }
        return items;
    }

    void save() {
        data.set("templates", null);
        for (RewardTemplate template : templates.values()) {
            String path = "templates." + template.id() + ".";
            data.set(path + "display-name", template.displayName());
            data.set(path + "icon", template.icon().name());
            data.set(path + "items", template.items());
        }

        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Khong the luu data.yml: " + exception.getMessage());
        }
    }

    Collection<RewardTemplate> templates() {
        return templates.values();
    }

    Optional<RewardTemplate> template(String id) {
        return Optional.ofNullable(templates.get(id));
    }

    RewardTemplate createTemplate() {
        int number = 1;
        String id;
        do {
            id = "ruong-" + number++;
        } while (templates.containsKey(id));

        RewardTemplate template = new RewardTemplate(id, "#D473EFRuong thinh " + (number - 1), Material.CHEST, List.of());
        templates.put(id, template);
        save();
        return template;
    }

    void deleteTemplate(String id) {
        templates.remove(id);
        save();
    }

    void saveActiveDrop(ActiveDrop drop) {
        if (drop == null) {
            data.set("active-drop", null);
            saveDataOnly();
            return;
        }

        Location location = drop.location();
        data.set("active-drop.template-id", drop.templateId());
        data.set("active-drop.world", location.getWorld() == null ? null : location.getWorld().getName());
        data.set("active-drop.x", location.getBlockX());
        data.set("active-drop.y", location.getBlockY());
        data.set("active-drop.z", location.getBlockZ());
        data.set("active-drop.spawned-at", drop.spawnedAtMillis());
        saveDataOnly();
    }

    Optional<ActiveDrop> loadActiveDrop() {
        String worldName = data.getString("active-drop.world");
        String templateId = data.getString("active-drop.template-id");
        if (worldName == null || templateId == null) {
            return Optional.empty();
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return Optional.empty();
        }

        Location location = new Location(
                world,
                data.getInt("active-drop.x"),
                data.getInt("active-drop.y"),
                data.getInt("active-drop.z")
        );
        long spawnedAt = data.getLong("active-drop.spawned-at", System.currentTimeMillis());
        return Optional.of(new ActiveDrop(templateId, location, spawnedAt));
    }

    List<TemporaryBlockState> loadBeaconBlocks() {
        List<TemporaryBlockState> states = new ArrayList<>();
        for (Map<?, ?> rawState : data.getMapList("active-drop.beacon-blocks")) {
            Object world = rawState.get("world");
            Object blockData = rawState.get("data");
            Integer x = asInteger(rawState.get("x"));
            Integer y = asInteger(rawState.get("y"));
            Integer z = asInteger(rawState.get("z"));
            if (!(world instanceof String worldName)
                    || !(blockData instanceof String blockDataString)
                    || x == null
                    || y == null
                    || z == null) {
                continue;
            }
            states.add(new TemporaryBlockState(worldName, x, y, z, blockDataString));
        }
        return states;
    }

    void saveBeaconBlocks(List<TemporaryBlockState> states) {
        if (states == null || states.isEmpty()) {
            data.set("active-drop.beacon-blocks", null);
            saveDataOnly();
            return;
        }

        List<Map<String, Object>> rawStates = new ArrayList<>();
        for (TemporaryBlockState state : states) {
            Map<String, Object> rawState = new LinkedHashMap<>();
            rawState.put("world", state.worldName());
            rawState.put("x", state.x());
            rawState.put("y", state.y());
            rawState.put("z", state.z());
            rawState.put("data", state.blockData());
            rawStates.add(rawState);
        }
        data.set("active-drop.beacon-blocks", rawStates);
        saveDataOnly();
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private void saveDataOnly() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Khong the luu data.yml: " + exception.getMessage());
        }
    }
}
