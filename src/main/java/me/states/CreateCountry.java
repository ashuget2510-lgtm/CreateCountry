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
        saveDefaultConfig();
        loadData();
        getCommand("create").setExecutor(this);
        getCommand("create").setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("!!! CreateCountry v1.6.5 (Political Overlay) LOADED !!!");
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
            p.sendMessage("§f/create <название> §7- Создать");
            p.sendMessage("§f/create claim §7- Захватить территорию");
            p.sendMessage("§f/create color <R G B> §7- Цвет");
            p.sendMessage("§f/create map §7- Взять полит. карту");
            return true;
        }

        // Создание страны
        if (args.length == 1 && !isSubCommand(args[0])) {
            String name = args[0];
            if (getPlayerCountry(p) != null) { p.sendMessage("§c[!] У вас уже есть страна."); return true; }
            leaders.put(name, p.getName());
            countryColors.put(name, Color.GREEN);
            p.sendMessage("§a§l[✔] §fСтрана §l" + name + " §fсоздана!");
            saveData();
            return true;
        }

        // Захват территории
        if (args[0].equalsIgnoreCase("claim")) {
            String country = getPlayerCountry(p);
            if (country == null) { p.sendMessage("§c[!] Только лидер страны может захватывать земли."); return true; }
            String chunkKey = p.getWorld().getName() + ":" + p.getLocation().getChunk().getX() + ":" + p.getLocation().getChunk().getZ();
            if (claims.containsKey(chunkKey)) {
                p.sendMessage("§c[!] Занято: " + claims.get(chunkKey));
                return true;
            }
            claims.put(chunkKey, country);
            p.sendMessage("§a§l[✔] §fТерритория добавлена к §l" + country);
            return true;
        }

        // Карта
        if (args[0].equalsIgnoreCase("map")) {
            ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
            MapMeta meta = (MapMeta) mapItem.getItemMeta();
            MapView view = Bukkit.createMap(p.getWorld());
            
            // ВАЖНО: Мы НЕ очищаем рендереры, чтобы осталась обычная карта мира
            view.addRenderer(new CountryMapRenderer());
            
            meta.setMapView(view);
            meta.setDisplayName("§b§lПолитическая Карта");
            mapItem.setItemMeta(meta);
            p.getInventory().addItem(mapItem);
            p.sendMessage("§a§l[✔] §fВы получили карту с границами!");
            return true;
        }

        // Цвет
        if (args[0].equalsIgnoreCase("color") && args.length >= 4) {
            String country = getPlayerCountry(p);
            if (country == null) { p.sendMessage("§c[!] Нет страны."); return true; }
            try {
                int r = Integer.parseInt(args[1]);
                int g = Integer.parseInt(args[2]);
                int b = Integer.parseInt(args[3]);
                countryColors.put(country, Color.fromRGB(r, g, b));
                p.sendMessage("§a§l[✔] §fЦвет обновлен!");
            } catch (Exception e) { p.sendMessage("§c[!] Ошибка формата RGB."); }
            return true;
        }
        return true;
    }

    private boolean isSubCommand(String arg) {
        return Arrays.asList("map", "color", "claim", "help").contains(arg.toLowerCase());
    }

    private String getPlayerCountry(Player p) {
        return leaders.entrySet().stream().filter(e -> e.getValue().equals(p.getName())).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) return Arrays.asList("color", "map", "claim").stream().filter(o -> o.startsWith(args[0])).collect(Collectors.toList());
        return new ArrayList<>();
    }

    // Логика отрисовки
    public class CountryMapRenderer extends MapRenderer {
        @Override
        public void render(MapView view, MapCanvas canvas, Player p) {
            int pX = p.getLocation().getChunk().getX();
            int pZ = p.getLocation().getChunk().getZ();

            for (int cx = -8; cx <= 8; cx++) {
                for (int cz = -8; cz <= 8; cz++) {
                    String key = p.getWorld().getName() + ":" + (pX + cx) + ":" + (pZ + cz);
                    if (claims.containsKey(key)) {
                        String name = claims.get(key);
                        Color col = countryColors.getOrDefault(name, Color.GREEN);
                        byte mCol = MapPalette.matchColor(col.getRed(), col.getGreen(), col.getBlue());

                        int x = 64 + (cx * 8);
                        int y = 64 + (cz * 8);

                        // Рисуем рамку чанка (границы)
                        for (int i = 0; i < 8; i++) {
                            drawPixel(canvas, x + i, y, mCol);      // верх
                            drawPixel(canvas, x + i, y + 7, mCol);  // низ
                            drawPixel(canvas, x, y + i, mCol);      // лево
                            drawPixel(canvas, x + 7, y + i, mCol);  // право
                        }
                        // Пишем название страны в центре чанка (очень мелко)
                        if (cx % 2 == 0 && cz % 2 == 0) { // Чтобы не спамить текст в каждом чанке
                             canvas.drawText(x + 1, y + 1, MinecraftFont.Font, "§1" + name.substring(0, Math.min(name.length(), 3)));
                        }
                    }
                }
            }
        }

        private void drawPixel(MapCanvas canvas, int x, int y, byte col) {
            if (x >= 0 && x < 128 && y >= 0 && y < 128) canvas.setPixel(x, y, col);
        }
    }

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
        if (config.getConfigurationSection("claims") != null)
            config.getConfigurationSection("claims").getKeys(false).forEach(k -> claims.put(k.replace("_", ":"), config.getString("claims." + k)));
        if (config.getConfigurationSection("leaders") != null)
            config.getConfigurationSection("leaders").getKeys(false).forEach(k -> leaders.put(k, config.getString("leaders." + k)));
        if (config.getConfigurationSection("colors") != null)
            config.getConfigurationSection("colors").getKeys(false).forEach(k -> countryColors.put(k, Color.fromRGB(config.getInt("colors." + k))));
    }
}
