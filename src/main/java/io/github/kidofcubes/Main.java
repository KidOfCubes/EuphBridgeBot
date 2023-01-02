package io.github.kidofcubes;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class Main extends JavaPlugin {

    Logger logger=getLogger();
    EuphBridgeEuphoriaBot bot;

    FileConfiguration config;

    public static List<String> roomList = new ArrayList<>();
    public static boolean coloredMinecraftNames = true;
    public static boolean coloredEuphNames = true;
    public static boolean bridgeInactivityCheck = true;
    public static int bridgeInactivityPeriod = 600;
    public static double maximumBridgeLife = 60*10; //in seconds
    public static boolean threadedChat = false;
    public static int threadedChatMessageLimit = 1000;
    public static int threadedChatNormalChatLife = 10;
    public static String indentationText = "   ";



    public static Main mainPluginInstance;

    @Override
    public void onEnable() {
        loadConfig();
        mainPluginInstance=this;
        bot = new EuphBridgeEuphoriaBot("McBridgeBot");

        getServer().getPluginManager().registerEvents(new ChatManager(bot),this);
        try {
            for(String roomURL : roomList){
                bot.joinRoom(new URI(roomURL));
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        reloadConfig();
        ChatManager.enable();
    }

    void loadConfig(){
        this.saveDefaultConfig();
        config=this.getConfig();

        roomList=config.getStringList("roomEndpoints");

        coloredMinecraftNames=config.getBoolean("coloredMinecraftNames");
        coloredEuphNames=config.getBoolean("coloredEuphNames");
        bridgeInactivityCheck=config.getBoolean("bridgeInactivityCheck");
        bridgeInactivityPeriod=config.getInt("bridgeInactivityPeriod");
        maximumBridgeLife=config.getDouble("maximumBridgeLife");

        threadedChat=config.getBoolean("threadedChat.enabled");
        indentationText=config.getString("threadedChat.indentationText");
        threadedChatMessageLimit=config.getInt("threadedChat.messageLimit");
        threadedChatNormalChatLife=config.getInt("threadedChat.normalChatLife");

    }

    @Override
    public void onDisable() {
        bot.disconnect();
        ChatManager.disable();
    }
}
