package me.kayji.simplerewardzone;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

final class RewardGuiManager implements Listener {
    private final KayjiSimpleRewardZone plugin;
    private final RewardDataStore dataStore;

    RewardGuiManager(KayjiSimpleRewardZone plugin, RewardDataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
    }

    void openMain(Player player) {
        Inventory inventory = Bukkit.createInventory(
                new RewardGuiHolder(RewardGuiHolder.Type.MAIN, null),
                45,
                Text.color(plugin.getConfig().getString("gui.main-title"))
        );

        inventory.setItem(20, item(Material.CHEST, "#D473EFDanh sach ruong", List.of(
                "#B9A7F5Them, xoa va sua do trong tung ruong."
        )));
        inventory.setItem(22, item(Material.ENDER_CHEST, "#8EDFD1Tha ruong ngay", List.of(
                "#B9A7F5Random mot template va tao ruong tren world."
        )));
        inventory.setItem(24, item(Material.COMPARATOR, "#F0C987Cai dat", List.of(
                "#B9A7F5Chinh thoi gian, ban kinh va co che auto."
        )));
        inventory.setItem(40, item(Material.LIME_DYE, "#8EDFD1Tai lai", List.of(
                "#B9A7F5Reload config.yml va data.yml."
        )));

        player.openInventory(inventory);
    }

    void openTemplates(Player player) {
        Inventory inventory = Bukkit.createInventory(
                new RewardGuiHolder(RewardGuiHolder.Type.TEMPLATES, null),
                54,
                Text.color(plugin.getConfig().getString("gui.templates-title"))
        );

        int slot = 0;
        for (RewardTemplate template : dataStore.templates()) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot++, item(template.icon(), template.displayName(), List.of(
                    "#B9A7F5ID: #F0C987" + template.id(),
                    "#B9A7F5Vat pham: #F0C987" + template.items().size(),
                    "#8EDFD1Left-click de sua do.",
                    "#F07B93Right-click de xoa."
            )));
        }

        inventory.setItem(48, item(Material.ARROW, "#F0C987Quay lai", List.of()));
        inventory.setItem(49, item(Material.CHEST, "#8EDFD1Tao ruong moi", List.of(
                "#B9A7F5Tao template trong data.yml."
        )));
        player.openInventory(inventory);
    }

    void openEditor(Player player, RewardTemplate template) {
        Inventory inventory = Bukkit.createInventory(
                new RewardGuiHolder(RewardGuiHolder.Type.EDITOR, template.id()),
                54,
                Text.color(plugin.getConfig().getString("gui.editor-title-prefix") + template.id())
        );

        List<ItemStack> items = template.items();
        for (int index = 0; index < Math.min(items.size(), inventory.getSize()); index++) {
            if (index == 49) {
                continue;
            }
            inventory.setItem(index, items.get(index));
        }
        inventory.setItem(49, item(Material.ARROW, "#F0C987Quay lai", List.of(
                "#B9A7F5Luu ruong va quay lai danh sach."
        )));
        player.openInventory(inventory);
    }

    void openSpawnSelect(Player player) {
        Inventory inventory = Bukkit.createInventory(
                new RewardGuiHolder(RewardGuiHolder.Type.SPAWN_SELECT, null),
                54,
                Text.color("#D473EFChon ruong thinh")
        );

        inventory.setItem(0, item(Material.ENDER_CHEST, "#8EDFD1Tha random ngay", List.of(
                "#B9A7F5Chon ngau nhien mot ruong co vat pham."
        )));

        int slot = 9;
        for (RewardTemplate template : dataStore.templates()) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot++, item(template.icon(), template.displayName(), List.of(
                    "#B9A7F5ID: #F0C987" + template.id(),
                    "#B9A7F5Vat pham: #F0C987" + template.items().size(),
                    "#8EDFD1Left-click de tha ruong nay ngay.",
                    "#D473EFRight-click de chon lam auto spawn."
            )));
        }

        inventory.setItem(47, item(Material.NETHER_STAR, "#D473EFKieu auto: random", List.of(
                "#B9A7F5Auto spawn se random neu bat o cai dat.",
                "#8EDFD1Click de chuyen ve random."
        )));
        inventory.setItem(51, selectedTemplateItem());
        inventory.setItem(49, item(Material.ARROW, "#F0C987Quay lai", List.of()));
        player.openInventory(inventory);
    }

    void openSettings(Player player) {
        FileConfiguration config = plugin.getConfig();
        Inventory inventory = Bukkit.createInventory(
                new RewardGuiHolder(RewardGuiHolder.Type.SETTINGS, null),
                45,
                Text.color(config.getString("gui.settings-title"))
        );

        inventory.setItem(10, item(config.getBoolean("settings.auto-spawn") ? Material.LIME_DYE : Material.GRAY_DYE,
                "#8EDFD1Auto spawn",
                List.of("#B9A7F5Trang thai: #F0C987" + enabled(config.getBoolean("settings.auto-spawn")),
                        "#8EDFD1Click de bat/tat.")));
        inventory.setItem(12, item(Material.CLOCK,
                "#D473EFKhoang cach spawn",
                List.of("#B9A7F5Hien tai: #F0C987" + config.getInt("settings.spawn-interval-minutes") + " phut",
                        "#8EDFD1Left +5 phut, Right -5 phut.")));
        inventory.setItem(14, item(Material.TARGET,
                "#D473EFBan kinh PvP",
                List.of("#B9A7F5Hien tai: #F0C987" + config.getInt("pvp-zone.radius") + " block",
                        "#8EDFD1Left +1, Right -1.")));
        inventory.setItem(16, item(Material.HOPPER,
                "#D473EFThoi gian ton tai",
                List.of("#B9A7F5Hien tai: #F0C987" + config.getInt("settings.despawn-minutes") + " phut",
                        "#8EDFD1Left +5 phut, Right -5 phut.")));
        inventory.setItem(20, item(Material.BELL,
                "#D473EFThong bao truoc",
                List.of("#B9A7F5Hien tai: #F0C987" + config.getInt("settings.pre-announce-seconds") + " giay",
                        "#8EDFD1Left +5 giay, Right -5 giay.")));
        inventory.setItem(22, item(Material.ENDER_CHEST, "#8EDFD1Tha ruong ngay", List.of(
                "#B9A7F5Mo menu chon random hoac mot ruong cu the."
        )));
        inventory.setItem(24, item(config.getBoolean("settings.replace-active-drop") ? Material.LIME_DYE : Material.GRAY_DYE,
                "#F0C987Thay ruong dang active",
                List.of("#B9A7F5Trang thai: #F0C987" + enabled(config.getBoolean("settings.replace-active-drop")),
                        "#8EDFD1Click de bat/tat.")));
        inventory.setItem(30, selectedTemplateItem());
        inventory.setItem(32, item(isSelectedSpawnMode() ? Material.CHEST : Material.ENDER_CHEST,
                "#D473EFKieu auto spawn",
                List.of("#B9A7F5Hien tai: #F0C987" + (isSelectedSpawnMode() ? "Ruong da chon" : "Random"),
                        "#8EDFD1Click de doi random/chon ruong.",
                        "#B9A7F5Right-click de mo danh sach chon.")));
        inventory.setItem(40, item(Material.ARROW, "#F0C987Quay lai", List.of()));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof RewardGuiHolder holder)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            if (holder.type() != RewardGuiHolder.Type.EDITOR) {
                event.setCancelled(true);
            }
            return;
        }

        if (holder.type() == RewardGuiHolder.Type.EDITOR) {
            if (event.getSlot() == 49) {
                event.setCancelled(true);
                openTemplates(player);
            }
            return;
        }

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        switch (holder.type()) {
            case MAIN -> handleMain(player, event.getSlot());
            case TEMPLATES -> handleTemplates(player, event.getSlot(), event.getClick());
            case SPAWN_SELECT -> handleSpawnSelect(player, event.getSlot(), event.getClick());
            case SETTINGS -> handleSettings(player, event.getSlot(), event.isRightClick());
            default -> {
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof RewardGuiHolder holder)) {
            return;
        }
        if (holder.type() != RewardGuiHolder.Type.EDITOR) {
            return;
        }

        dataStore.template(holder.templateId()).ifPresent(template -> {
            List<ItemStack> items = new ArrayList<>();
            ItemStack[] contents = event.getInventory().getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                if (slot == 49) {
                    continue;
                }
                ItemStack item = contents[slot];
                if (item != null && !item.getType().isAir()) {
                    items.add(item.clone());
                }
            }
            template.setItems(items);
            dataStore.save();
            player.sendMessage(plugin.message("prefix") + Text.color("#8EDFD1Da luu ruong #F0C987" + template.id() + "#8EDFD1."));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
        });
    }

    private void handleMain(Player player, int slot) {
        if (slot == 20) {
            openTemplates(player);
        } else if (slot == 22) {
            openSpawnSelect(player);
        } else if (slot == 24) {
            openSettings(player);
        } else if (slot == 40) {
            plugin.reloadAll();
            openMain(player);
            player.sendMessage(plugin.message("reloaded"));
        }
    }

    private void handleSpawnSelect(Player player, int slot, ClickType clickType) {
        if (slot == 49) {
            openMain(player);
            return;
        }
        if (slot == 47) {
            setSpawnMode("random", null);
            openSpawnSelect(player);
            return;
        }
        if (slot == 51) {
            openSettings(player);
            return;
        }
        if (slot == 0) {
            player.closeInventory();
            plugin.spawnRandomDrop(player, true);
            return;
        }
        if (slot < 9 || slot >= 45) {
            return;
        }

        RewardTemplate template = dataStore.templates().stream().skip(slot - 9L).findFirst().orElse(null);
        if (template == null) {
            return;
        }
        if (clickType.isRightClick()) {
            setSpawnMode("selected", template.id());
            openSpawnSelect(player);
            player.sendMessage(plugin.message("prefix") + Text.color("#8EDFD1Da chon auto spawn ruong #F0C987" + template.id() + "#8EDFD1."));
            return;
        }

        player.closeInventory();
        plugin.spawnTemplateDrop(player, true, template.id());
    }

    private void handleTemplates(Player player, int slot, ClickType clickType) {
        if (slot == 48) {
            openMain(player);
            return;
        }
        if (slot == 49) {
            RewardTemplate template = dataStore.createTemplate();
            openEditor(player, template);
            return;
        }
        if (slot < 0 || slot >= 45) {
            return;
        }

        RewardTemplate template = dataStore.templates().stream().skip(slot).findFirst().orElse(null);
        if (template == null) {
            return;
        }
        if (clickType.isRightClick()) {
            dataStore.deleteTemplate(template.id());
            openTemplates(player);
            player.sendMessage(plugin.message("prefix") + Text.color("#F0C987Da xoa ruong #D473EF" + template.id() + "#F0C987."));
        } else {
            openEditor(player, template);
        }
    }

    private void handleSettings(Player player, int slot, boolean rightClick) {
        FileConfiguration config = plugin.getConfig();
        if (slot == 10) {
            config.set("settings.auto-spawn", !config.getBoolean("settings.auto-spawn"));
        } else if (slot == 12) {
            adjust("settings.spawn-interval-minutes", rightClick ? -5 : 5, 1, 1440);
        } else if (slot == 14) {
            adjust("pvp-zone.radius", rightClick ? -1 : 1, 3, 100);
        } else if (slot == 16) {
            adjust("settings.despawn-minutes", rightClick ? -5 : 5, 1, 1440);
        } else if (slot == 20) {
            adjust("settings.pre-announce-seconds", rightClick ? -5 : 5, 0, 3600);
        } else if (slot == 22) {
            openSpawnSelect(player);
            return;
        } else if (slot == 24) {
            config.set("settings.replace-active-drop", !config.getBoolean("settings.replace-active-drop"));
        } else if (slot == 30) {
            openSpawnSelect(player);
            return;
        } else if (slot == 32) {
            if (rightClick) {
                openSpawnSelect(player);
                return;
            }
            if (isSelectedSpawnMode()) {
                config.set("settings.spawn-template-mode", "random");
            } else {
                config.set("settings.spawn-template-mode", "selected");
            }
        } else if (slot == 40) {
            openMain(player);
            return;
        } else {
            return;
        }

        plugin.saveConfig();
        plugin.restartAutoSpawnTask();
        openSettings(player);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    private void setSpawnMode(String mode, String templateId) {
        FileConfiguration config = plugin.getConfig();
        config.set("settings.spawn-template-mode", mode);
        if (templateId != null) {
            config.set("settings.spawn-template-id", templateId);
        }
        plugin.saveConfig();
        plugin.restartAutoSpawnTask();
    }

    private boolean isSelectedSpawnMode() {
        return plugin.getConfig().getString("settings.spawn-template-mode", "random").equalsIgnoreCase("selected");
    }

    private ItemStack selectedTemplateItem() {
        FileConfiguration config = plugin.getConfig();
        String templateId = config.getString("settings.spawn-template-id", "default");
        String mode = isSelectedSpawnMode() ? "Ruong da chon" : "Random";
        Material material = isSelectedSpawnMode() ? Material.CHEST : Material.ENDER_CHEST;
        String displayName = dataStore.template(templateId)
                .map(RewardTemplate::displayName)
                .orElse("#F0C987" + templateId);
        return item(material, "#8EDFD1Ruong auto dang chon", List.of(
                "#B9A7F5Kieu: #F0C987" + mode,
                "#B9A7F5ID: #F0C987" + templateId,
                "#B9A7F5Ten: " + displayName,
                "#8EDFD1Mo danh sach de doi ruong."
        ));
    }

    private void adjust(String path, int delta, int min, int max) {
        FileConfiguration config = plugin.getConfig();
        int next = Math.max(min, Math.min(max, config.getInt(path) + delta));
        config.set(path, next);
    }

    private String enabled(boolean value) {
        return value ? "Bat" : "Tat";
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            meta.setLore(lore.stream().map(Text::color).toList());
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }
}
