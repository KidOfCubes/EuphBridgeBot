package io.github.kidofcubes;

import euphoria.types.Message;
import euphoria.types.SessionView;
import euphoria.types.Snowflake;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.md_5.bungee.api.ChatColor;
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

import static java.util.concurrent.TimeUnit.*;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;

import static io.github.kidofcubes.ColorUtils.nickColor;
import static io.github.kidofcubes.EuphBridgeBot.coloredMinecraftNames;
import static io.github.kidofcubes.EuphBridgeBot.mainPluginInstance;

public class ChatManager implements Listener {
    static Snowflake rootSnowflake = new Snowflake("0".repeat(Snowflake.length));

    static Map<Player,Map<Integer,List<Object>>> playerMessagePacketCache = new HashMap<>();

    static List<Map.Entry<Message, Integer>> bridgedMessages = new ArrayList<>(); //key is parent of messages, value.first is parent message
//    static List<Map.Entry<Message,Integer>> bridgedMessages2 = new ArrayList<>(); //int is depth of message

    static Map<CommandSender,Snowflake> cursorLocation = new HashMap<>(); //value is parent of thread im on
    public static EuphBridgeEuphoriaBot botInstance;
//ChatColor.of(Color.getHSBColor((float)(Math.random()*10000),(float)Math.random(),(float)Math.random())).toString()
    public static final String threadTabber = "   ";

    public ChatManager(EuphBridgeEuphoriaBot botInstance){
        ChatManager.botInstance =botInstance;
    }

    public static void onEuphBridgeChat(Message message){
        if(message.parent==null) {
            message.parent=rootSnowflake;
        }
        int depth = -5;
        for(int i=0;i<bridgedMessages.size();i++){
            if(bridgedMessages.get(i).getValue()<depth&&depth!=-5){
                bridgedMessages.add(i,new AbstractMap.SimpleEntry<>(message,depth));
                depth = -4;
                break;
            }
            if(bridgedMessages.get(i).getKey().id==message.parent){
                depth = bridgedMessages.get(i).getValue()+1;
            }
        }
        if(depth!=-5&&depth!=-4){
            bridgedMessages.add(new AbstractMap.SimpleEntry<>(message,depth));
        }

//        bridgedMessages2.add(new AbstractMap.SimpleEntry<>(message,getDepthOfMessage(message)));
        updateAllChats();
    }

    public static void updateAllChats(){
        Bukkit.getServer().getOnlinePlayers().forEach(ChatManager::updateChat);
    }

    public static int getEndOfThread(Snowflake id){
        System.out.println("looking for "+id.toString());
        for(int i=bridgedMessages.size()-1;i>=0;i--){
            System.out.println("found "+bridgedMessages.get(i).getKey().parent+" while looking for "+id);
            if(bridgedMessages.get(i).getKey().id.equals(id)){
                return i;
            }
            if(bridgedMessages.get(i).getKey().parent.equals(id)){
                int foundIndex = bridgedMessages.size()-1;
                for(int j=i;j<bridgedMessages.size();j++){
                    if(bridgedMessages.get(j).getValue()<bridgedMessages.get(i).getValue()){
                        foundIndex=j-1;
                        break;
                    }
                }
                System.out.println("actually found "+id);
                return foundIndex;
            }
        }
        return -1;
    }


    public static void updateChat(CommandSender player){
//            cursorLocation.putIfAbsent(player,1000);

        int indexof = getEndOfThread(cursorLocation.get(player));
        System.out.println("indexof is "+indexof+" start is "+Math.max(indexof-100,1)+" and end is "+Math.min(indexof+1,bridgedMessages.size()));
        for(int i=0;i<bridgedMessages.size();i++){
            System.out.println("bridgedMessages["+i+"]="+bridgedMessages.get(i).getKey().id+", "+bridgedMessages.get(i).getValue());
        }
        List<Map.Entry<Message,Integer>> focusedLines =
                bridgedMessages.subList(
                        Math.max(indexof-100,1),
                        Math.min(indexof+1,bridgedMessages.size()));
        List<net.kyori.adventure.text.Component> lines = new ArrayList<>(); //length 100

        for (Map.Entry<Message,Integer> entry: focusedLines) {
            System.out.println("depth1 = "+entry.getValue()+" and msg id is "+entry.getKey().id);
            lines.add(
                    net.kyori.adventure.text.Component.text(
                            threadTabber.repeat(entry.getValue())+entry.getKey().sender.name+": "+entry.getKey().content
                    ).clickEvent(ClickEvent.runCommand("/setcursor "+entry.getKey().id))
            );
        }


        //add cursor line
        int cursorLength = bridgedMessages.get(indexof).getKey().id.equals(cursorLocation.get(player)) ? bridgedMessages.get(indexof).getValue()+1 : bridgedMessages.get(indexof).getValue();
        lines.add(
                Component.text(threadTabber.repeat(cursorLength)).append(
                        Component.text(">"+player.getName()+"<").color(TextColor.color(255,0,255)).append(
                                Component.text(":______________").color(TextColor.color(255,255,255)))));

        //add small aftermessages

        focusedLines=(bridgedMessages.subList(
                Math.min(indexof+1,bridgedMessages.size()),
                Math.min(indexof+4,bridgedMessages.size())));

        for (Map.Entry<Message,Integer> entry: focusedLines) {
            System.out.println("depth2 = "+entry.getValue());
            lines.add(
                    net.kyori.adventure.text.Component.text(
                            threadTabber.repeat(entry.getValue())+entry.getKey().sender.name+": "+entry.getKey().content
                    ).clickEvent(ClickEvent.runCommand("/setcursor "+entry.getKey().id))
            );
        }









        while(lines.size()<100){
            lines.add(0, net.kyori.adventure.text.Component.text(""));
        }
//            System.out.println("UPDATD CHAT FOR "+player.getName());
        lines.forEach(line -> {
            ((CraftPlayer)player).getHandle().connection.getConnection().channel.write(new ClientboundSystemChatPacket(line, false));
        });
        if(playerMessagePacketCache.get(player)!=null){
            for (List<Object> messagePackets: playerMessagePacketCache.get(player).values()) {
                messagePackets.forEach(packetObject -> {
//                    if(packetObject instanceof ClientboundSystemChatPacket packet){
                        ((CraftPlayer)player).getHandle().connection.getConnection().channel.write(packetObject);
//                    }
//
//                    if(packetObject instanceof ClientboundPlayerChatPacket packet){
//                        ((CraftPlayer)player).getHandle().connection.connection.channel.write(packet);
//                    }
                });
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
        Bukkit.getScheduler().runTaskTimer(mainPluginInstance, new Runnable() {
            @Override
            public void run() {
                tickScheduled=false;
            }
        }, 0,1);
        Bukkit.getServer().getOnlinePlayers().forEach(ChatManager::listenToPlayer);
        Message fakeMsg = new Message(null);
        fakeMsg.id=rootSnowflake;
        fakeMsg.parent=rootSnowflake;
        mainPluginInstance.getCommand("setcursor").setExecutor(new SetCursorCommand());
        bridgedMessages.add(new AbstractMap.SimpleEntry<>(fakeMsg,-1));


    }

    public static void disable(){
        bridgedMessages.clear();
//        Bukkit.getServer().getOnlinePlayers().forEach(player -> {((CraftPlayer)player).getHandle().connection.connection.channel.pipeline().remove("threadingChatHandler");});
    }


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event){

        Bukkit.getScheduler().runTaskLater(mainPluginInstance, new Runnable() {
            @Override
            public void run() {
                listenToPlayer(event.getPlayer());
            }
        }, 10);
    }
    static void listenToPlayer(Player player){
        cursorLocation.put(player,rootSnowflake);
        ((CraftPlayer)player).getHandle().connection=new PlayerChatIntercepter(((CraftPlayer)player).getHandle().server,((CraftPlayer)player).getHandle().connection.connection,((CraftPlayer)player).getHandle().connection.player, player);
    }
    static class PlayerChatIntercepter extends ServerGamePacketListenerImpl{

        Player playerr;
        public PlayerChatIntercepter(MinecraftServer server, Connection connection, ServerPlayer player, Player player2) {
            super(server, connection, player);
            playerr=player2;
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener callbacks) {
            if(packet instanceof ClientboundSystemChatPacket || packet instanceof ClientboundPlayerChatPacket){
                System.out.println("WE GOT ONE ");
                int currentTick = Bukkit.getServer().getCurrentTick();
                playerMessagePacketCache.putIfAbsent(playerr, new HashMap<>());
                playerMessagePacketCache.get(playerr).putIfAbsent(currentTick, new ArrayList<>());
//                        playerMessagePacketCache.get(playerr).get(currentTick).add((new ClientboundSystemChatPacket(packet.adventure$content(), identifierString + packet.content(), packet.overlay())));
                playerMessagePacketCache.get(playerr).get(currentTick).add(packet);
                if (!tickScheduled) {
                    scheduler.schedule(new Runnable() {
                        public void run() {
                            for (Map.Entry<Player, Map<Integer, List<Object>>> entry : playerMessagePacketCache.entrySet()) {
                                if (entry.getValue().remove(currentTick) != null) {
                                    updateChat(entry.getKey());
                                }
                                ;
                            }
                        }
                    }, 10, SECONDS);
                    tickScheduled = true;
                }
            }
            super.send(packet, callbacks);

        }
    }

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    static boolean tickScheduled = false;


    public static final String playerNameMatcher = Matcher.quoteReplacement("%1$s");

    @EventHandler(priority=EventPriority.HIGH)
    public static void onMCChat(AsyncPlayerChatEvent event){


        botInstance.allBridgesBroadcast("<"+event.getPlayer().getName()+">",event.getMessage());

        int indexof= getEndOfThread(cursorLocation.get(event.getPlayer()));
        int depth = bridgedMessages.get(indexof).getKey().id.equals(cursorLocation.get(event.getPlayer())) ? bridgedMessages.get(indexof).getValue()+1 : bridgedMessages.get(indexof).getValue();

        Message fakemsg = fakeMessage(event.getPlayer(),event.getMessage(),cursorLocation.get(event.getPlayer()));
        bridgedMessages.add(indexof+1, new AbstractMap.SimpleEntry<>(fakemsg,depth));
//        bridgedMessages2.add(new AbstractMap.SimpleEntry<>(fakemsg,getDepthOfMessage(fakemsg)));


        if(coloredMinecraftNames){
            String newFormat = event.getFormat().replaceFirst(playerNameMatcher,
                ChatColor.of(new Color(nickColor(event.getPlayer().getName()))).toString()+
                    playerNameMatcher+
                    ChatColor.RESET);
            event.setFormat(newFormat);
        }
        Bukkit.getScheduler().runTaskLater(mainPluginInstance, new Runnable() {
            @Override
            public void run() {
                updateAllChats();
            }
        }, 1);
        event.setCancelled(true);


    }
    public static class SetCursorCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

            if(args.length>0){
                cursorLocation.put(sender,new Snowflake(args[0]));
                updateChat(sender);
                return true;
            }
            return false;
        }
    }
//    @EventHandler(priority = EventPriority.MONITOR)
}
