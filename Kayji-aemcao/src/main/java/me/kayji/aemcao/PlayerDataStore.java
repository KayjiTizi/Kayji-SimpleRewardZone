package me.kayji.aemcao;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class PlayerDataStore {
    private final KayjiAemCaoPlugin plugin;
    private final File file;
    private YamlConfiguration data;
    private final Map<UUID, LinkedHashMap<String, HomeData>> homes = new LinkedHashMap<>();
    private final Map<UUID, Location> backs = new LinkedHashMap<>();

    PlayerDataStore(KayjiAemCaoPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    void load() {
        plugin.getDataFolder().mkdirs();
        data = YamlConfiguration.loadConfiguration(file);
        homes.clear();
        backs.clear();

        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) {
            return;
        }

        for (String uuidText : players.getKeys(false)) {
            UUID uuid = parseUuid(uuidText);
            if (uuid == null) {
                continue;
            }

            LinkedHashMap<String, HomeData> playerHomes = new LinkedHashMap<>();
            ConfigurationSection homeSection = data.getConfigurationSection("players." + uuidText + ".homes");
            if (homeSection != null) {
                for (String homeName : homeSection.getKeys(false)) {
                    readLocation("players." + uuidText + ".homes." + homeName).ifPresent(location ->
                            playerHomes.put(homeName.toLowerCase(), new HomeData(homeName, location)));
                }
            }
            homes.put(uuid, playerHomes);

            readLocation("players." + uuidText + ".back").ifPresent(location -> backs.put(uuid, location));
        }
    }

    void save() {
        data.set("players", null);

        for (Map.Entry<UUID, LinkedHashMap<String, HomeData>> entry : homes.entrySet()) {
            String playerPath = "players." + entry.getKey();
            for (HomeData home : entry.getValue().values()) {
                writeLocation(playerPath + ".homes." + home.name(), home.location());
            }
        }

        for (Map.Entry<UUID, Location> entry : backs.entrySet()) {
            writeLocation("players." + entry.getKey() + ".back", entry.getValue());
        }

        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Khong the luu data.yml: " + exception.getMessage());
        }
    }

    Collection<HomeData> homes(UUID uuid) {
        return playerHomes(uuid).values();
    }

    int homeCount(UUID uuid) {
        return playerHomes(uuid).size();
    }

    Optional<HomeData> home(UUID uuid, String name) {
        return Optional.ofNullable(playerHomes(uuid).get(name.toLowerCase()));
    }

    void setHome(UUID uuid, String name, Location location) {
        playerHomes(uuid).put(name.toLowerCase(), new HomeData(name, location.clone()));
    }

    boolean deleteHome(UUID uuid, String name) {
        return playerHomes(uuid).remove(name.toLowerCase()) != null;
    }

    Optional<Location> back(UUID uuid) {
        Location location = backs.get(uuid);
        return location == null ? Optional.empty() : Optional.of(location.clone());
    }

    void setBack(UUID uuid, Location location) {
        backs.put(uuid, location.clone());
    }

    void clearBack(UUID uuid) {
        backs.remove(uuid);
    }

    private LinkedHashMap<String, HomeData> playerHomes(UUID uuid) {
        return homes.computeIfAbsent(uuid, ignored -> new LinkedHashMap<>());
    }

    private Optional<Location> readLocation(String path) {
        String worldName = data.getString(path + ".world");
        if (worldName == null) {
            return Optional.empty();
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return Optional.empty();
        }

        return Optional.of(new Location(
                world,
                data.getDouble(path + ".x"),
                data.getDouble(path + ".y"),
                data.getDouble(path + ".z"),
                (float) data.getDouble(path + ".yaw"),
                (float) data.getDouble(path + ".pitch")
        ));
    }

    private void writeLocation(String path, Location location) {
        data.set(path + ".world", location.getWorld() == null ? null : location.getWorld().getName());
        data.set(path + ".x", location.getX());
        data.set(path + ".y", location.getY());
        data.set(path + ".z", location.getZ());
        data.set(path + ".yaw", location.getYaw());
        data.set(path + ".pitch", location.getPitch());
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
