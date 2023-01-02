package io.github.kidofcubes;

import euphoria.types.Message;
import euphoria.types.SessionView;
import euphoria.types.Snowflake;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.Nullable;

import static io.github.kidofcubes.Main.*;
import static java.util.concurrent.TimeUnit.*;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;

import static io.github.kidofcubes.ColorUtils.nickColor;

public class ChatManager implements Listener {

    static int currentTick=0;
    static Snowflake rootSnowflake = new Snowflake("0".repeat(Snowflake.length));

    static Map<Player,Map<Integer,List<Object>>> playerMessagePacketCache = new HashMap<>();

    static List<Map.Entry<Message, Integer>> threadedMessages = new ArrayList<>(); //key is parent of messages, value.first is parent message
    static Map<CommandSender,Snowflake> cursorLocation = new HashMap<>(); //value is parent of thread im on

    public static EuphBridgeEuphoriaBot botInstance;

    public ChatManager(EuphBridgeEuphoriaBot botInstance){
        ChatManager.botInstance =botInstance;
    }

    public static void updateAllChats(){
        Bukkit.getServer().getOnlinePlayers().forEach(ChatManager::updateChat);
    }

    public static Map.Entry<Integer,Integer> getEndOfThread(Snowflake id){
        for(int i = threadedMessages.size()-1; i>=0; i--){
            if(threadedMessages.get(i).getKey().id.equals(id)){
                return new AbstractMap.SimpleEntry<>(i, threadedMessages.get(i).getValue()+1);
            }
            if(threadedMessages.get(i).getKey().parent.equals(id)){
                int foundIndex = threadedMessages.size()-1;
                for(int j = i; j< threadedMessages.size(); j++){
                    if(threadedMessages.get(j).getValue()< threadedMessages.get(i).getValue()){
                        foundIndex=j-1;
                        break;
                    }
                }
                return new AbstractMap.SimpleEntry<>(foundIndex, threadedMessages.get(i).getValue());
            }
        }
        return new AbstractMap.SimpleEntry<>(-1,-69); //hehe
    }


    private static final BaseComponent[] emptyComponent = new ComponentBuilder("").create();

    public static void updateChat(CommandSender player){

        Map.Entry<Integer,Integer> endOfThread = getEndOfThread(cursorLocation.get(player));
        List<Map.Entry<Message,Integer>> focusedLines =
                threadedMessages.subList(
                        Math.max(endOfThread.getKey()-100,1),
                        Math.min(endOfThread.getKey()+1, threadedMessages.size()));
        List<BaseComponent[]> lines = new ArrayList<>(); //length 100

        for (Map.Entry<Message,Integer> entry: focusedLines) {
            lines.add(
                    new ComponentBuilder(indentationText.repeat(entry.getValue())).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/setcursor "+entry.getKey().id+" "+entry.getKey().parent))
                            .append(entry.getKey().sender.name, ComponentBuilder.FormatRetention.EVENTS)
                                .color(coloredMinecraftNames ? ChatColor.of(new Color(nickColor(entry.getKey().sender.name))) : null)
                            .append(": "+entry.getKey().content, ComponentBuilder.FormatRetention.EVENTS).create()
            );
        }



        //add cursor line
        lines.add(
                new ComponentBuilder(indentationText.repeat(endOfThread.getValue())).append(player.getName()).color(coloredMinecraftNames ? ChatColor.of(new Color(nickColor(player.getName()))) : null).italic(true)
                                .append(":", ComponentBuilder.FormatRetention.NONE).create()
        );

        //add small aftermessages

        focusedLines=(threadedMessages.subList(
                Math.min(endOfThread.getKey()+1, threadedMessages.size()),
                Math.min(endOfThread.getKey()+4, threadedMessages.size())));

        for (Map.Entry<Message,Integer> entry: focusedLines) {
            lines.add(
                    new ComponentBuilder(indentationText.repeat(entry.getValue())).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/setcursor "+entry.getKey().id+" "+entry.getKey().parent))
                            .append(entry.getKey().sender.name, ComponentBuilder.FormatRetention.EVENTS)
                            .color(coloredMinecraftNames ? ChatColor.of(new Color(nickColor(entry.getKey().sender.name))) : null)
                            .append(": "+entry.getKey().content, ComponentBuilder.FormatRetention.EVENTS).create()
//            );
            );
        }






        while(lines.size()<100){
            lines.add(0, emptyComponent);
        }
        lines.forEach(line -> {
            ((CraftPlayer)player).getHandle().connection.getConnection().channel.write(new ClientboundSystemChatPacket(line, false));
        });
        if(playerMessagePacketCache.get(player)!=null){
            for (List<Object> messagePackets: playerMessagePacketCache.get(player).values()) {
                messagePackets.forEach(packetObject ->  ((CraftPlayer)player).getHandle().connection.getConnection().channel.write(packetObject));
            }
        }
    }


    public static Message fakeMessage(Player player, String content, Snowflake parent){
        Message msg = new Message(content);
        SessionView fakeSession = new SessionView();
        fakeSession.name=player.getName();
        msg.sender = fakeSession;
        msg.parent = parent;
        msg.id = Snowflake.random();

        return msg;
    }

    public static void enable(){
        if(threadedChat) {
            Bukkit.getScheduler().runTaskTimer(mainPluginInstance, new Runnable() {
                @Override
                public void run() {
                    tickScheduled = false;
                    currentTick++;
                }
            }, 0, 1);
            Bukkit.getServer().getOnlinePlayers().forEach(ChatManager::listenToPlayer);
            Message fakeMsg = new Message(null);
            fakeMsg.id = rootSnowflake;
            fakeMsg.parent = rootSnowflake;
            mainPluginInstance.getCommand("setcursor").setExecutor(new SetCursorCommand());
            threadedMessages.add(new AbstractMap.SimpleEntry<>(fakeMsg, -1));
        }


    }

    public static void disable(){
        threadedMessages.clear();
    }

    static void addMessage(Message message){
        if(threadedChat) {
            Map.Entry<Integer, Integer> endOfThread = getEndOfThread(message.parent);
            threadedMessages.add(endOfThread.getKey() + 1, new AbstractMap.SimpleEntry<>(message, endOfThread.getValue()));
            while (threadedMessages.size() > threadedChatMessageLimit) {
                threadedMessages.remove(0);
            }
            updateAllChats();
        }else{
            if(coloredEuphNames){
                Bukkit.broadcastMessage(ChatColor.of(new Color(nickColor(message.sender.name))).toString()+message.sender.name+ChatColor.RESET+": "+message.content);
            }else{
                Bukkit.broadcastMessage(message.sender.name+": "+message.content);
            }
        }
    }



    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event){
        if(threadedChat) {
            Bukkit.getScheduler().runTaskLater(mainPluginInstance, () -> listenToPlayer(event.getPlayer()), 10);
        }
    }
    static void listenToPlayer(Player player){
        cursorLocation.put(player,rootSnowflake);
        ((CraftPlayer)player).getHandle().connection=new PlayerChatInterceptor(((CraftPlayer)player).getHandle().server,((CraftPlayer)player).getHandle().connection.connection,((CraftPlayer)player).getHandle().connection.player, player);
    }


    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    static boolean tickScheduled = false;


    public static final String playerNameMatcher = Matcher.quoteReplacement("%1$s");

    @EventHandler(priority=EventPriority.HIGH)
    public static void onMCChat(AsyncPlayerChatEvent event){




        if(threadedChat) {
            Message fakemsg = fakeMessage(event.getPlayer(),event.getMessage(),cursorLocation.get(event.getPlayer()));
            addMessage(fakemsg);
            botInstance.uploadLocalMessage(fakemsg);
            event.setCancelled(true);
            return;
        }else{
            botInstance.bridgeMessage("<"+event.getPlayer().getName()+">",event.getMessage());
        }

        if(coloredMinecraftNames){
            String newFormat = event.getFormat().replaceFirst(playerNameMatcher,
                ChatColor.of(new Color(nickColor(event.getPlayer().getName()))).toString()+
                    playerNameMatcher+
                    ChatColor.RESET);
            event.setFormat(newFormat);
        }



    }



    static class PlayerChatInterceptor extends ServerGamePacketListenerImpl{

        Player playerr;
        public PlayerChatInterceptor(MinecraftServer server, Connection connection, ServerPlayer player, Player player2) {
            super(server, connection, player);
            playerr=player2;
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener callbacks) {
            if(packet instanceof ClientboundSystemChatPacket || packet instanceof ClientboundPlayerChatPacket){
                int toRemoveTick = currentTick;
                playerMessagePacketCache.putIfAbsent(playerr, new HashMap<>());
                playerMessagePacketCache.get(playerr).putIfAbsent(currentTick, new ArrayList<>());
                playerMessagePacketCache.get(playerr).get(currentTick).add(packet);
                if (!tickScheduled) {
                    scheduler.schedule(() -> {
                        for (Map.Entry<Player, Map<Integer, List<Object>>> entry : playerMessagePacketCache.entrySet()) {
                            entry.getValue().remove(toRemoveTick);
//                            if ( != null){} //updateChat(entry.getKey());
                        }
                    }, threadedChatNormalChatLife, SECONDS);
                    tickScheduled = true;
                }
            }
            super.send(packet, callbacks);

        }
    }







    public static class SetCursorCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if(threadedChat) {
                if (args.length > 1) {
                    Snowflake snowflake1 = new Snowflake(args[0]);
                    Snowflake snowflake2 = new Snowflake(args[1]);
                    if (snowflake1.equals(cursorLocation.get(sender))) {
                        cursorLocation.put(sender, snowflake2);
                    } else {
                        cursorLocation.put(sender, snowflake1);
                    }
                    updateChat(sender);
                    return true;
                }
            }else{
                return false;
            }
            return false;
        }
    }
}
