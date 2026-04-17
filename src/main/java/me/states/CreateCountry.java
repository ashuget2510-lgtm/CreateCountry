package me.states;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CreateCountry extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    public static final Map<String, String> claims = new ConcurrentHashMap<>();
    public static final Map<String, Color> countryColors = new HashMap<>();
    public static final Map<String, String> leaders = new HashMap<>();

    @Override
    public void onEnable() {
        loadData();
        getCommand("create").setExecutor(this);
        getCommand("create").setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("!!! CreateCountry v1.6.5 LOADED !!!");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        if (args.length == 0) {
            p.sendMessage("§b--- [ Система Стран ] ---");
            p.sendMessage("§f/create <название> §7- Создать страну");
            p.sendMessage("§f/create claim §7- Захватить чанк");
            p.sendMessage("§f/create color <R G B> §7- Сменить цвет");
            p.sendMessage("§f/create map §7- Получить карту");
            return true;
        }

        // 1. Создание страны
        if (args.length == 1 && !isSubCommand(args[0])) {
            String name = args[0];
            if (getPlayerCountry(p) != null) { p.sendMessage("§c[!] У вас уже есть страна."); return true; }
            leaders.put(name, p.getName());
            countryColors.put(name, Color.GREEN);
            p.sendMessage("§a§l[✔] §fСтрана §l" + name + " §fсоздана!");
            saveData();
            return true;
        }

        // 2. Захват чанка
        if (args[0].equalsIgnoreCase("claim")) {
            String country = getPlayerCountry(p);
            if (country == null) { p.sendMessage("§c[!] Вы не лидер страны."); return true; }
            
            String chunkKey = p.getWorld().getName() + ":" + p.getLocation().getChunk().getX() + ":" + p.getLocation().getChunk().getZ();
            if (claims.containsKey(chunkKey)) {
                p.sendMessage("§c[!] Этот чанк уже принадлежит: " + claims.get(chunkKey));
                return true;
            }
            claims.put(chunkKey, country);
            p.sendMessage("§a§l[✔] §fЧанк захвачен для §l" + country);
            return true;
        }

        // 3. Цвет страны
        if (args[0].equalsIgnoreCase("color") && args.length >= 4) {
            String country = getPlayerCountry(p);
            if (country == null) { p.sendMessage("§c[!] У вас нет страны."); return true; }
            try {
                int r = Integer.parseInt(args[1]);
                int g = Integer.parseInt(args[2]);
                int b = Integer.parseInt(args[3]);
                countryColors.put(country, Color.fromRGB(r, g, b));
                p.sendMessage("§a§l[✔] §fЦвет страны §l" + country + " §fизменен!");
            } catch (Exception e) { p.sendMessage("§c[!] Ошибка! Формат: /create color R G B (0-255)"); }
            return true;
        }

        // 4. Выдача карты
        if (args[0].equalsIgnoreCase("map")) {
            ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
            MapMeta meta = (MapMeta) mapItem.getItemMeta();
            MapView view = Bukkit.createMap(p.getWorld());
            view.getRenderers().clear();
            view.addRenderer(new CountryMapRenderer());
            meta.setMapView(view);
            meta.setDisplayName("§b§lПолитическая Карта");
            mapItem.setItemMeta(meta);
            p.getInventory().addItem(mapItem);
            p.sendMessage("§a§l[✔] §fКарта выдана!");
            return true;
        }
        return true;
    }

    private boolean isSubCommand(String arg) {
        return arg.equalsIgnoreCase("map") || arg.equalsIgnoreCase("color") || arg.equalsIgnoreCase("claim") || arg.equalsIgnoreCase("help");
    }

    private String getPlayerCountry(Player p) {
        return leaders.entrySet().stream().filter(e -> e.getValue().equals(p.getName())).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("color", "map", "claim", "help").stream()
                    .filter(opt -> opt.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args[0].equalsIgnoreCase("color") && args.length <= 4) return Collections.singletonList("255");
        return new ArrayList<>();
    }

    // Сохранение и загрузка данных
    private void saveData() {
        FileConfiguration config = getConfig();
        config.set("claims", null); 
        claims.forEach((k, v) -> config.set("claims." + k.replace(":", "_"), v));
        leaders.forEach((k, v) -> config.set("leaders." + k, v));
        countryColors.forEach((k, v) -> config.set("colors." + k, v.asRGB()));
        saveConfig();
    }

    private void loadData() {
        FileConfiguration config = getConfig();
        if (config.getConfigurationSection("claims") != null) {
            config.getConfigurationSection("claims").getKeys(false).forEach(k -> claims.put(k.replace("_", ":"), config.getString("claims." + k)));
        }
        if (config.getConfigurationSection("leaders") != null) {
            config.getConfigurationSection("leaders").getKeys(false).forEach(k -> leaders.put(k, config.getString("leaders." + k)));
        }
        if (config.getConfigurationSection("colors") != null) {
            config.getConfigurationSection("colors").getKeys(false).forEach(k -> countryColors.put(k, Color.fromRGB(config.getInt("colors." + k))));
        }
    }

    // Отрисовка карты
    public class CountryMapRenderer extends MapRenderer {
        @Override
        public void render(MapView view, MapCanvas canvas, Player p) {
            // Фон
            for (int x = 0; x < 128; x++) for (int y = 0; y < 128; y++) canvas.setPixel(x, y, MapPalette.GRAY_1);
            
            int pX = p.getLocation().getChunk().getX();
            int pZ = p.getLocation().getChunk().getZ();

            // Отрисовка чанков вокруг игрока
            for (int cx = -7; cx <= 7; cx++) {
                for (int cz = -7; cz <= 7; cz++) {
                    String key = p.getWorld().getName() + ":" + (pX + cx) + ":" + (pZ + cz);
                    if (claims.containsKey(key)) {
                        String cName = claims.get(key);
                        Color col = countryColors.getOrDefault(cName, Color.GREEN);
                        byte mCol = MapPalette.matchColor(col.getRed(), col.getGreen(), col.getBlue());
                        
                        int drawX = 64 + (cx * 8);
                        int drawY = 64 + (cz * 8);
                        
                        for(int i=0; i<7; i++) {
                            for(int j=0; j<7; j++) {
                                if (drawX+i >= 0 && drawX+i < 128 && drawY+j >= 0 && drawY+j < 128) {
                                    canvas.setPixel(drawX+i, drawY+j, mCol);
                                }
                            }
                        }
                    }
                }
            }
            canvas.setPixel(64, 64, MapPalette.RED); // Игрок в центре
        }
    }
}
