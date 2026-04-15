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

public class CreateCountry extends JavaPlugin implements Listener, CommandExecutor {

    private Connection db;
    private Economy econ = null;
    private final Map<String, String> claims = new ConcurrentHashMap<>();
    private final Map<String, Color> stateColors = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Vault не найден!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        setupDatabase();
        getCommand("create").setExecutor(this);
        getCommand("state").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
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
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("create") && args.length >= 5) {
            String name = args[1];
            try {
                Color col = Color.fromRGB(Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]));
                if (econ.withdrawPlayer(p, 10000).transactionSuccess()) {
                    saveState(name, p.getUniqueId().toString(), col);
                    stateColors.put(name, col);
                    p.sendMessage("§aСтрана создана!");
                } else p.sendMessage("§cМало денег ($10,000)!");
            } catch (Exception e) { p.sendMessage("§cОшибка RGB!"); }
            return true;
        }
        
        if (cmd.getName().equalsIgnoreCase("state") && args.length > 0 && args[0].equalsIgnoreCase("claim")) {
            String state = getPlayerState(p);
            if (state != null && econ.withdrawPlayer(p, 500).transactionSuccess()) {
                Chunk c = p.getLocation().getChunk();
                claims.put(c.getWorld().getName() + ":" + c.getX() + ":" + c.getZ(), state);
                saveClaim(c, state);
                p.sendMessage("§aЧанк захвачен!");
            } else p.sendMessage("§cОшибка захвата!");
            return true;
        }
        return true;
    }

    @EventHandler public void onBreak(BlockBreakEvent e) { check(e, e.getBlock().getChunk()); }
    @EventHandler public void onPlace(BlockPlaceEvent e) { check(e, e.getBlock().getChunk()); }

    private void check(Cancellable e, Chunk c) {
        String key = c.getWorld().getName() + ":" + c.getX() + ":" + c.getZ();
        if (claims.containsKey(key)) {
            String owner = claims.get(key);
            if (!owner.equals(getPlayerState((Player) e))) e.setCancelled(true);
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
