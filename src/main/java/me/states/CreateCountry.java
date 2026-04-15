package me.states;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import net.milkbowl.vault.economy.Economy;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CreateCountry extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private Connection db;
    private Economy econ = null;
    private final Map<String, String> claims = new ConcurrentHashMap<>();
    private final Map<String, Color> stateColors = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Vault не найден! Экономика отключена.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        setupDatabase();
        
        // Регистрация команд
        getCommand("create").setExecutor(this);
        getCommand("create").setTabCompleter(this);
        getCommand("state").setExecutor(this);
        getCommand("state").setTabCompleter(this);
        
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Плагин на страны успешно запущен!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    private void setupDatabase() {
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdir();
            db = DriverManager.getConnection("jdbc:sqlite:" + getDataFolder() + "/states.db");
            db.createStatement().execute("CREATE TABLE IF NOT EXISTS states (name TEXT PRIMARY KEY, leader TEXT, r INT, g INT, b INT)");
            db.createStatement().execute("CREATE TABLE IF NOT EXISTS claims (world TEXT, x INT, z INT, owner TEXT)");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Команды только для игроков!");
            return true;
        }
        Player p = (Player) sender;

        // Команда /create <название> <R> <G> <B>
        if (cmd.getName().equalsIgnoreCase("create")) {
            if (args.length < 4) {
                p.sendMessage("§e[!] Использование: §f/create <название> <R> <G> <B>");
                p.sendMessage("§7Пример: /create Russia 255 0 0");
                return true;
            }
            String name = args[0];
            try {
                int r = Integer.parseInt(args[1]);
                int g = Integer.parseInt(args[2]);
                int b = Integer.parseInt(args[3]);
                Color col = Color.fromRGB(r, g, b);
                
                if (econ.withdrawPlayer(p, 10000).transactionSuccess()) {
                    saveState(name, p.getUniqueId().toString(), col);
                    p.sendMessage("§a[✔] Страна §l" + name + " §aуспешно создана!");
                } else {
                    p.sendMessage("§c[✘] Недостаточно средств! Нужно §6$10,000");
                }
            } catch (Exception e) {
                p.sendMessage("§c[!] Ошибка в цвете RGB (0-255)!");
            }
            return true;
        }
        
        // Команда /state
        if (cmd.getName().equalsIgnoreCase("state")) {
            if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
                p.sendMessage("§b--- [ Помощь по Странам ] ---");
                p.sendMessage("§f/state claim §7- Захватить чанк ($500)");
                p.sendMessage("§f/state info §7- Узнать владельца чанка");
                p.sendMessage("§f/create <имя> <r> <g> <b> §7- Создать страну");
                return true;
            }

            if (args[0].equalsIgnoreCase("claim")) {
                String state = getPlayerState(p);
                if (state == null) {
                    p.sendMessage("§c[!] Вы не являетесь лидером страны!");
                    return true;
                }
                if (econ.withdrawPlayer(p, 500).transactionSuccess()) {
                    Chunk c = p.getLocation().getChunk();
                    claims.put(c.getWorld().getName() + ":" + c.getX() + ":" + c.getZ(), state);
                    saveClaim(c, state);
                    p.sendMessage("§a[✔] Чанк теперь принадлежит стране §l" + state);
                } else {
                    p.sendMessage("§c[✘] Мало денег! Захват стоит §6$500");
                }
                return true;
            }
        }
        return true;
    }

    // ТАБ-КОМПЛИТЕР (Подсказки)
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (cmd.getName().equalsIgnoreCase("state")) {
            if (args.length == 1) {
                return Arrays.asList("claim", "help", "info").stream()
                        .filter(s -> s.startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        if (cmd.getName().equalsIgnoreCase("create")) {
            if (args.length == 1) return Collections.singletonList("<Название>");
            if (args.length == 2) return Collections.singletonList("255"); // Подсказка для R
        }
        return null;
    }

    @EventHandler public void onBreak(BlockBreakEvent e) { check(e, e.getBlock().getChunk()); }
    @EventHandler public void onPlace(BlockPlaceEvent e) { check(e, e.getBlock().getChunk()); }

    private void check(Cancellable e, Chunk c) {
        String key = c.getWorld().getName() + ":" + c.getX() + ":" + c.getZ();
        if (claims.containsKey(key)) {
            String owner = claims.get(key);
            Player p = (e instanceof BlockBreakEvent) ? ((BlockBreakEvent)e).getPlayer() : ((BlockPlaceEvent)e).getPlayer();
            if (!owner.equals(getPlayerState(p))) {
                e.setCancelled(true);
                p.sendMessage("§c[!] Эта территория принадлежит стране §l" + owner);
            }
        }
    }

    private String getPlayerState(Player p) {
        try (PreparedStatement ps = db.prepareStatement("SELECT name FROM states WHERE leader = ?")) {
            ps.setString(1, p.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("name");
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    private void saveState(String n, String l, Color c) throws SQLException {
        PreparedStatement ps = db.prepareStatement("INSERT OR REPLACE INTO states VALUES (?, ?, ?, ?, ?)");
        ps.setString(1, n); ps.setString(2, l); ps.setInt(3, c.getRed()); ps.setInt(4, c.getGreen()); ps.setInt(5, c.getBlue());
        ps.execute();
    }

    private void saveClaim(Chunk c, String o) {
        try (PreparedStatement ps = db.prepareStatement("INSERT OR REPLACE INTO claims VALUES (?, ?, ?, ?)")) {
            ps.setString(1, c.getWorld().getName()); ps.setInt(2, c.getX()); ps.setInt(3, c.getZ()); ps.setString(4, o);
            ps.execute();
        } catch (SQLException e) { e.printStackTrace(); }
    }
}
