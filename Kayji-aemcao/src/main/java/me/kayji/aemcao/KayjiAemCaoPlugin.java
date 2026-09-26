package me.kayji.aemcao;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.block.Block;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.lang.reflect.Field;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class KayjiAemCaoPlugin extends JavaPlugin implements Listener, TabExecutor {
    private PlayerDataStore dataStore;
    private YamlConfiguration lang;
    private BukkitTask saveTask;
    private BukkitTask requestTask;

    private final Map<UUID, TeleportTask> pendingTeleports = new HashMap<>();
    private final Map<UUID, LinkedHashMap<UUID, TeleportRequest>> incomingRequests = new HashMap<>();
    private final Map<UUID, UUID> outgoingRequests = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("lang.yml", false);
        HomeNameKey.KEY = new org.bukkit.NamespacedKey(this, "home_name");
        HomeGuiActionKey.KEY = new org.bukkit.NamespacedKey(this, "home_gui_action");
        loadLang();

        dataStore = new PlayerDataStore(this);
        dataStore.load();
        registerCommands();
        claimConflictingCommands();
        Bukkit.getPluginManager().registerEvents(this, this);
        startTasks();
    }

    @Override
    public void onDisable() {
        if (saveTask != null) {
            saveTask.cancel();
        }
        if (requestTask != null) {
            requestTask.cancel();
        }
        for (TeleportTask task : pendingTeleports.values()) {
            task.cancel();
        }
        pendingTeleports.clear();
        dataStore.save();
    }

    private void loadLang() {
        lang = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "lang.yml"));
    }

    private void registerCommands() {
        for (String commandName : List.of("tpa", "tpahere", "tpaccept", "tpdeny", "tpacancel", "home", "homes", "sethome", "delhome", "back", "aemcao")) {
            PluginCommand command = getCommand(commandName);
            if (command != null) {
                command.setExecutor(this);
                command.setTabCompleter(this);
            }
        }
    }

    private void startTasks() {
        long saveInterval = Math.max(30, getConfig().getLong("settings.save-interval-seconds", 120)) * 20L;
        saveTask = Bukkit.getScheduler().runTaskTimer(this, () -> dataStore.save(), saveInterval, saveInterval);
        requestTask = Bukkit.getScheduler().runTaskTimer(this, this::expireRequests, 20L, 20L);
    }

    private void claimConflictingCommands() {
        if (!getConfig().getBoolean("conflict-protection.enabled", true)
                || !getConfig().getBoolean("conflict-protection.take-over-other-plugin-commands", true)) {
            return;
        }

        List<String> labels = new ArrayList<>();
        labels.addAll(getConfig().getStringList("conflict-protection.tpa-commands"));
        labels.addAll(getConfig().getStringList("conflict-protection.home-commands"));
        labels.addAll(getConfig().getStringList("conflict-protection.back-commands"));
        claimCommandLabels(labels);
    }

    @SuppressWarnings("unchecked")
    private void claimCommandLabels(Collection<String> labels) {
        try {
            if (!(Bukkit.getCommandMap() instanceof SimpleCommandMap commandMap)) {
                return;
            }
            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            for (String label : labels) {
                PluginCommand ownCommand = getCommand(label);
                if (ownCommand == null) {
                    continue;
                }

                String normalizedLabel = label.toLowerCase(Locale.ROOT);
                knownCommands.entrySet().removeIf(entry -> {
                    String key = entry.getKey().toLowerCase(Locale.ROOT);
                    if (!key.equals(normalizedLabel) && !key.endsWith(":" + normalizedLabel)) {
                        return false;
                    }
                    Command command = entry.getValue();
                    return command instanceof PluginCommand pluginCommand && !pluginCommand.getPlugin().equals(this);
                });

                knownCommands.put(normalizedLabel, ownCommand);
                knownCommands.put(getName().toLowerCase(Locale.ROOT) + ":" + normalizedLabel, ownCommand);
            }
            getLogger().info("Da uu tien lenh TPA/Home/Back cua Kayji-aemcao de tranh xung dot plugin khac.");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            getLogger().warning("Khong the uu tien command map: " + exception.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("aemcao")) {
            return handleAdmin(sender, args);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(message("player-only"));
            return true;
        }

        return switch (name) {
            case "tpa" -> handleTpa(player, args, TeleportRequest.RequestType.TPA);
            case "tpahere" -> handleTpa(player, args, TeleportRequest.RequestType.TPAHERE);
            case "tpaccept" -> handleTpaAccept(player, args);
            case "tpdeny" -> handleTpaDeny(player, args);
            case "tpacancel" -> handleTpaCancel(player, args);
            case "home", "homes" -> handleHome(player, args);
            case "sethome" -> handleSetHome(player, args);
            case "delhome" -> handleDelHome(player, args);
            case "back" -> handleBack(player);
            default -> false;
        };
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("kayjiaemcao.admin")) {
            sender.sendMessage(message("no-permission"));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadLang();
            dataStore.load();
            claimConflictingCommands();
            sender.sendMessage(message("reloaded"));
            return true;
        }
        sender.sendMessage(Text.color("#D473EF/aemcao reload #B9A7F5- tải lại plugin."));
        return true;
    }

    private boolean handleTpa(Player sender, String[] args, TeleportRequest.RequestType type) {
        String permission = type == TeleportRequest.RequestType.TPA ? "kayjiaemcao.tpa" : "kayjiaemcao.tpahere";
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(message("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Text.color("#D473EF/" + (type == TeleportRequest.RequestType.TPA ? "tpa" : "tpahere") + " <player>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(message("player-not-found", "{player}", args[0]));
            return true;
        }
        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage(message("self-target"));
            return true;
        }

        removeOutgoing(sender.getUniqueId(), false);
        long expiresAt = System.currentTimeMillis() + Math.max(5, getConfig().getLong("settings.tpa-expire-seconds", 30)) * 1000L;
        TeleportRequest request = new TeleportRequest(sender.getUniqueId(), target.getUniqueId(), type, expiresAt);
        incomingRequests.computeIfAbsent(target.getUniqueId(), ignored -> new LinkedHashMap<>()).put(sender.getUniqueId(), request);
        outgoingRequests.put(sender.getUniqueId(), target.getUniqueId());

        if (type == TeleportRequest.RequestType.TPA) {
            sender.sendMessage(message("tpa-sent", "{target}", target.getName()));
            target.sendMessage(message("tpa-received", "{sender}", sender.getName()));
        } else {
            sender.sendMessage(message("tpahere-sent", "{target}", target.getName()));
            target.sendMessage(message("tpahere-received", "{sender}", sender.getName()));
        }
        sendTpaActionButtons(target, sender);
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.4f);
        return true;
    }

    private void sendTpaActionButtons(Player target, Player sender) {
        String senderName = sender.getName();
        TextComponent prefix = new TextComponent(Text.color(messageRaw("tpa-click-prefix")));
        TextComponent accept = clickablePart(
                messageRaw("tpa-click-accept"),
                "/tpaccept " + senderName,
                messageRaw("tpa-click-accept-hover").replace("{sender}", senderName)
        );
        TextComponent separator = new TextComponent(Text.color(messageRaw("tpa-click-separator")));
        TextComponent deny = clickablePart(
                messageRaw("tpa-click-deny"),
                "/tpdeny " + senderName,
                messageRaw("tpa-click-deny-hover").replace("{sender}", senderName)
        );

        prefix.addExtra(accept);
        prefix.addExtra(separator);
        prefix.addExtra(deny);
        target.spigot().sendMessage(prefix);
    }

    private TextComponent clickablePart(String text, String command, String hover) {
        TextComponent component = new TextComponent(Text.color(text));
        component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(Text.color(hover)).create()));
        return component;
    }

    private boolean handleTpaAccept(Player target, String[] args) {
        Optional<TeleportRequest> request = findIncoming(target, args);
        if (request.isEmpty()) {
            return true;
        }

        TeleportRequest teleportRequest = request.get();
        Player sender = Bukkit.getPlayer(teleportRequest.senderId());
        if (sender == null) {
            target.sendMessage(message("tpa-no-request"));
            removeRequest(teleportRequest);
            return true;
        }

        removeRequest(teleportRequest);
        target.sendMessage(message("tpa-accepted", "{sender}", sender.getName()));

        if (teleportRequest.type() == TeleportRequest.RequestType.TPA) {
            startTeleport(sender, target.getLocation(), false);
        } else {
            startTeleport(target, sender.getLocation(), false);
        }
        return true;
    }

    private boolean handleTpaDeny(Player target, String[] args) {
        Optional<TeleportRequest> request = findIncoming(target, args);
        if (request.isEmpty()) {
            return true;
        }

        TeleportRequest teleportRequest = request.get();
        Player sender = Bukkit.getPlayer(teleportRequest.senderId());
        removeRequest(teleportRequest);
        target.sendMessage(message("tpa-denied", "{sender}", sender == null ? "unknown" : sender.getName()));
        if (sender != null) {
            sender.sendMessage(message("tpa-denied-sender", "{target}", target.getName()));
        }
        return true;
    }

    private boolean handleTpaCancel(Player sender, String[] args) {
        UUID targetId = outgoingRequests.get(sender.getUniqueId());
        if (targetId == null) {
            sender.sendMessage(message("tpa-no-request"));
            return true;
        }
        Player target = Bukkit.getPlayer(targetId);
        removeOutgoing(sender.getUniqueId(), true);
        sender.sendMessage(message("tpa-cancelled", "{target}", target == null ? "unknown" : target.getName()));
        return true;
    }

    private Optional<TeleportRequest> findIncoming(Player target, String[] args) {
        LinkedHashMap<UUID, TeleportRequest> requests = incomingRequests.get(target.getUniqueId());
        if (requests == null || requests.isEmpty()) {
            target.sendMessage(message("tpa-no-request"));
            return Optional.empty();
        }

        if (args.length > 0) {
            Player sender = Bukkit.getPlayerExact(args[0]);
            if (sender == null || !requests.containsKey(sender.getUniqueId())) {
                target.sendMessage(message("tpa-no-request"));
                return Optional.empty();
            }
            return Optional.of(requests.get(sender.getUniqueId()));
        }

        if (requests.size() > 1) {
            target.sendMessage(message("tpa-multiple"));
            return Optional.empty();
        }
        return Optional.of(requests.values().iterator().next());
    }

    private void expireRequests() {
        if (incomingRequests.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, LinkedHashMap<UUID, TeleportRequest>>> iterator = incomingRequests.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<UUID, LinkedHashMap<UUID, TeleportRequest>> entry = iterator.next();
            Player target = Bukkit.getPlayer(entry.getKey());
            Iterator<TeleportRequest> requestIterator = entry.getValue().values().iterator();
            while (requestIterator.hasNext()) {
                TeleportRequest request = requestIterator.next();
                if (!request.expired()) {
                    continue;
                }
                Player sender = Bukkit.getPlayer(request.senderId());
                requestIterator.remove();
                outgoingRequests.remove(request.senderId());
                if (sender != null) {
                    sender.sendMessage(message("tpa-expired-sender", "{target}", target == null ? "unknown" : target.getName()));
                }
                if (target != null) {
                    target.sendMessage(message("tpa-expired-target", "{sender}", sender == null ? "unknown" : sender.getName()));
                }
            }
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    private void removeRequest(TeleportRequest request) {
        LinkedHashMap<UUID, TeleportRequest> requests = incomingRequests.get(request.targetId());
        if (requests != null) {
            requests.remove(request.senderId());
            if (requests.isEmpty()) {
                incomingRequests.remove(request.targetId());
            }
        }
        outgoingRequests.remove(request.senderId());
    }

    private void removeOutgoing(UUID senderId, boolean notifyTarget) {
        UUID targetId = outgoingRequests.remove(senderId);
        if (targetId == null) {
            return;
        }
        LinkedHashMap<UUID, TeleportRequest> requests = incomingRequests.get(targetId);
        if (requests != null) {
            requests.remove(senderId);
            if (requests.isEmpty()) {
                incomingRequests.remove(targetId);
            }
        }
        if (notifyTarget) {
            Player target = Bukkit.getPlayer(targetId);
            Player sender = Bukkit.getPlayer(senderId);
            if (target != null && sender != null) {
                target.sendMessage(message("tpa-cancelled", "{target}", sender.getName()));
            }
        }
    }

    private boolean handleHome(Player player, String[] args) {
        if (!player.hasPermission("kayjiaemcao.home")) {
            player.sendMessage(message("no-permission"));
            return true;
        }
        if (args.length == 0) {
            openHomeGui(player);
            return true;
        }

        Optional<HomeData> home = dataStore.home(player.getUniqueId(), args[0]);
        if (home.isEmpty()) {
            player.sendMessage(message("home-not-found", "{name}", args[0]));
            return true;
        }
        teleportToHome(player, home.get());
        return true;
    }

    private boolean handleSetHome(Player player, String[] args) {
        if (!player.hasPermission("kayjiaemcao.sethome")) {
            player.sendMessage(message("no-permission"));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Text.color("#D473EF/sethome <name>"));
            return true;
        }

        int limit = homeLimit(player);
        boolean replacing = dataStore.home(player.getUniqueId(), args[0]).isPresent();
        if (!replacing && dataStore.homeCount(player.getUniqueId()) >= limit) {
            player.sendMessage(message("home-limit", "{limit}", String.valueOf(limit)));
            return true;
        }

        String name = sanitizeHomeName(args[0]);
        dataStore.setHome(player.getUniqueId(), name, player.getLocation());
        dataStore.save();
        player.sendMessage(message("home-set", "{name}", name));
        return true;
    }

    private boolean handleDelHome(Player player, String[] args) {
        if (!player.hasPermission("kayjiaemcao.delhome")) {
            player.sendMessage(message("no-permission"));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Text.color("#D473EF/delhome <name>"));
            return true;
        }
        if (!dataStore.deleteHome(player.getUniqueId(), args[0])) {
            player.sendMessage(message("home-not-found", "{name}", args[0]));
            return true;
        }
        dataStore.save();
        player.sendMessage(message("home-deleted", "{name}", args[0]));
        return true;
    }

    private boolean handleBack(Player player) {
        if (!player.hasPermission("kayjiaemcao.back")) {
            player.sendMessage(message("no-permission"));
            return true;
        }
        Optional<Location> back = dataStore.back(player.getUniqueId());
        if (back.isEmpty()) {
            player.sendMessage(message("back-none"));
            return true;
        }
        startTeleport(player, back.get(), getConfig().getBoolean("back.clear-after-use", false));
        return true;
    }

    private void teleportToHome(Player player, HomeData home) {
        startTeleport(player, home.location(), false);
    }

    private void startTeleport(Player player, Location destination, boolean clearBackAfterUse) {
        TeleportTask existing = pendingTeleports.remove(player.getUniqueId());
        if (existing != null) {
            existing.cancel();
        }

        int delay = player.hasPermission("kayjiaemcao.bypass.delay")
                ? 0
                : Math.max(0, getConfig().getInt("settings.teleport-delay-seconds", 3));
        if (delay <= 0) {
            completeTeleport(player, destination, clearBackAfterUse);
            return;
        }

        player.sendMessage(message("teleport-start", "{seconds}", String.valueOf(delay)));
        BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
            pendingTeleports.remove(player.getUniqueId());
            completeTeleport(player, destination, clearBackAfterUse);
        }, delay * 20L);
        pendingTeleports.put(player.getUniqueId(), new TeleportTask(player, task));
    }

    private void completeTeleport(Player player, Location destination, boolean clearBackAfterUse) {
        if (!player.isOnline()) {
            return;
        }
        player.teleport(destination);
        player.sendMessage(message("teleport-success"));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
        if (clearBackAfterUse) {
            dataStore.clearBack(player.getUniqueId());
            dataStore.save();
            player.sendMessage(message("back-cleared"));
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!getConfig().getBoolean("settings.cancel-on-move", true)
                || event.getPlayer().hasPermission("kayjiaemcao.bypass.move-cancel")) {
            return;
        }
        TeleportTask task = pendingTeleports.get(event.getPlayer().getUniqueId());
        if (task == null) {
            return;
        }
        if (!sameWorld(event.getTo(), task.from())) {
            cancelTeleport(event.getPlayer());
            return;
        }
        double maxDistance = Math.max(0, getConfig().getDouble("settings.move-cancel-distance", 0.15));
        if (event.getTo().distanceSquared(task.from()) > maxDistance * maxDistance) {
            cancelTeleport(event.getPlayer());
        }
    }

    private void cancelTeleport(Player player) {
        TeleportTask task = pendingTeleports.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            player.sendMessage(message("teleport-cancel-move"));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!getConfig().getBoolean("back.only-after-death", true)) {
            return;
        }
        Player player = event.getEntity();
        dataStore.setBack(player.getUniqueId(), player.getLocation());
        dataStore.save();
        player.sendMessage(message("back-set"));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        TeleportTask task = pendingTeleports.remove(event.getPlayer().getUniqueId());
        if (task != null) {
            task.cancel();
        }
        removeOutgoing(event.getPlayer().getUniqueId(), false);
        incomingRequests.remove(event.getPlayer().getUniqueId());
    }

    private static final int[] HOME_GUI_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int HOME_GUI_SIZE = 54;
    private static final int HOMES_PER_PAGE = HOME_GUI_SLOTS.length;

    private void openHomeGui(Player player) {
        openHomeGui(player, 0);
    }

    private void openHomeGui(Player player, int page) {
        List<HomeData> homes = new ArrayList<>(dataStore.homes(player.getUniqueId()));
        homes.sort(Comparator.comparing(HomeData::name, String.CASE_INSENSITIVE_ORDER));

        if (homes.isEmpty()) {
            player.sendMessage(message("home-empty"));
        }

        int totalPages = Math.max(1, (homes.size() + HOMES_PER_PAGE - 1) / HOMES_PER_PAGE);
        int safePage = Math.max(0, Math.min(page, totalPages - 1));

        String title = getConfig().getString("gui.home-title", "#D473EFDanh sách home");
        if (totalPages > 1) {
            title = title + " #5A536F(" + (safePage + 1) + "/" + totalPages + ")";
        }
        Inventory inventory = Bukkit.createInventory(new HomeGuiHolder(safePage), HOME_GUI_SIZE, Text.color(title));

        fillHomeGuiBorder(inventory);

        int start = safePage * HOMES_PER_PAGE;
        for (int index = 0; index < HOMES_PER_PAGE && start + index < homes.size(); index++) {
            inventory.setItem(HOME_GUI_SLOTS[index], homeItem(homes.get(start + index)));
        }

        inventory.setItem(48, guiActionItem("prev", Material.ARROW, "home-gui-prev", "home-gui-prev-lore"));
        inventory.setItem(49, infoItem(player));
        inventory.setItem(50, guiActionItem("set", Material.WRITABLE_BOOK, "home-gui-set", "home-gui-set-lore"));
        inventory.setItem(51, guiActionItem("close", Material.BARRIER, "home-gui-close", "home-gui-close-lore"));
        inventory.setItem(52, guiActionItem("next", Material.ARROW, "home-gui-next", "home-gui-next-lore"));

        if (safePage <= 0) {
            inventory.setItem(48, borderPane());
        }
        if (safePage >= totalPages - 1) {
            inventory.setItem(52, borderPane());
        }

        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
    }

    private void fillHomeGuiBorder(Inventory inventory) {
        ItemStack pane = borderPane();
        for (int slot = 0; slot < 9; slot++) {
            inventory.setItem(slot, pane);
        }
        for (int slot = 45; slot < 54; slot++) {
            if (slot != 48 && slot != 49 && slot != 50 && slot != 51 && slot != 52) {
                inventory.setItem(slot, pane);
            }
        }
        for (int slot : new int[]{9, 17, 18, 26, 27, 35, 36, 44}) {
            inventory.setItem(slot, pane);
        }
    }

    private ItemStack borderPane() {
        Material material = Material.matchMaterial(getConfig().getString("gui.filler", "GRAY_STAINED_GLASS_PANE"));
        ItemStack item = new ItemStack(material == null ? Material.GRAY_STAINED_GLASS_PANE : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(" "));
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack guiActionItem(String action, Material fallback, String nameKey, String loreKey) {
        ItemStack item = new ItemStack(fallback);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(messageRaw(nameKey)));
            String lore = messageRaw(loreKey);
            if (!lore.isBlank()) {
                meta.setLore(List.of(Text.color(lore)));
            }
            meta.getPersistentDataContainer().set(HomeGuiActionKey.KEY, HomeGuiActionKey.TYPE, action);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof HomeGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) {
            return;
        }

        String action = clicked.getItemMeta().getPersistentDataContainer().get(HomeGuiActionKey.KEY, HomeGuiActionKey.TYPE);
        if (action != null) {
            handleHomeGuiAction(player, holder, action);
            return;
        }

        String homeName = clicked.getItemMeta().getPersistentDataContainer().get(HomeNameKey.KEY, HomeNameKey.TYPE);
        if (homeName == null) {
            return;
        }

        if (event.getClick() == ClickType.SHIFT_RIGHT) {
            handleHomeGuiDelete(player, holder.page(), homeName);
            return;
        }

        player.closeInventory();
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 1.5f);
        dataStore.home(player.getUniqueId(), homeName).ifPresent(home -> teleportToHome(player, home));
    }

    private void handleHomeGuiAction(Player player, HomeGuiHolder holder, String action) {
        switch (action) {
            case "close" -> {
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.9f);
            }
            case "set" -> {
                player.closeInventory();
                player.sendMessage(message("home-gui-set-hint"));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.1f);
            }
            case "prev" -> {
                if (holder.page() > 0) {
                    openHomeGui(player, holder.page() - 1);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
                }
            }
            case "next" -> openHomeGui(player, holder.page() + 1);
            default -> {
            }
        }
    }

    private void handleHomeGuiDelete(Player player, int page, String homeName) {
        if (!player.hasPermission("kayjiaemcao.delhome")) {
            player.sendMessage(message("no-permission"));
            return;
        }
        if (!dataStore.deleteHome(player.getUniqueId(), homeName)) {
            player.sendMessage(message("home-not-found", "{name}", homeName));
            return;
        }
        dataStore.save();
        player.sendMessage(message("home-deleted", "{name}", homeName));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.7f, 0.8f);
        openHomeGui(player, page);
    }

    private ItemStack homeItem(HomeData home) {
        boolean safe = isSafeForDisplay(home.location());
        Material material = Material.matchMaterial(getConfig().getString(safe ? "gui.safe-icon" : "gui.unsafe-icon", safe ? "LIME_BED" : "RED_BED"));
        ItemStack item = new ItemStack(material == null ? Material.LIME_BED : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("#D473EF✦ #F0C987" + home.name()));
            Location location = home.location();
            World world = location.getWorld();
            String worldName = world == null ? "?" : world.getName();
            meta.setLore(List.of(
                    Text.color(" "),
                    Text.color("#B9A7F5▸ #F0C987" + worldName),
                    Text.color("#B9A7F5▸ #F0C987" + location.getBlockX() + "#5A536F, #F0C987" + location.getBlockY() + "#5A536F, #F0C987" + location.getBlockZ()),
                    Text.color(" "),
                    Text.color(safe ? messageRaw("home-safe") : messageRaw("home-unsafe")),
                    Text.color(" "),
                    Text.color(messageRaw("home-gui-click-teleport")),
                    Text.color(messageRaw("home-gui-click-delete"))
            ));
            meta.getPersistentDataContainer().set(HomeNameKey.KEY, HomeNameKey.TYPE, home.name());
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack infoItem(Player player) {
        Material material = Material.matchMaterial(getConfig().getString("gui.info-icon", "BOOK"));
        ItemStack item = new ItemStack(material == null ? Material.BOOK : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            int used = dataStore.homeCount(player.getUniqueId());
            int limit = homeLimit(player);
            meta.setDisplayName(Text.color(messageRaw("home-gui-info-title")));
            meta.setLore(List.of(
                    Text.color(" "),
                    Text.color(messageRaw("home-gui-info")
                            .replace("{used}", String.valueOf(used))
                            .replace("{limit}", String.valueOf(limit))),
                    Text.color(" "),
                    Text.color(messageRaw("home-gui-info-hint"))
            ));
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private int homeLimit(Player player) {
        int limit = getConfig().getInt("homes.default-limit", 3);
        ConfigurationSection section = getConfig().getConfigurationSection("homes.permission-limits");
        if (section == null) {
            return limit;
        }
        for (String permission : section.getKeys(false)) {
            if (player.hasPermission(permission)) {
                limit = Math.max(limit, section.getInt(permission));
            }
        }
        return limit;
    }

    /** Chỉ dùng để hiển thị trên GUI — không chặn dịch chuyển. */
    private boolean isSafeForDisplay(Location location) {
        if (location.getWorld() == null) {
            return false;
        }
        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        if (isDangerousBlock(feet) || isDangerousBlock(head)) {
            return false;
        }

        Block below = world.getBlockAt(x, y - 1, z);
        if (isDangerousBlock(below)) {
            return false;
        }
        if (below.getType().isAir()) {
            Block twoBelow = world.getBlockAt(x, y - 2, z);
            return !twoBelow.getType().isAir();
        }
        return true;
    }

    private boolean isDangerousBlock(Block block) {
        return switch (block.getType()) {
            case LAVA, FIRE, SOUL_FIRE, MAGMA_BLOCK, CACTUS, WITHER_ROSE, SWEET_BERRY_BUSH, POWDER_SNOW -> true;
            default -> false;
        };
    }

    private boolean sameWorld(Location first, Location second) {
        return first.getWorld() != null && first.getWorld().equals(second.getWorld());
    }

    private String sanitizeHomeName(String input) {
        String cleaned = input.replaceAll("[^A-Za-z0-9_-]", "");
        if (cleaned.isBlank()) {
            return "home";
        }
        return cleaned.substring(0, Math.min(cleaned.length(), 24));
    }

    private String message(String key, String... replacements) {
        String message = messageRaw(key);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }
        return Text.color(message);
    }

    private String messageRaw(String key) {
        return lang.getString(key, "");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("aemcao") && args.length == 1) {
            return List.of("reload");
        }
        if ((name.equals("tpa") || name.equals("tpahere")) && args.length == 1) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(playerName -> playerName.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if ((name.equals("home") || name.equals("delhome")) && sender instanceof Player player && args.length == 1) {
            return dataStore.homes(player.getUniqueId()).stream().map(HomeData::name)
                    .filter(homeName -> homeName.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if ((name.equals("tpaccept") || name.equals("tpdeny")) && sender instanceof Player player && args.length == 1) {
            LinkedHashMap<UUID, TeleportRequest> requests = incomingRequests.get(player.getUniqueId());
            if (requests == null) {
                return List.of();
            }
            return requests.keySet().stream()
                    .map(Bukkit::getOfflinePlayer)
                    .map(OfflinePlayer::getName)
                    .filter(playerName -> playerName != null && playerName.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
        return List.of();
    }
}
