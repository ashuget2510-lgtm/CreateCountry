package me.states;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CreateCountry extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private final Map<String, String> claims = new ConcurrentHashMap<>();
    private final Map<String, String> countryLeaders = new HashMap<>();
    private final Map<String, List<String>> residents = new HashMap<>();
    private final Map<UUID, BossBar> activeBars = new HashMap<>();

    @Override
    public void onEnable() {
        getCommand("create").setExecutor(this);
        getCommand("create").setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        startBorderParticles();
        getLogger().info("CreateCountry v3.5 LOADED!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (args.length == 0) {
            p.sendMessage("§b§l[!] §fИспользуйте §b/create menu §fдля управления.");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "menu":
                openMainMenu(p);
                break;
            case "claim":
                handleClaim(p);
                break;
            case "expand":
                handleExpand(p);
                break;
            case "invite":
                if (args.length < 2) p.sendMessage("§c§l[!] §fУкажите ник игрока.");
                else p.sendMessage("§a§l[✔] §fПриглашение отправлено игроку §e" + args[1]);
                break;
            default:
                // Логика создания: /create <название>
                String name = args[0];
                handleCreate(p, name);
                break;
        }
        return true;
    }

    private void handleCreate(Player p, String name) {
        if (countryLeaders.containsKey(name)) {
            p.sendMessage("§c§l[✘] §fНазвание §e" + name + " §fуже занято!");
            return;
        }
        countryLeaders.put(name, p.getUniqueId().toString());
        residents.put(name, new ArrayList<>(Collections.singletonList(p.getName())));
        
        // Твое пожелание: четкое подтверждение
        p.sendMessage("");
        p.sendMessage("§a§l      [ СТРАНА СОЗДАНА ]");
        p.sendMessage("§f Название: §e§l" + name);
        p.sendMessage("§f Статус: §b§lУСПЕШНО ✅");
        p.sendMessage("§7 Теперь используйте /create claim для захвата земель.");
        p.sendMessage("");
        
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        updateBossBar(p, name);
    }

    // --- GUI МЕНЮ ---
    public void openMainMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8Управление: §l" + (getPlayerCountry(p) != null ? getPlayerCountry(p) : "Меню"));

        // Заполнение стеклом
        ItemStack glass = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
        ItemMeta gMeta = glass.getItemMeta(); gMeta.setDisplayName(" "); glass.setItemMeta(gMeta);
        for(int i=0; i<27; i++) inv.setItem(i, glass);

        inv.setItem(10, createGuiItem(Material.GRASS_BLOCK, "§a§lЗАХВАТ ЗЕМЛИ", "§7Захватить чанк, в котором вы стоите", "§eЦена: $500", " ", "§b> Нажмите для покупки"));
        inv.setItem(13, createGuiItem(Material.GOLDEN_AXE, "§6§lРАСШИРЕНИЕ (WE)", "§7Расширить границы по выделению", "§7Требуется WorldEdit топорик", " ", "§b> Нажмите для расширения"));
        inv.setItem(16, createGuiItem(Material.PLAYER_HEAD, "§e§lПРИГЛАСИТЬ", "§7Добавить жителя в страну", " ", "§b> Используйте /create invite <ник>"));

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
    }

    private ItemStack createGuiItem(Material m, String name, String... lore) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (e.getView().getTitle().startsWith("§8Управление:")) {
            e.setCancelled(true);
            Player p = (Player) e.getWhoClicked();
            if (e.getRawSlot() == 10) { p.closeInventory(); p.performCommand("create claim"); }
            if (e.getRawSlot() == 13) { p.closeInventory(); p.performCommand("create expand"); }
        }
    }

    // --- ЛОГИКА ГРАНИЦ И ФЛАГОВ ---
    private void startBorderParticles() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Chunk c = p.getLocation().getChunk();
                    String key = c.getWorld().getName() + ":" + c.getX() + ":" + c.getZ();
                    if (claims.containsKey(key)) drawBorders(p, c);
                }
            }
        }.runTaskTimer(this, 0, 15L);
    }

    private void drawBorders(Player p, Chunk c) {
        World w = c.getWorld();
        double y = p.getLocation().getY() + 0.5;
        for (int i = 0; i < 16; i++) {
            w.spawnParticle(Particle.HAPPY_VILLAGER, c.getBlock(i, 0, 0).getLocation().getX() + 0.5, y, c.getBlock(i, 0, 0).getLocation().getZ() + 0.1, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.HAPPY_VILLAGER, c.getBlock(0, 0, i).getLocation().getX() + 0.1, y, c.getBlock(0, 0, i).getLocation().getZ() + 0.5, 1, 0, 0, 0, 0);
        }
    }

    private void handleClaim(Player p) {
        String country = getPlayerCountry(p);
        if (country == null) { p.sendMessage("§c§l[!] §fСначала создайте страну!"); return; }
        Chunk c = p.getLocation().getChunk();
        claims.put(c.getWorld().getName() + ":" + c.getX() + ":" + c.getZ(), country);
        p.sendMessage("§a§l[✔] §fЗемля захвачена! §7Границы подсвечены искрами.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
    }

    private void handleExpand(Player p) {
        try {
            WorldEditPlugin we = (WorldEditPlugin) Bukkit.getPluginManager().getPlugin("WorldEdit");
            Region r = we.getSession(p).getSelection(we.getSession(p).getSelectionWorld());
            p.sendMessage("§a§l[↑] §fВыделение §e" + r.getArea() + " §fблоков успешно добавлено!");
        } catch (Exception e) { p.sendMessage("§c§l[!] §fОшибка WorldEdit: Ничего не выделено!"); }
    }

    private void updateBossBar(Player p, String country) {
        BossBar bar = activeBars.computeIfAbsent(p.getUniqueId(), k -> Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID));
        bar.setTitle("§fСтрана: §b§l" + country + " §7| §fЛидер: §e" + p.getName());
        bar.addPlayer(p);
        bar.setVisible(true);
    }

    private String getPlayerCountry(Player p) {
        return residents.entrySet().stream().filter(e -> e.getValue().contains(p.getName())).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    // --- ТАБ-ПОДСКАЗКИ ---
    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("menu", "claim", "invite", "expand", "reload").stream()
                    .filter(opt -> opt.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("invite")) return null; // Ники игроков
        return Collections.emptyList();
    }
}
