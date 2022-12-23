package io.github.kidofcubes;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.java_websocket.enums.CloseHandshakeType;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;

public class EuphBridgeBot extends JavaPlugin {

    Logger logger=getLogger();
    EuphBridgeEuphoriaBot bot=new EuphBridgeEuphoriaBot("McBridgeBot");

    FileConfiguration config;
    public static boolean coloredMinecraftNames = true;
    public static boolean coloredEuphNames = true;
    public static boolean bridgeInactivityCheck = true;
    public static double maximumBridgeLife = 60*10; //in seconds
    public static boolean attemptThreadedChat = false;

    public static EuphBridgeBot mainPluginInstance;

    @Override
    public void onEnable() {
        loadConfig();
        mainPluginInstance=this;

        getServer().getPluginManager().registerEvents(new ChatManager(bot),this);
        try {
            bot.joinRoom(new URI("wss://euphoria.io/room/testing/ws"));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        reloadConfig();
        ChatManager.enable();
    }

    void loadConfig(){
        config=this.getConfig();
        config.addDefault("coloredMinecraftNames", coloredMinecraftNames);
        config.addDefault("coloredEuphNames", coloredEuphNames);
        config.addDefault("bridgeInactivityCheck", bridgeInactivityCheck);
        config.addDefault("maximumBridgeLife", maximumBridgeLife);
        config.addDefault("attemptThreadedChat", attemptThreadedChat);
        config.options().copyDefaults(true);
        saveConfig();
        coloredMinecraftNames=config.getBoolean("coloredMinecraftNames");
        coloredEuphNames=config.getBoolean("coloredEuphNames");
        bridgeInactivityCheck=config.getBoolean("bridgeInactivityCheck");
        maximumBridgeLife=config.getDouble("maximumBridgeLife");
        attemptThreadedChat=config.getBoolean("attemptThreadedChat");
    }

    @Override
    public void onDisable() {
        bot.disconnect();
        ChatManager.disable();
    }
}
