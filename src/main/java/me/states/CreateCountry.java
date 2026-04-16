package me.states;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CreateCountry extends JavaPlugin implements Listener, CommandExecutor {

    // Структура данных
    public static final Map<String, String> claims = new ConcurrentHashMap<>(); // чанк -> название
    public static final Map<String, Color> countryColors = new HashMap<>(); // название -> цвет
    public static final Map<String, String> leaders = new HashMap<>(); // название -> ник лидера

    @Override
    public void onEnable() {
        getCommand("create").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (args.length == 0) return false;

        // --- СОЗДАНИЕ СТРАНЫ ---
        if (args.length >= 2 && args[0].equalsIgnoreCase("create")) {
            String name = args[1];
            leaders.put(name, p.getName());
            countryColors.put(name, Color.GREEN); // Цвет по умолчанию
            p.sendMessage("§a[✔] Страна §l" + name + " §aсоздана! Цвет: Зеленый.");
            return true;
        }

        // --- ВЫБОР ЦВЕТА ---
        if (args.length >= 4 && args[0].equalsIgnoreCase("color")) {
            String country = getPlayerCountry(p);
            if (country == null) { p.sendMessage("§cСначала создайте страну!"); return true; }
            
            try {
                int r = Integer.parseInt(args[1]);
                int g = Integer.parseInt(args[2]);
                int b = Integer.parseInt(args[3]);
                countryColors.put(country, Color.fromRGB(r, g, b));
                p.sendMessage("§a[✔] Цвет страны §l" + country + " §aизменен!");
            } catch (Exception e) { p.sendMessage("§cОшибка: /create color <0-255> <0-255> <0-255>"); }
            return true;
        }

        // --- ВЫДАЧА КАРТЫ ---
        if (args[0].equalsIgnoreCase("map")) {
            ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
            MapMeta meta = (MapMeta) mapItem.getItemMeta();
            MapView view = Bukkit.createMap(p.getWorld());
            view.getRenderers().clear();
            view.addRenderer(new AdvancedMapRenderer());
            meta.setMapView(view);
            meta.setDisplayName("§b§lПолитическая Карта");
            mapItem.setItemMeta(meta);
            p.getInventory().addItem(mapItem);
            return true;
        }
        return true;
    }

    private String getPlayerCountry(Player p) {
        return leaders.entrySet().stream().filter(e -> e.getValue().equals(p.getName())).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    // --- ПРОДВИНУТЫЙ ОТРИСОВЩИК КАРТЫ ---
    public class AdvancedMapRenderer extends MapRenderer {
        @Override
        public void render(MapView view, MapCanvas canvas, Player p) {
            // Очистка фона
            for (int x = 0; x < 128; x++) for (int y = 0; y < 128; y++) canvas.setPixel(x, y, MapPalette.GRAY_1);

            int pX = p.getLocation().getChunk().getX();
            int pZ = p.getLocation().getChunk().getZ();

            // Отрисовка чанков
            for (int cx = -6; cx <= 6; cx++) {
                for (int cz = -6; cz <= 6; cz++) {
                    String key = p.getWorld().getName() + ":" + (pX + cx) + ":" + (pZ + cz);
                    int x = 64 + (cx * 9);
                    int y = 64 + (cz * 9);

                    if (claims.containsKey(key)) {
                        String countryName = claims.get(key);
                        Color col = countryColors.getOrDefault(countryName, Color.GREEN);
                        byte mapColor = MapPalette.matchColor(col.getRed(), col.getGreen(), col.getBlue());
                        drawSquare(canvas, x, y, mapColor, countryName);
                    } else {
                        drawSquare(canvas, x, y, MapPalette.DARK_GRAY, null);
                    }
                }
            }
            // Игрок
            canvas.setPixel(64, 64, MapPalette.RED);
        }

        private void drawSquare(MapCanvas canvas, int x, int y, byte color, String name) {
            for (int i = 0; i < 7; i++) {
                for (int j = 0; j < 7; j++) {
                    if (x + i >= 0 && x + i < 128 && y + j >= 0 && y + j < 128) {
                        canvas.setPixel(x + i, y + j, color);
                    }
                }
            }
            // Если есть название — пишем первую букву или название рядом
            if (name != null && x == 64 && y == 64) {
                canvas.drawText(x, y, MinecraftFont.Font, "§0" + name);
            }
        }
    }
}
