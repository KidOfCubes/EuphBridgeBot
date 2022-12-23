package io.github.kidofcubes;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import euphoria.EuphoriaBot;
import euphoria.RoomConnection;
import euphoria.types.Message;
import euphoria.types.Snowflake;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import java.awt.*;
import java.util.*;


import static io.github.kidofcubes.ChatManager.addMessage;
import static io.github.kidofcubes.ChatManager.rootSnowflake;
import static io.github.kidofcubes.ColorUtils.nickColor;
import static io.github.kidofcubes.EuphBridgeBot.maximumBridgeLife;

public class EuphBridgeEuphoriaBot extends EuphoriaBot {
    Map<RoomConnection,Message> openBridges = new HashMap<>();

    Map<RoomConnection, BiMap<Snowflake,Snowflake>> roomSnowflakeMappings = new HashMap<>(); //local to room
    public EuphBridgeEuphoriaBot(String name) {
        super(name);
    }


    @Override
    public void onJoinRoom(RoomConnection connection) {
    }

    @Override
    public void onMessage(Message message, RoomConnection connection) {
        if(message.content.equalsIgnoreCase("!openbridge")){
            if(openBridges.containsKey(connection)){
                Bukkit.broadcastMessage(euphName(message.sender.name)+" moved the bridge in &"+connection.roomName());
                connection.sendEuphoriaMessage(new Message("Bridge moved",message.parent));
            }else{
                Bukkit.broadcastMessage(euphName(message.sender.name)+" opened a bridge in &"+connection.roomName());
                connection.sendEuphoriaMessage(new Message("Bridge opened",message.parent));
            }
            openBridges.put(connection,message);
            roomSnowflakeMappings.putIfAbsent(connection, HashBiMap.create());
            roomSnowflakeMappings.get(connection).put(rootSnowflake,message.parent);
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    if(openBridges.get(connection).equals(message)){
                        openBridges.remove(connection);
                        connection.sendEuphoriaMessage(new Message("Bridge closed",message.parent));

                        System.out.println("BRIDGE CLOSED");
                    }
                }
            }, (int)(maximumBridgeLife*1000));
            return;

        }
        if(openBridges.get(connection)!=null){
            System.out.println("DIDNT ADD a message ID:"+message.id+" PARENT: "+message.parent+" CONTENT:"+message.content);
            System.out.println("CORECT PARENT ID WASSSSSSSSSSSSSSSSSSSSSSSSS: "+openBridges.get(connection).parent);
            if(roomSnowflakeMappings.get(connection).containsValue(message.parent)){
                //NEED TO MAP TO LOCAL SNOWFLAKES FIRST
                System.out.println("ITS HERE THE WORDS ARE HERE ");
                message.parent=roomSnowflakeMappings.get(connection).inverse().getOrDefault(message.parent,rootSnowflake);
                roomSnowflakeMappings.get(connection).put(message.id,message.id);
                addMessage(message);
//                Bukkit.broadcastMessage(euphName(message.sender.name)+": "+message.content);
            }
        }

    }
    public String euphName(String name){
        return ChatColor.of(new Color(nickColor(name))).toString()+name+ChatColor.RESET;
    }

    public void sendToBridges(Message message){
        openBridges.forEach((key, value) -> {
            sendToBridge(key,message);
        });
    }
    public void sendToBridge(RoomConnection roomConnection, Message message){
        //map snowflake

    }
    public void uploadLocalMessage(Message localMessage){
        //map message parent
        openBridges.forEach((key, value) -> {
            if(roomSnowflakeMappings.get(key)!=null){
                if(roomSnowflakeMappings.get(key).get(localMessage.parent)!=null){
                    key.sendEuphoriaMessage(new Message(localMessage.content,roomSnowflakeMappings.get(key).get(localMessage.parent))).thenAccept((sentMessage) -> {
                        roomSnowflakeMappings.get(key).put(localMessage.id,sentMessage.id);
                    });
                }
            }
        });
    }

    public void allBridgesBroadcast(String name, String text){
        openBridges.forEach((key, value) -> {
            if (name != null) {
                key.setName(name);
            }
            key.sendEuphoriaMessage(new Message(text, value.parent));
            if (name != null) {
                key.setName(name);
            }
        });
    }


}
