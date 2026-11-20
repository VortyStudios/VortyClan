package me.vorty.vortyclan;

import me.vorty.vortyclan.command.ClanCommand;
import me.vorty.vortyclan.gui.Menus;
import me.vorty.vortyclan.hook.PapiExpansion;
import me.vorty.vortyclan.listener.ChatListener;
import me.vorty.vortyclan.listener.GuiListener;
import me.vorty.vortyclan.listener.PlayerListener;
import me.vorty.vortyclan.manager.ClanManager;
import me.vorty.vortyclan.manager.ClanService;
import me.vorty.vortyclan.manager.PromptManager;
import me.vorty.vortyclan.util.FoliaTasks;
import me.vorty.vortyclan.util.Lang;
import me.vorty.vortyclan.util.SpigotTasks;
import me.vorty.vortyclan.util.Tasks;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class VortyClan extends JavaPlugin {

    private Tasks tasks;
    private Lang lang;
    private ClanManager clans;
    private ClanService service;
    private PromptManager prompts;
    private Menus menus;
    private boolean folia;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        folia = detectFolia();
        // FoliaTasks is only instantiated on Folia so other servers never load the region scheduler API.
        tasks = folia ? new FoliaTasks(this) : new SpigotTasks(this);

        lang = new Lang(this);
        lang.load();

        clans = new ClanManager(this);
        clans.load();

        prompts = new PromptManager(this);
        service = new ClanService(this);
        menus = new Menus(this);

        PluginCommand command = getCommand("clan");
        if (command == null) {
            getLogger().severe("Command 'clan' is missing from plugin.yml. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        ClanCommand handler = new ClanCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);

        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PapiExpansion(this).register();
            getLogger().info("PlaceholderAPI found: %vortyclan_...% placeholders registered.");
        }

        getLogger().info("VortyClan enabled (" + (folia ? "Folia" : "Bukkit scheduler") + ").");
    }

    @Override
    public void onDisable() {
        if (clans != null) clans.saveNow();
        if (tasks != null) tasks.cancelAll();
    }

    public void reloadAll() {
        reloadConfig();
        lang.load();
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    public boolean isFolia() {
        return folia;
    }

    public Tasks tasks() {
        return tasks;
    }

    public Lang lang() {
        return lang;
    }

    public ClanManager clans() {
        return clans;
    }

    public ClanService service() {
        return service;
    }

    public PromptManager prompts() {
        return prompts;
    }

    public Menus menus() {
        return menus;
    }
}
