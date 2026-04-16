package me.states;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CreateCountry extends JavaPlugin implements Listener, CommandExecutor {

    // world:x:z -> Название страны
    public static final Map<String, String> claims = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        getCommand("create").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Система бумажных карт запущена!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (args.length > 0 && args[0].equalsIgnoreCase("map")) {
            ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
            MapMeta meta = (MapMeta) mapItem.getItemMeta();
            MapView view = Bukkit.createMap(p.getWorld());
            
            // Удаляем стандартные слои и ставим наш радар
            view.getRenderers().clear();
            view.addRenderer(new CountryMapRenderer());
            view.setTrackingPosition(true);
            
            meta.setMapView(view);
            meta.setDisplayName("§b§lКарта Территорий");
            mapItem.setItemMeta(meta);
            p.getInventory().addItem(mapItem);
            p.sendMessage("§a[✔] Вы получили бумажный радар!");
            return true;
        }
        return true;
    }

    // --- ОТРИСОВКА НА БУМАГЕ ---
    public class CountryMapRenderer extends MapRenderer {
        @Override
        public void render(MapView view, MapCanvas canvas, Player p) {
            // Очистка фона (серый)
            for (int x = 0; x < 128; x++) {
                for (int y = 0; y < 128; y++) {
                    canvas.setPixel(x, y, MapPalette.GRAY_1);
                }
            }

            int playerX = p.getLocation().getChunk().getX();
            int playerZ = p.getLocation().getChunk().getZ();

            // Рисуем сетку 11x11 чанков вокруг игрока
            for (int cx = -5; cx <= 5; cx++) {
                for (int cz = -5; cz <= 5; cz++) {
                    String key = p.getWorld().getName() + ":" + (playerX + cx) + ":" + (playerZ + cz);
                    
                    int mapX = 64 + (cx * 10);
                    int mapY = 64 + (cz * 10);

                    if (claims.containsKey(key)) {
                        // Если чанк захвачен — рисуем зеленый квадрат
                        drawChunkSquare(canvas, mapX, mapY, MapPalette.LIGHT_GREEN);
                    } else {
                        // Если пустой — рисуем контур
                        drawChunkSquare(canvas, mapX, mapY, MapPalette.DARK_GRAY);
                    }
                }
            }
            // Точка игрока в центре
            canvas.setPixel(64, 64, MapPalette.WHITE);
        }

        private void drawChunkSquare(MapCanvas canvas, int x, int y, byte color) {
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    if (x + i >= 0 && x + i < 128 && y + j >= 0 && y + j < 128) {
                        canvas.setPixel(x + i, y + j, color);
                    }
                }
            }
        }
    }

    // --- МАГИЧЕСКОЕ ПЕРО (ЭДИТОР) ---
    @EventHandler
    public void onEdit(org.bukkit.event.player.PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (p.getInventory().getItemInMainHand().getType() == Material.FEATHER) {
            e.setCancelled(true);
            Chunk c = p.getLocation().getChunk();
            String key = c.getWorld().getName() + ":" + c.getX() + ":" + c.getZ();

            if (e.getAction().name().contains("LEFT")) {
                claims.put(key, "CurrentState");
                p.sendActionBar("§a§lЧАНК ЗАХВАЧЕН");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
            } else if (e.getAction().name().contains("RIGHT")) {
                claims.remove(key);
                p.sendActionBar("§c§lЗАХВАТ УДАЛЕН");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
            }
        }
    }
}
