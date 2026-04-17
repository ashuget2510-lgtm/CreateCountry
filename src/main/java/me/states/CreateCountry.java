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
import java.util.stream.Collectors;

public class CreateCountry extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    public static final Map<String, String> claims = new ConcurrentHashMap<>();
    public static final Map<String, Color> countryColors = new HashMap<>();
    public static final Map<String, String> leaders = new HashMap<>();

    @Override
    public void onEnable() {
        getCommand("create").setExecutor(this);
        getCommand("create").setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("!!! CreateCountry v6.5 LOADED !!!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (args.length == 0) {
            p.sendMessage("§b--- [ Система Стран ] ---");
            p.sendMessage("§f/create <название> §7- Создать");
            p.sendMessage("§f/create color <R G B> §7- Цвет");
            p.sendMessage("§f/create map §7- Взять карту");
            return true;
        }

        // Команда создания: /create <название>
        if (args.length == 1 && !args[0].equalsIgnoreCase("map") && !args[0].equalsIgnoreCase("color")) {
            String name = args[0];
            leaders.put(name, p.getName());
            countryColors.put(name, Color.GREEN);
            p.sendMessage("§a§l[✔] §fСтрана §l" + name + " §fсоздана!");
            return true;
        }

        // Команда цвета: /create color 255 0 0
        if (args[0].equalsIgnoreCase("color") && args.length >= 4) {
            String country = getPlayerCountry(p);
            if (country == null) { p.sendMessage("§c[!] У вас нет страны."); return true; }
            try {
                int r = Integer.parseInt(args[1]);
                int g = Integer.parseInt(args[2]);
                int b = Integer.parseInt(args[3]);
                countryColors.put(country, Color.fromRGB(r, g, b));
                p.sendMessage("§a§l[✔] §fЦвет страны §l" + country + " §fизменен!");
            } catch (Exception e) { p.sendMessage("§c[!] Ошибка RGB!"); }
            return true;
        }

        // Команда карты: /create map
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

    private String getPlayerCountry(Player p) {
        return leaders.entrySet().stream().filter(e -> e.getValue().equals(p.getName())).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    // --- ТЕ САМЫЕ ПОДСКАЗКИ (Чтобы не было ников) ---
    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("color", "map", "help").stream()
                    .filter(opt -> opt.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args[0].equalsIgnoreCase("color") && args.length <= 4) {
            return Collections.singletonList("255");
        }
        return new ArrayList<>();
    }

    // --- ОТРИСОВКА КАРТЫ ---
    public class CountryMapRenderer extends MapRenderer {
        @Override
        public void render(MapView view, MapCanvas canvas, Player p) {
            for (int x = 0; x < 128; x++) for (int y = 0; y < 128; y++) canvas.setPixel(x, y, MapPalette.GRAY_1);
            int pX = p.getLocation().getChunk().getX();
            int pZ = p.getLocation().getChunk().getZ();

            for (int cx = -6; cx <= 6; cx++) {
                for (int cz = -6; cz <= 6; cz++) {
                    String key = p.getWorld().getName() + ":" + (pX + cx) + ":" + (pZ + cz);
                    int x = 64 + (cx * 9);
                    int y = 64 + (cz * 9);
                    if (claims.containsKey(key)) {
                        String cName = claims.get(key);
                        Color col = countryColors.getOrDefault(cName, Color.GREEN);
                        byte mCol = MapPalette.matchColor(col.getRed(), col.getGreen(), col.getBlue());
                        for(int i=0; i<8; i++) for(int j=0; j<8; j++) canvas.setPixel(x+i, y+j, mCol);
                    }
                }
            }
            canvas.setPixel(64, 64, MapPalette.RED); // Точка игрока
        }
    }
}
