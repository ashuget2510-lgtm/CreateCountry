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

    @Override
    public void onEnable() {
        setupEconomy();
        setupDatabase();
        loadClaims();
        
        getCommand("create").setExecutor(this);
        getCommand("create").setTabCompleter(this);
        getCommand("state").setExecutor(this);
        getCommand("state").setTabCompleter(this);
        
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("CreateCountry включен!");
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) econ = rsp.getProvider();
        }
    }

    private void setupDatabase() {
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdir();
            db = DriverManager.getConnection("jdbc:sqlite:" + getDataFolder() + "/states.db");
            Statement s = db.createStatement();
            s.execute("CREATE TABLE IF NOT EXISTS states (name TEXT PRIMARY KEY, leader TEXT, r INT, g INT, b INT)");
            s.execute("CREATE TABLE IF NOT EXISTS claims (world TEXT, x INT, z INT, owner TEXT)");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void loadClaims() {
        claims.clear();
        try (ResultSet rs = db.createStatement().executeQuery("SELECT * FROM claims")) {
            while (rs.next()) {
                claims.put(rs.getString("world") + ":" + rs.getInt("x") + ":" + rs.getInt("z"), rs.getString("owner"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("create") && sender instanceof Player) {
            Player p = (Player) sender;
            if (args.length < 4) {
                p.sendMessage("§6[!] §fИспользуй: /create <имя> <R> <G> <B>");
                return true;
            }
            try {
                String name = args[0];
                int r = Integer.parseInt(args[1]);
                int g = Integer.parseInt(args[2]);
                int b = Integer.parseInt(args[3]);
                if (econ != null && econ.withdrawPlayer(p, 10000).transactionSuccess()) {
                    saveState(name, p.getUniqueId().toString(), Color.fromRGB(r, g, b));
                    p.sendMessage("§a[✔] Страна §l" + name + " §aсоздана!");
                } else p.sendMessage("§c[✘] Нужно $10,000!");
            } catch (Exception e) { p.sendMessage("§c[!] Ошибка в числах RGB!"); }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("state")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("state.admin")) {
                    sender.sendMessage("§cНет прав!");
                    return true;
                }
                sender.sendMessage("§e[↻] Перезагрузка плагина CreateCountry...");
                loadClaims();
                sender.sendMessage("§a[✔] Плагин успешно перезагружен!");
                return true;
            }
            
            if (sender instanceof Player && args.length > 0 && args[0].equalsIgnoreCase("claim")) {
                Player p = (Player) sender;
                String state = getPlayerState(p);
                if (state != null && econ != null && econ.withdrawPlayer(p, 500).transactionSuccess()) {
                    Chunk c = p.getLocation().getChunk();
                    claims.put(c.getWorld().getName() + ":" + c.getX() + ":" + c.getZ(), state);
                    saveClaim(c, state);
                    p.sendMessage("§a[✔] Чанк захвачен!");
                } else p.sendMessage("§c[✘] Ошибка (вы не лидер или нет денег)");
                return true;
            }
            sender.sendMessage("§b--- [ Страны ] ---\n§f/state claim §7- Захват\n§f/state reload §7- Админ-релоад");
        }
        return true;
    }

    // ТЕ САМЫЕ ПОДСКАЗКИ (TAB COMPLETE)
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (cmd.getName().equalsIgnoreCase("state")) {
            if (args.length == 1) return filter(Arrays.asList("claim", "reload", "help"), args[0]);
        }
        if (cmd.getName().equalsIgnoreCase("create")) {
            if (args.length == 1) return Collections.singletonList("<Название>");
            if (args.length == 2) return Arrays.asList("255", "0");
            if (args.length == 3) return Arrays.asList("255", "0");
            if (args.length == 4) return Arrays.asList("255", "0");
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String arg) {
        return list.stream().filter(s -> s.toLowerCase().startsWith(arg.toLowerCase())).collect(Collectors.toList());
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Chunk c = e.getBlock().getChunk();
        String key = c.getWorld().getName() + ":" + c.getX() + ":" + c.getZ();
        if (claims.containsKey(key) && !claims.get(key).equals(getPlayerState(e.getPlayer()))) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§c[!] Территория страны §l" + claims.get(key));
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
