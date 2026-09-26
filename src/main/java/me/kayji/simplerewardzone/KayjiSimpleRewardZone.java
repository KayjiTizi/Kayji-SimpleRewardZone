package me.kayji.simplerewardzone;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class KayjiSimpleRewardZone extends JavaPlugin implements Listener, TabExecutor {
    private static final String HOLOGRAM_TAG = "kayji_simplerewardzone_hologram";

    private final Random random = new Random();
    private final Set<UUID> playersInZone = ConcurrentHashMap.newKeySet();
    private final List<UUID> hologramIds = new ArrayList<>();
    private final List<TemporaryBlockState> beaconBlockStates = new ArrayList<>();
    private RewardDataStore dataStore;
    private RewardGuiManager guiManager;
    private RewardHologramService hologramService;
    private CompatScheduler scheduler;
    private volatile ActiveDrop activeDrop;
    private CompatTask autoSpawnTask;
    private CompatTask pendingSpawnTask;
    private CompatTask zoneTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataStore = new RewardDataStore(this);
        dataStore.load();
        guiManager = new RewardGuiManager(this, dataStore);
        hologramService = createHologramService();
        scheduler = new CompatScheduler(this);

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(guiManager, this);

        PluginCommand command = getCommand("rewardzone");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        restoreActiveDrop();
        restartAutoSpawnTask();
        restartZoneTask();
    }

    @Override
    public void onDisable() {
        if (autoSpawnTask != null) {
            autoSpawnTask.cancel();
        }
        if (pendingSpawnTask != null) {
            pendingSpawnTask.cancel();
        }
        if (zoneTask != null) {
            zoneTask.cancel();
        }
        removeHologramAt(null);
        dataStore.saveActiveDrop(activeDrop);
        if (activeDrop != null && !beaconBlockStates.isEmpty()) {
            dataStore.saveBeaconBlocks(beaconBlockStates);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rewardzone.admin")) {
            sender.sendMessage(message("no-permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("gui") || args[0].equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(message("player-only"));
                return true;
            }
            guiManager.openMain(player);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        if (subCommand.equals("spawn")) {
            if (args.length >= 2) {
                if (args[1].equalsIgnoreCase("random")) {
                    spawnRandomDrop(sender, true);
                } else {
                    spawnTemplateDrop(sender, true, args[1]);
                }
            } else {
                spawnConfiguredDrop(sender, true);
            }
            return true;
        }
        if (subCommand.equals("reload")) {
            reloadAll();
            sender.sendMessage(message("reloaded"));
            return true;
        }
        if (subCommand.equals("despawn") || subCommand.equals("remove")) {
            removeActiveDropSafely(true, null);
            sender.sendMessage(message("prefix") + Text.color("#F0C987Da xoa ruong thinh dang active."));
            return true;
        }

        sender.sendMessage(Text.color("#D473EF/rewardzone #B9A7F5mo GUI quan ly."));
        sender.sendMessage(Text.color("#D473EF/rewardzone spawn [random|id] #B9A7F5tha ruong ngay."));
        sender.sendMessage(Text.color("#D473EF/rewardzone reload #B9A7F5tai lai config va data."));
        sender.sendMessage(Text.color("#D473EF/rewardzone despawn #B9A7F5xoa ruong dang active."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("gui", "spawn", "reload", "despawn").stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            List<String> options = new ArrayList<>();
            options.add("random");
            dataStore.templates().forEach(template -> options.add(template.id()));
            return options.stream()
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }

    void reloadAll() {
        reloadConfig();
        dataStore.load();
        beaconBlockStates.clear();
        hologramService.remove();
        hologramService = createHologramService();
        restoreActiveDrop();
        restartAutoSpawnTask();
        restartZoneTask();
    }

    private RewardHologramService createHologramService() {
        if (!Bukkit.getPluginManager().isPluginEnabled("FancyHolograms")) {
            getLogger().warning("FancyHolograms khong duoc bat, hologram ruong thinh se tam tat.");
            return new DisabledRewardHologramService();
        }

        try {
            return new FancyRewardHologramService(this);
        } catch (RuntimeException | LinkageError exception) {
            getLogger().warning("Khong the ket noi FancyHolograms API: " + exception.getMessage());
            return new DisabledRewardHologramService();
        }
    }

    void restartAutoSpawnTask() {
        if (autoSpawnTask != null) {
            autoSpawnTask.cancel();
            autoSpawnTask = null;
        }
        if (pendingSpawnTask != null) {
            pendingSpawnTask.cancel();
            pendingSpawnTask = null;
        }
        if (!getConfig().getBoolean("settings.auto-spawn")) {
            return;
        }

        long intervalTicks = Math.max(1, getConfig().getInt("settings.spawn-interval-minutes")) * 60L * 20L;
        long warningTicks = Math.max(0, getConfig().getInt("settings.pre-announce-seconds", 30)) * 20L;
        if (warningTicks >= intervalTicks) {
            warningTicks = Math.max(0, intervalTicks - 20L);
        }
        long initialDelay = Math.max(0, intervalTicks - warningTicks);
        long finalWarningTicks = warningTicks;
        autoSpawnTask = scheduler.runGlobalTimer(() -> startPreAnnouncedDrop(finalWarningTicks),
                initialDelay, intervalTicks);
    }

    private void startPreAnnouncedDrop(long warningTicks) {
        if (activeDrop != null && !getConfig().getBoolean("settings.replace-active-drop")) {
            return;
        }
        if (dataStore.templates().stream().noneMatch(template -> !template.isEmpty())) {
            return;
        }
        if (pendingSpawnTask != null) {
            pendingSpawnTask.cancel();
            pendingSpawnTask = null;
        }

        if (warningTicks <= 0) {
            spawnConfiguredDrop(Bukkit.getConsoleSender(), true);
            return;
        }

        announceUpcomingDrop((int) (warningTicks / 20L));
        pendingSpawnTask = scheduler.runGlobalLater(() -> {
            pendingSpawnTask = null;
            spawnConfiguredDrop(Bukkit.getConsoleSender(), true);
        }, warningTicks);
    }

    private void restartZoneTask() {
        if (zoneTask != null) {
            zoneTask.cancel();
        }

        zoneTask = scheduler.runGlobalTimer(() -> {
            if (activeDrop == null) {
                return;
            }
            Location taskLocation = activeDrop.location();
            scheduler.runAtLocation(taskLocation, () -> {
                if (activeDrop == null || !sameBlock(taskLocation, activeDrop.location())) {
                    return;
                }
                handleDespawnTime();
                updateZonePlayers();
                updateHologramText();
            });
        }, 20L, 20L);
    }

    boolean spawnRandomDrop(CommandSender sender, boolean announce) {
        List<RewardTemplate> templates = spawnableTemplates();
        if (templates.isEmpty()) {
            sender.sendMessage(message("no-template"));
            return false;
        }

        RewardTemplate template = templates.get(random.nextInt(templates.size()));
        return spawnDrop(sender, announce, template);
    }

    boolean spawnConfiguredDrop(CommandSender sender, boolean announce) {
        if (isSelectedSpawnMode()) {
            String templateId = getConfig().getString("settings.spawn-template-id", "");
            Optional<RewardTemplate> template = dataStore.template(templateId).filter(reward -> !reward.isEmpty());
            if (template.isPresent()) {
                return spawnDrop(sender, announce, template.get());
            }
            sender.sendMessage(message("prefix") + Text.color("#F0C987Ruong auto da chon khong ton tai hoac dang trong, se random."));
        }
        return spawnRandomDrop(sender, announce);
    }

    boolean spawnTemplateDrop(CommandSender sender, boolean announce, String templateId) {
        Optional<RewardTemplate> template = dataStore.template(templateId).filter(reward -> !reward.isEmpty());
        if (template.isEmpty()) {
            sender.sendMessage(message("prefix") + Text.color("#F07B93Khong tim thay ruong thinh #F0C987" + templateId + "#F07B93 hoac ruong dang trong."));
            return false;
        }
        return spawnDrop(sender, announce, template.get());
    }

    private boolean spawnDrop(CommandSender sender, boolean announce, RewardTemplate template) {
        if (activeDrop != null) {
            if (!getConfig().getBoolean("settings.replace-active-drop")) {
                sender.sendMessage(message("prefix") + Text.color("#F0C987Dang co ruong thinh active, dung #D473EF/rewardzone despawn #F0C987neu muon xoa."));
                return false;
            }
            removeActiveDropSafely(false, () -> scheduler.runGlobalLater(() -> spawnDrop(sender, announce, template), 1L));
            return true;
        }

        if (scheduler.isFolia()) {
            spawnRandomDropFolia(sender, announce, template);
            return true;
        }

        Optional<Location> location = findSafeLocation();
        if (location.isEmpty()) {
            sender.sendMessage(message("spawn-failed"));
            return false;
        }

        completeDropSpawn(template, location.get(), announce);
        return true;
    }

    private List<RewardTemplate> spawnableTemplates() {
        return dataStore.templates().stream()
                .filter(template -> !template.isEmpty())
                .toList();
    }

    private boolean isSelectedSpawnMode() {
        return getConfig().getString("settings.spawn-template-mode", "random").equalsIgnoreCase("selected");
    }

    private void completeDropSpawn(RewardTemplate template, Location chestLocation, boolean announce) {
        Block block = chestLocation.getBlock();
        block.setType(Material.CHEST, false);
        if (block.getState() instanceof Chest chest) {
            chest.setCustomName(Text.color(template.displayName()));
            chest.update(true, false);
        }

        activeDrop = new ActiveDrop(template.id(), chestLocation, System.currentTimeMillis());
        dataStore.saveActiveDrop(activeDrop);
        createBeaconBeam(chestLocation, false);
        playersInZone.clear();

        spawnHologram(template, chestLocation);
        playSpawnEffects(chestLocation);
        if (announce) {
            announceSpawn(chestLocation);
        }
    }

    private void spawnRandomDropFolia(CommandSender sender, boolean announce, RewardTemplate template) {
        World world = Bukkit.getWorld(getConfig().getString("settings.world", "world"));
        if (world == null) {
            sender.sendMessage(message("spawn-failed"));
            return;
        }

        int minX = getConfig().getInt("spawn-area.min-x");
        int maxX = getConfig().getInt("spawn-area.max-x");
        int minZ = getConfig().getInt("spawn-area.min-z");
        int maxZ = getConfig().getInt("spawn-area.max-z");
        int attempts = Math.max(1, getConfig().getInt("spawn-area.max-random-attempts", 80));
        List<SpawnCandidate> candidates = collectSpawnCandidates(world, minX, maxX, minZ, maxZ, attempts);
        if (candidates.isEmpty()) {
            sender.sendMessage(message("spawn-failed"));
            return;
        }

        tryFoliaSpawnCandidate(sender, announce, template, candidates, 0);
    }

    private void tryFoliaSpawnCandidate(CommandSender sender, boolean announce, RewardTemplate template,
                                        List<SpawnCandidate> candidates, int index) {
        if (index >= candidates.size()) {
            sender.sendMessage(message("spawn-failed"));
            return;
        }

        SpawnCandidate candidate = candidates.get(index);
        Location regionLocation = new Location(candidate.world(), candidate.x(), 0, candidate.z());
        scheduler.runAtLocation(regionLocation, () -> {
            Optional<Location> safeLocation = safeSurfaceLocation(candidate.world(), candidate.x(), candidate.z());
            if (safeLocation.isEmpty()) {
                tryFoliaSpawnCandidate(sender, announce, template, candidates, index + 1);
                return;
            }

            if (activeDrop != null && !getConfig().getBoolean("settings.replace-active-drop")) {
                sender.sendMessage(message("prefix") + Text.color("#F0C987Dang co ruong thinh active, dung #D473EF/rewardzone despawn #F0C987neu muon xoa."));
                return;
            }
            completeDropSpawn(template, safeLocation.get(), announce);
        });
    }

    private Optional<Location> findSafeLocation() {
        World world = Bukkit.getWorld(getConfig().getString("settings.world", "world"));
        if (world == null) {
            return Optional.empty();
        }

        int minX = getConfig().getInt("spawn-area.min-x");
        int maxX = getConfig().getInt("spawn-area.max-x");
        int minZ = getConfig().getInt("spawn-area.min-z");
        int maxZ = getConfig().getInt("spawn-area.max-z");
        int attempts = Math.max(1, getConfig().getInt("spawn-area.max-random-attempts", 80));
        boolean loadUnloadedChunks = getConfig().getBoolean("spawn-area.load-unloaded-chunks", false);

        if (!loadUnloadedChunks) {
            return findSafeLocationInLoadedChunks(world, minX, maxX, minZ, maxZ, attempts);
        }

        for (int attempt = 0; attempt < attempts; attempt++) {
            int x = randomBetween(minX, maxX);
            int z = randomBetween(minZ, maxZ);
            Optional<Location> safeLocation = safeSurfaceLocation(world, x, z);
            if (safeLocation.isPresent()) {
                return safeLocation;
            }
        }
        return Optional.empty();
    }

    private Optional<Location> findSafeLocationInLoadedChunks(World world, int minX, int maxX, int minZ, int maxZ, int attempts) {
        List<Chunk> chunks = new ArrayList<>();
        for (Chunk chunk : world.getLoadedChunks()) {
            int chunkMinX = chunk.getX() << 4;
            int chunkMaxX = chunkMinX + 15;
            int chunkMinZ = chunk.getZ() << 4;
            int chunkMaxZ = chunkMinZ + 15;
            if (chunkMaxX >= Math.min(minX, maxX)
                    && chunkMinX <= Math.max(minX, maxX)
                    && chunkMaxZ >= Math.min(minZ, maxZ)
                    && chunkMinZ <= Math.max(minZ, maxZ)) {
                chunks.add(chunk);
            }
        }

        if (chunks.isEmpty()) {
            return Optional.empty();
        }

        for (int attempt = 0; attempt < attempts; attempt++) {
            Chunk chunk = chunks.get(random.nextInt(chunks.size()));
            int x = clamp((chunk.getX() << 4) + random.nextInt(16), minX, maxX);
            int z = clamp((chunk.getZ() << 4) + random.nextInt(16), minZ, maxZ);
            Optional<Location> safeLocation = safeSurfaceLocation(world, x, z);
            if (safeLocation.isPresent()) {
                return safeLocation;
            }
        }

        for (Chunk chunk : chunks) {
            for (int offsetX = 0; offsetX < 16; offsetX += 3) {
                for (int offsetZ = 0; offsetZ < 16; offsetZ += 3) {
                    int x = (chunk.getX() << 4) + offsetX;
                    int z = (chunk.getZ() << 4) + offsetZ;
                    if (!isInsideSpawnArea(x, z, minX, maxX, minZ, maxZ)) {
                        continue;
                    }
                    Optional<Location> safeLocation = safeSurfaceLocation(world, x, z);
                    if (safeLocation.isPresent()) {
                        return safeLocation;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private List<SpawnCandidate> collectSpawnCandidates(World world, int minX, int maxX, int minZ, int maxZ, int attempts) {
        List<Chunk> chunks = new ArrayList<>();
        boolean loadUnloadedChunks = getConfig().getBoolean("spawn-area.load-unloaded-chunks", false);
        if (!loadUnloadedChunks) {
            for (Chunk chunk : world.getLoadedChunks()) {
                int chunkMinX = chunk.getX() << 4;
                int chunkMaxX = chunkMinX + 15;
                int chunkMinZ = chunk.getZ() << 4;
                int chunkMaxZ = chunkMinZ + 15;
                if (chunkMaxX >= Math.min(minX, maxX)
                        && chunkMinX <= Math.max(minX, maxX)
                        && chunkMaxZ >= Math.min(minZ, maxZ)
                        && chunkMinZ <= Math.max(minZ, maxZ)) {
                    chunks.add(chunk);
                }
            }
        }

        List<SpawnCandidate> candidates = new ArrayList<>();
        if (!chunks.isEmpty()) {
            for (int attempt = 0; attempt < attempts; attempt++) {
                Chunk chunk = chunks.get(random.nextInt(chunks.size()));
                int x = clamp((chunk.getX() << 4) + random.nextInt(16), minX, maxX);
                int z = clamp((chunk.getZ() << 4) + random.nextInt(16), minZ, maxZ);
                candidates.add(new SpawnCandidate(world, x, z));
            }
            for (Chunk chunk : chunks) {
                for (int offsetX = 0; offsetX < 16; offsetX += 4) {
                    for (int offsetZ = 0; offsetZ < 16; offsetZ += 4) {
                        int x = (chunk.getX() << 4) + offsetX;
                        int z = (chunk.getZ() << 4) + offsetZ;
                        if (isInsideSpawnArea(x, z, minX, maxX, minZ, maxZ)) {
                            candidates.add(new SpawnCandidate(world, x, z));
                        }
                    }
                }
            }
            return candidates;
        }

        if (loadUnloadedChunks) {
            for (int attempt = 0; attempt < attempts; attempt++) {
                candidates.add(new SpawnCandidate(world, randomBetween(minX, maxX), randomBetween(minZ, maxZ)));
            }
        }
        return candidates;
    }

    private Optional<Location> safeSurfaceLocation(World world, int x, int z) {
        int surfaceY = world.getHighestBlockYAt(x, z);
        Location chestLocation = new Location(world, x, surfaceY + 1, z);

        Block below = chestLocation.clone().subtract(0, 1, 0).getBlock();
        Block target = chestLocation.getBlock();
        Block above = chestLocation.clone().add(0, 1, 0).getBlock();
        if (below.getType().isSolid()
                && !below.isLiquid()
                && target.isPassable()
                && above.isPassable()) {
                return Optional.of(chestLocation);
        }
        return Optional.empty();
    }

    private boolean isInsideSpawnArea(int x, int z, int minX, int maxX, int minZ, int maxZ) {
        return x >= Math.min(minX, maxX)
                && x <= Math.max(minX, maxX)
                && z >= Math.min(minZ, maxZ)
                && z <= Math.max(minZ, maxZ);
    }

    private int clamp(int value, int first, int second) {
        int min = Math.min(first, second);
        int max = Math.max(first, second);
        return Math.max(min, Math.min(max, value));
    }

    private int randomBetween(int first, int second) {
        int min = Math.min(first, second);
        int max = Math.max(first, second);
        return random.nextInt(max - min + 1) + min;
    }

    private void restoreActiveDrop() {
        activeDrop = null;
        dataStore.loadActiveDrop().ifPresentOrElse(drop -> {
            activeDrop = drop;
            scheduler.runAtLocation(drop.location(), () -> restoreActiveDropInRegion(drop));
        }, () -> {
            restoreBeaconBeamBlocksSafely();
            removeHologramAt(null);
            playersInZone.clear();
        });
    }

    private void restoreActiveDropInRegion(ActiveDrop drop) {
        removeHologramAt(drop.location());
        Location location = drop.location();
        try {
            if (location.getBlock().getType() == Material.CHEST) {
                activeDrop = drop;
                createBeaconBeam(location, true);
                dataStore.template(drop.templateId()).ifPresent(template -> spawnHologram(template, location));
            } else {
                restoreBeaconBeamBlocks();
                activeDrop = null;
                dataStore.saveActiveDrop(null);
            }
        } catch (IllegalStateException exception) {
            restoreBeaconBeamBlocks();
            activeDrop = null;
            dataStore.saveActiveDrop(null);
            getLogger().warning("Khong the khoi phuc ruong thinh active: " + exception.getMessage());
        }
        playersInZone.clear();
    }

    private void handleDespawnTime() {
        int despawnMinutes = getConfig().getInt("settings.despawn-minutes", 60);
        if (despawnMinutes <= 0 || activeDrop == null) {
            return;
        }
        long aliveMillis = System.currentTimeMillis() - activeDrop.spawnedAtMillis();
        if (aliveMillis >= despawnMinutes * 60_000L) {
            removeActiveDrop(true);
            Bukkit.broadcastMessage(message("despawned"));
        }
    }

    private void spawnHologram(RewardTemplate template, Location chestLocation) {
        removeHologramAt(chestLocation);
        if (!getConfig().getBoolean("hologram.enabled", true)) {
            return;
        }

        World world = chestLocation.getWorld();
        if (world == null) {
            return;
        }

        cleanupNearbyHolograms(chestLocation, 8.0, 8.0);
        hologramService.show(template, chestLocation);
    }

    private void cleanupNearbyHolograms(Location location, double horizontalRadius, double verticalRadius) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        for (Entity entity : world.getNearbyEntities(location.clone().add(0.5, 1.5, 0.5), horizontalRadius, verticalRadius, horizontalRadius)) {
            if (entity instanceof ArmorStand && entity.getScoreboardTags().contains(HOLOGRAM_TAG)) {
                entity.remove();
            }
        }
    }

    private void removeHologram() {
        removeHologramAt(activeDrop == null ? null : activeDrop.location());
    }

    private void removeHologramAt(Location cleanupLocation) {
        if (hologramService != null) {
            hologramService.remove();
        }
        if (cleanupLocation != null) {
            cleanupNearbyHologramsSafely(cleanupLocation, 8.0, 8.0);
        }
        if (scheduler != null && scheduler.isFolia()) {
            hologramIds.clear();
            return;
        }
        for (UUID uuid : hologramIds) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        }
        hologramIds.clear();
    }

    private void cleanupOrphanHologramsNearPlayers() {
        if (hologramIds.isEmpty()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                cleanupNearbyHologramsSafely(player.getLocation(), 24.0, 24.0);
            }
            return;
        }
        removeHologram();
    }

    private void cleanupNearbyHologramsSafely(Location location, double horizontalRadius, double verticalRadius) {
        if (scheduler != null && scheduler.isFolia()) {
            scheduler.runAtLocation(location, () -> cleanupNearbyHolograms(location, horizontalRadius, verticalRadius));
            return;
        }
        cleanupNearbyHolograms(location, horizontalRadius, verticalRadius);
    }

    private void updateHologramText() {
        if (activeDrop == null || !getConfig().getBoolean("hologram.enabled", true)) {
            return;
        }

        RewardTemplate template = dataStore.template(activeDrop.templateId()).orElse(null);
        if (template == null) {
            return;
        }

        hologramService.update(template);
    }

    private Color colorFromConfig(String path, String fallback) {
        String value = getConfig().getString(path, fallback).replace("#", "");
        try {
            int red = Integer.parseInt(value.substring(0, 2), 16);
            int green = Integer.parseInt(value.substring(2, 4), 16);
            int blue = Integer.parseInt(value.substring(4, 6), 16);
            return Color.fromRGB(red, green, blue);
        } catch (RuntimeException exception) {
            return Color.fromRGB(212, 115, 239);
        }
    }

    private void playSpawnEffects(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, location.clone().add(0.5, 1.2, 0.5), 90, 0.6, 0.7, 0.6, 0.02);
        world.spawnParticle(Particle.END_ROD, location.clone().add(0.5, 1.1, 0.5), 60, 0.45, 0.6, 0.45, 0.02);
        playConfiguredSound(world, location, "effects.spawn-sound", Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
    }

    private void createBeaconBeam(Location chestLocation, boolean reuseSavedSnapshot) {
        if (!getConfig().getBoolean("beacon-beam.enabled", true)) {
            restoreBeaconBeamBlocks();
            return;
        }

        Optional<Location> beaconLocation = findBeaconBeamLocation(chestLocation);
        if (beaconLocation.isEmpty()) {
            restoreBeaconBeamBlocks();
            getLogger().warning("Khong the tao tru beacon cho ruong thinh: vi tri khong phu hop.");
            return;
        }

        List<BeaconBlockPlacement> placements = beaconPlacements(chestLocation, beaconLocation.get());
        List<TemporaryBlockState> savedStates = dataStore.loadBeaconBlocks();
        boolean reuseSnapshot = reuseSavedSnapshot
                && !savedStates.isEmpty()
                && sameBeaconSnapshot(savedStates, placements);

        if (reuseSnapshot) {
            beaconBlockStates.clear();
            beaconBlockStates.addAll(savedStates);
        } else {
            restoreBeaconBeamBlocks();
            beaconBlockStates.clear();
            for (BeaconBlockPlacement placement : placements) {
                Block block = placement.location().getBlock();
                World world = placement.location().getWorld();
                if (world == null) {
                    continue;
                }
                beaconBlockStates.add(new TemporaryBlockState(
                        world.getName(),
                        block.getX(),
                        block.getY(),
                        block.getZ(),
                        block.getBlockData().getAsString()
                ));
            }
            dataStore.saveBeaconBlocks(beaconBlockStates);
        }

        for (BeaconBlockPlacement placement : placements) {
            placement.location().getBlock().setType(placement.material(), false);
        }
    }

    private Optional<Location> findBeaconBeamLocation(Location chestLocation) {
        World world = chestLocation.getWorld();
        if (world == null) {
            return Optional.empty();
        }
        int hiddenDepth = Math.max(3, getConfig().getInt("beacon-beam.hidden-depth", 3));
        int y = chestLocation.getBlockY() - hiddenDepth;
        if (y - 1 < world.getMinHeight() || chestLocation.getBlockY() + 1 >= world.getMaxHeight()) {
            return Optional.empty();
        }

        List<int[]> offsets = new ArrayList<>();
        addBeaconOffset(offsets, getConfig().getInt("beacon-beam.offset-x", 2), getConfig().getInt("beacon-beam.offset-z", 0));
        addBeaconOffset(offsets, 0, 0);
        addBeaconOffset(offsets, 2, 0);
        addBeaconOffset(offsets, -2, 0);
        addBeaconOffset(offsets, 0, 2);
        addBeaconOffset(offsets, 0, -2);
        addBeaconOffset(offsets, 2, 2);
        addBeaconOffset(offsets, -2, 2);
        addBeaconOffset(offsets, 2, -2);
        addBeaconOffset(offsets, -2, -2);

        for (int[] offset : offsets) {
            Location beaconLocation = new Location(world,
                    chestLocation.getBlockX() + offset[0],
                    y,
                    chestLocation.getBlockZ() + offset[1]);
            if (isBeaconInDropChunk(chestLocation, beaconLocation)) {
                return Optional.of(beaconLocation);
            }
        }
        return Optional.empty();
    }

    private void addBeaconOffset(List<int[]> offsets, int offsetX, int offsetZ) {
        for (int[] offset : offsets) {
            if (offset[0] == offsetX && offset[1] == offsetZ) {
                return;
            }
        }
        offsets.add(new int[] {offsetX, offsetZ});
    }

    private boolean isBeaconInDropChunk(Location chestLocation, Location beaconLocation) {
        if ((chestLocation.getBlockX() >> 4) != (beaconLocation.getBlockX() >> 4)
                || (chestLocation.getBlockZ() >> 4) != (beaconLocation.getBlockZ() >> 4)) {
            return false;
        }
        int localX = Math.floorMod(beaconLocation.getBlockX(), 16);
        int localZ = Math.floorMod(beaconLocation.getBlockZ(), 16);
        return localX >= 1 && localX <= 14 && localZ >= 1 && localZ <= 14;
    }

    private List<BeaconBlockPlacement> beaconPlacements(Location chestLocation, Location beaconLocation) {
        Material baseBlock = materialFromConfig("beacon-beam.base-block", Material.GOLD_BLOCK);
        Material glassBlock = materialFromConfig("beacon-beam.glass", Material.YELLOW_STAINED_GLASS);
        World world = beaconLocation.getWorld();
        List<BeaconBlockPlacement> placements = new ArrayList<>();
        int y = beaconLocation.getBlockY();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                placements.add(new BeaconBlockPlacement(
                        new Location(world, beaconLocation.getBlockX() + x, y - 1, beaconLocation.getBlockZ() + z),
                        baseBlock
                ));
            }
        }
        placements.add(new BeaconBlockPlacement(beaconLocation.clone(), Material.BEACON));
        placements.add(new BeaconBlockPlacement(beaconLocation.clone().add(0, 1, 0), glassBlock));
        boolean chestColumn = chestLocation.getBlockX() == beaconLocation.getBlockX()
                && chestLocation.getBlockZ() == beaconLocation.getBlockZ();
        int clearAboveChest = Math.max(1, getConfig().getInt("beacon-beam.clear-above-chest", 2));
        int topY = Math.min(world.getMaxHeight() - 1, chestLocation.getBlockY() + clearAboveChest);
        int firstAirY = chestColumn ? Math.max(y + 2, chestLocation.getBlockY() + 1) : y + 2;
        for (int airY = firstAirY; airY <= topY; airY++) {
            placements.add(new BeaconBlockPlacement(
                    new Location(world, beaconLocation.getBlockX(), airY, beaconLocation.getBlockZ()),
                    Material.AIR
            ));
        }
        return placements;
    }

    private Material materialFromConfig(String path, Material fallback) {
        Material material = Material.matchMaterial(getConfig().getString(path, fallback.name()).toUpperCase(Locale.ROOT));
        if (material == null || material.isAir()) {
            return fallback;
        }
        return material;
    }

    private boolean sameBeaconSnapshot(List<TemporaryBlockState> states, List<BeaconBlockPlacement> placements) {
        if (states.size() != placements.size()) {
            return false;
        }
        List<String> expectedLocations = placements.stream()
                .map(placement -> locationKey(placement.location()))
                .toList();
        for (TemporaryBlockState state : states) {
            if (!expectedLocations.contains(stateKey(state))) {
                return false;
            }
        }
        return true;
    }

    private void restoreBeaconBeamBlocks() {
        List<TemporaryBlockState> states = beaconBlockStates.isEmpty()
                ? dataStore.loadBeaconBlocks()
                : new ArrayList<>(beaconBlockStates);
        if (states.isEmpty()) {
            return;
        }

        for (TemporaryBlockState state : states) {
            World world = Bukkit.getWorld(state.worldName());
            if (world == null) {
                continue;
            }
            Block block = world.getBlockAt(state.x(), state.y(), state.z());
            try {
                BlockData blockData = Bukkit.createBlockData(state.blockData());
                block.setBlockData(blockData, false);
            } catch (IllegalArgumentException exception) {
                block.setType(Material.AIR, false);
            }
        }
        beaconBlockStates.clear();
        dataStore.saveBeaconBlocks(List.of());
    }

    private void restoreBeaconBeamBlocksSafely() {
        if (scheduler == null || !scheduler.isFolia()) {
            restoreBeaconBeamBlocks();
            return;
        }
        List<TemporaryBlockState> states = beaconBlockStates.isEmpty()
                ? dataStore.loadBeaconBlocks()
                : new ArrayList<>(beaconBlockStates);
        if (states.isEmpty()) {
            return;
        }
        beaconBlockStates.clear();
        beaconBlockStates.addAll(states);
        Optional<Location> location = firstRestorableBeaconLocation(states);
        if (location.isPresent()) {
            scheduler.runAtLocation(location.get(), this::restoreBeaconBeamBlocks);
        } else {
            restoreBeaconBeamBlocks();
        }
    }

    private Optional<Location> firstRestorableBeaconLocation(List<TemporaryBlockState> states) {
        for (TemporaryBlockState state : states) {
            World world = Bukkit.getWorld(state.worldName());
            if (world != null) {
                return Optional.of(new Location(world, state.x(), state.y(), state.z()));
            }
        }
        return Optional.empty();
    }

    private boolean isProtectedRewardBlock(Location location) {
        if (activeDrop != null && sameBlock(location, activeDrop.location())) {
            return true;
        }
        return isBeaconStructureBlock(location);
    }

    private boolean isBeaconStructureBlock(Location location) {
        List<TemporaryBlockState> states = beaconBlockStates.isEmpty()
                ? dataStore.loadBeaconBlocks()
                : beaconBlockStates;
        for (TemporaryBlockState state : states) {
            if (sameBlock(location, state)) {
                return true;
            }
        }
        if (activeDrop == null) {
            return false;
        }
        Optional<Location> beaconLocation = findBeaconBeamLocation(activeDrop.location());
        if (beaconLocation.isEmpty()) {
            return false;
        }
        return beaconPlacements(activeDrop.location(), beaconLocation.get()).stream()
                .anyMatch(placement -> sameBlock(location, placement.location()));
    }

    private boolean sameBlock(Location location, TemporaryBlockState state) {
        return location.getWorld() != null
                && location.getWorld().getName().equals(state.worldName())
                && location.getBlockX() == state.x()
                && location.getBlockY() == state.y()
                && location.getBlockZ() == state.z();
    }

    private String locationKey(Location location) {
        World world = location.getWorld();
        return (world == null ? "" : world.getName()) + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private String stateKey(TemporaryBlockState state) {
        return state.worldName() + ":" + state.x() + ":" + state.y() + ":" + state.z();
    }

    private void playClaimEffects(Player player, Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, location.clone().add(0.5, 1.2, 0.5), 160, 0.9, 1.0, 0.9, 0.06);
        world.spawnParticle(Particle.FIREWORK, location.clone().add(0.5, 1.1, 0.5), 80, 0.8, 0.9, 0.8, 0.04);
        playConfiguredSound(world, location, "effects.claim-sound", Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        int fireworkCount = Math.max(0, getConfig().getInt("effects.fireworks", 4));
        for (int index = 0; index < fireworkCount; index++) {
            scheduler.runGlobalLater(() -> scheduler.runAtLocation(location, () -> spawnFirework(location)), index * 8L);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    private void spawnFirework(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        Firework firework = world.spawn(location.clone().add(0.5, 1.2, 0.5), Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .withColor(colorFromConfig("pvp-zone.particle-color-main", "#D473EF"))
                .withFade(colorFromConfig("pvp-zone.particle-color-accent", "#79E6D9"))
                .with(FireworkEffect.Type.BALL_LARGE)
                .trail(true)
                .flicker(true)
                .build());
        meta.setPower(1);
        firework.setFireworkMeta(meta);
    }

    private void playConfiguredSound(World world, Location location, String path, Sound fallback, float volume, float pitch) {
        Sound sound = fallback;
        try {
            sound = Sound.valueOf(getConfig().getString(path, fallback.name()).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
        }
        world.playSound(location, sound, volume, pitch);
    }

    private void announceSpawn(Location location) {
        broadcastList("messages.spawned-chat", location, null);
        String title = replaceLocation(getConfig().getString("messages.spawned-title"), location);
        String subtitle = replaceLocation(getConfig().getString("messages.spawned-subtitle"), location);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(Text.color(title), Text.color(subtitle), 10, 70, 20);
        }
    }

    private void announceUpcomingDrop(int seconds) {
        for (String line : getConfig().getStringList("messages.upcoming-chat")) {
            Bukkit.broadcastMessage(Text.color(replaceSeconds(line, seconds)));
        }

        String title = replaceSeconds(getConfig().getString("messages.upcoming-title"), seconds);
        String subtitle = replaceSeconds(getConfig().getString("messages.upcoming-subtitle"), seconds);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(Text.color(title), Text.color(subtitle), 10, 60, 20);
            playConfiguredSound(player.getWorld(), player.getLocation(), "effects.upcoming-sound", Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.45f);
        }
    }

    private void announceClaim(Player player, Location location) {
        broadcastList("messages.claimed-chat", location, player.getName());
        String title = replacePlayer(getConfig().getString("messages.claimed-title"), player.getName());
        String subtitle = replacePlayer(getConfig().getString("messages.claimed-subtitle"), player.getName());
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendTitle(Text.color(title), Text.color(subtitle), 10, 70, 20);
        }
    }

    private void broadcastList(String path, Location location, String playerName) {
        for (String line : getConfig().getStringList(path)) {
            String message = replaceLocation(line, location);
            message = replacePlayer(message, playerName);
            Bukkit.broadcastMessage(Text.color(message));
        }
    }

    private String replaceLocation(String message, Location location) {
        if (message == null) {
            return "";
        }
        World world = location.getWorld();
        return message
                .replace("{world}", world == null ? "unknown" : world.getName())
                .replace("{x}", String.valueOf(location.getBlockX()))
                .replace("{y}", String.valueOf(location.getBlockY()))
                .replace("{z}", String.valueOf(location.getBlockZ()));
    }

    private String replacePlayer(String message, String playerName) {
        if (message == null) {
            return "";
        }
        return message.replace("{player}", playerName == null ? "" : playerName);
    }

    private String replaceSeconds(String message, int seconds) {
        if (message == null) {
            return "";
        }
        return message.replace("{seconds}", String.valueOf(seconds));
    }

    String replaceHologram(String message, RewardTemplate template) {
        if (message == null) {
            return "";
        }

        int radius = Math.max(3, getConfig().getInt("pvp-zone.radius", 18));
        return message
                .replace("{template}", template.id())
                .replace("{name}", Text.plain(template.displayName()))
                .replace("{radius}", String.valueOf(radius))
                .replace("{time}", remainingTimeText());
    }

    private String remainingTimeText() {
        if (activeDrop == null) {
            return "--";
        }

        int despawnMinutes = getConfig().getInt("settings.despawn-minutes", 60);
        if (despawnMinutes <= 0) {
            return "khong gioi han";
        }

        long elapsedSeconds = Math.max(0, (System.currentTimeMillis() - activeDrop.spawnedAtMillis()) / 1000L);
        long remainingSeconds = Math.max(0, (despawnMinutes * 60L) - elapsedSeconds);
        long minutes = remainingSeconds / 60L;
        long seconds = remainingSeconds % 60L;
        return minutes + "m " + seconds + "s";
    }

    private void removeActiveDrop(boolean removeBlock) {
        if (activeDrop == null) {
            removeHologramAt(null);
            dataStore.saveActiveDrop(null);
            playersInZone.clear();
            return;
        }
        ActiveDrop drop = activeDrop;
        removeActiveDropInRegion(drop, removeBlock);
        activeDrop = null;
        playersInZone.clear();
        dataStore.saveActiveDrop(null);
    }

    private void removeActiveDropInRegion(ActiveDrop drop, boolean removeBlock) {
        Location location = drop.location();
        if (removeBlock && location.getBlock().getType() == Material.CHEST) {
                location.getBlock().setType(Material.AIR, false);
        }
        restoreBeaconBeamBlocks();
        removeHologramAt(location);
    }

    private void removeActiveDropSafely(boolean removeBlock, Runnable afterRemove) {
        ActiveDrop drop = activeDrop;
        if (drop == null) {
            restoreBeaconBeamBlocksSafely();
            removeHologramAt(null);
            dataStore.saveActiveDrop(null);
            playersInZone.clear();
            if (afterRemove != null) {
                afterRemove.run();
            }
            return;
        }

        activeDrop = null;
        playersInZone.clear();

        scheduler.runAtLocation(drop.location(), () -> {
            try {
                removeActiveDropInRegion(drop, removeBlock);
            } catch (IllegalStateException exception) {
                getLogger().warning("Khong the xoa ruong thinh active: " + exception.getMessage());
            }
            dataStore.saveActiveDrop(null);
            if (afterRemove != null) {
                afterRemove.run();
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (activeDrop == null || event.getClickedBlock() == null) {
            return;
        }
        Location clicked = event.getClickedBlock().getLocation();
        if (!sameBlock(clicked, activeDrop.location())) {
            return;
        }

        event.setCancelled(true);
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        RewardTemplate template = dataStore.template(activeDrop.templateId()).orElse(null);
        if (template == null || template.isEmpty()) {
            player.sendMessage(message("no-template"));
            removeActiveDropSafely(true, null);
            return;
        }

        Location chestLocation = activeDrop.location();
        for (ItemStack reward : template.items()) {
            HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(reward.clone());
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }

        playClaimEffects(player, chestLocation);
        announceClaim(player, chestLocation);
        removeActiveDropSafely(true, null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isProtectedRewardBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(message("prefix") + Text.color("#F0C987Rương thính chỉ có thể nhận bằng chuột phải."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> isProtectedRewardBlock(block.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> isProtectedRewardBlock(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (activeDrop == null || !getConfig().getBoolean("pvp-zone.enabled")) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = findAttackingPlayer(event.getDamager());
        if (attacker == null) {
            return;
        }
        if (isInsideZone(attacker.getLocation()) && isInsideZone(victim.getLocation())) {
            event.setCancelled(false);
        }
    }

    private Player findAttackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private void updateZonePlayers() {
        if (activeDrop == null || !getConfig().getBoolean("pvp-zone.enabled")) {
            playersInZone.clear();
            return;
        }

        Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();
        for (Player player : Bukkit.getOnlinePlayers()) {
            onlinePlayers.add(player.getUniqueId());
            boolean inside = isInsideZone(player.getLocation());
            boolean knownInside = playersInZone.contains(player.getUniqueId());
            if (inside && !knownInside) {
                playersInZone.add(player.getUniqueId());
                player.sendMessage(Text.color(getConfig().getString("pvp-zone.enter-message")));
            } else if (!inside && knownInside) {
                playersInZone.remove(player.getUniqueId());
                player.sendMessage(Text.color(getConfig().getString("pvp-zone.leave-message")));
            }
        }
        playersInZone.removeIf(uuid -> !onlinePlayers.contains(uuid));
    }

    private boolean isInsideZone(Location location) {
        if (activeDrop == null) {
            return false;
        }
        Location center = activeDrop.location();
        if (location.getWorld() == null || center.getWorld() == null || !location.getWorld().equals(center.getWorld())) {
            return false;
        }
        double dx = location.getX() - (center.getBlockX() + 0.5);
        double dz = location.getZ() - (center.getBlockZ() + 0.5);
        int radius = Math.max(3, getConfig().getInt("pvp-zone.radius", 18));
        return dx * dx + dz * dz <= radius * radius;
    }

    private boolean sameBlock(Location first, Location second) {
        return first.getWorld() != null
                && second.getWorld() != null
                && first.getWorld().equals(second.getWorld())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private record BeaconBlockPlacement(Location location, Material material) {
    }

    String message(String key) {
        return Text.color(getConfig().getString("messages." + key, ""));
    }
}
