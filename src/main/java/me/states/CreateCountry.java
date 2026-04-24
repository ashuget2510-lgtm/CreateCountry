package me.states;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CreateCountry extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    public static final Map<String, String> claims = new ConcurrentHashMap<>();
    public static final Map<String, Color> countryColors = new HashMap<>();
    public static final Map<String, UUID> leaders = new HashMap<>();
    
    public static Economy econ = null;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Vault не найден! Экономические функции отключены.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        loadData();
        
        getCommand("create").setExecutor(this);
        getCommand("create").setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        
        getLogger().info("CreateCountry v1.6.5 для 1.21.x успешно запущен!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    @Override
    public void onDisable() {
        saveData();
    }

    // --- ЗАЩИТА ТЕРРИТОРИИ ---
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (!canInteract(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§c[!] Вы не можете строить на территории чужой страны!");
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (!canInteract(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§c[!] Вы не можете строить на территории чужой страны!");
        }
    }

    private boolean canInteract(Player p, Location loc) {
        String chunkKey = loc.getWorld().getName() + ":" + loc.getChunk().getX() + ":" + loc.getChunk().getZ();
        if (!claims.containsKey(chunkKey)) return true;
        
        String countryName = claims.get(chunkKey);
        String playerCountry = getPlayerCountry(p);
        
        return countryName.equalsIgnoreCase(playerCountry);
    }

    // --- КОМАНДЫ ---
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        if (args.length == 0) {
            p.sendMessage("§b--- [ CreateCountry ] ---");
            p.sendMessage("§f/create <имя> §7- Создать страну");
            p.sendMessage("§f/create claim §7- Захватить чанк (§e1000$§7)");
            p.sendMessage("§f/create map §7- Получить полит. карту");
            return true;
        }

        // Создание страны
        if (args.length == 1 && !isSubCommand(args[0])) {
            String name = args[0];
            if (getPlayerCountry(p) != null) { p.sendMessage("§c[!] Вы уже лидер страны."); return true; }
            if (leaders.containsKey(name)) { p.sendMessage("§c[!] Это имя занято."); return true; }

            leaders.put(name, p.getUniqueId());
            countryColors.put(name, Color.fromRGB(new Random().nextInt(255), new Random().nextInt(255), new Random().nextInt(255)));
            p.sendMessage("§a[✔] §fСтрана §l" + name + " §fсоздана!");
            saveData();
            return true;
        }

        // Захват чанка
        if (args[0].equalsIgnoreCase("claim")) {
            String country = getPlayerCountry(p);
            if (country == null) { p.sendMessage("§c[!] Вы должны быть лидером страны."); return true; }
            
            double cost = 1000.0;
            if (econ.getBalance(p) < cost) {
                p.sendMessage("§c[!] Недостаточно денег. Нужно §e" + cost + "$");
                return true;
            }

            String key = p.getWorld().getName() + ":" + p.getLocation().getChunk().getX() + ":" + p.getLocation().getChunk().getZ();
            if (claims.containsKey(key)) {
                p.sendMessage("§c[!] Это уже занято страной " + claims.get(key));
                return true;
            }

            econ.withdrawPlayer(p, cost);
            claims.put(key, country);
            p.sendMessage("§a[✔] §fЧанк захвачен! Списано §e" + cost + "$");
            saveData();
            return true;
        }

        // Выдача карты
        if (args[0].equalsIgnoreCase("map")) {
            ItemStack map = new ItemStack(Material.FILLED_MAP);
            MapMeta meta = (MapMeta) map.getItemMeta();
            MapView view = Bukkit.createMap(p.getWorld());
            view.getRenderers().clear();
            view.addRenderer(new CountryMapRenderer());
            meta.setMapView(view);
            meta.setDisplayName("§b§lПолитическая Карта");
            map.setItemMeta(meta);
            p.getInventory().addItem(map);
            return true;
        }

        return true;
    }

    private boolean isSubCommand(String arg) {
        return Arrays.asList("map", "color", "claim").contains(arg.toLowerCase());
    }

    private String getPlayerCountry(Player p) {
        return leaders.entrySet().stream()
                .filter(e -> e.getValue().equals(p.getUniqueId()))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) return Arrays.asList("claim", "map", "color").stream()
                .filter(o -> o.startsWith(args[0])).collect(Collectors.toList());
        return new ArrayList<>();
    }

    // --- РЕНДЕРЕР КАРТЫ ---
    public class CountryMapRenderer extends MapRenderer {
        @Override
        public void render(MapView view, MapCanvas canvas, Player p) {
            int pX = p.getLocation().getChunk().getX();
            int pZ = p.getLocation().getChunk().getZ();

            for (int cx = -7; cx <= 7; cx++) {
                for (int cz = -7; cz <= 7; cz++) {
                    String key = p.getWorld().getName() + ":" + (pX + cx) + ":" + (pZ + cz);
                    if (claims.containsKey(key)) {
                        Color col = countryColors.getOrDefault(claims.get(key), Color.GREEN);
                        byte mCol = MapPalette.matchColor(col.getRed(), col.getGreen(), col.getBlue());

                        int x = 64 + (cx * 8);
                        int y = 64 + (cz * 8);

                        for (int i = 0; i < 8; i++) {
                            if (x+i >= 0 && x+i < 128 && y >= 0 && y < 128) canvas.setPixel(x+i, y, mCol);
                            if (x+i >= 0 && x+i < 128 && y+7 >= 0 && y+7 < 128) canvas.setPixel(x+i, y+7, mCol);
                            if (x >= 0 && x < 128 && y+i >= 0 && y+i < 128) canvas.setPixel(x, y+i, mCol);
                            if (x+7 >= 0 && x+7 < 128 && y+i >= 0 && y+i < 128) canvas.setPixel(x+7, y+i, mCol);
                        }
                    }
                }
            }
        }
    }

    // --- СОХРАНЕНИЕ ---
    private void saveData() {
        FileConfiguration config = getConfig();
        config.set("claims", null);
        claims.forEach((k, v) -> config.set("claims." + k.replace(":", "_"), v));
        leaders.forEach((k, v) -> config.set("leaders." + k, v.toString()));
        countryColors.forEach((k, v) -> config.set("colors." + k, v.asRGB()));
        saveConfig();
    }

    private void loadData() {
        FileConfiguration config = getConfig();
        if (config.getConfigurationSection("claims") != null)
            config.getConfigurationSection("claims").getKeys(false).forEach(k -> 
                claims.put(k.replace("_", ":"), config.getString("claims." + k)));
        if (config.getConfigurationSection("leaders") != null)
            config.getConfigurationSection("leaders").getKeys(false).forEach(k -> 
                leaders.put(k, UUID.fromString(config.getString("leaders." + k))));
        if (config.getConfigurationSection("colors") != null)
            config.getConfigurationSection("colors").getKeys(false).forEach(k -> 
                countryColors.put(k, Color.fromRGB(config.getInt("colors." + k))));
    }
}
