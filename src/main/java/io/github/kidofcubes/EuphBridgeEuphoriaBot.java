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
import java.util.List;


import static io.github.kidofcubes.ChatManager.addMessage;
import static io.github.kidofcubes.ChatManager.rootSnowflake;
import static io.github.kidofcubes.ColorUtils.nickColor;
import static io.github.kidofcubes.Main.*;

public class EuphBridgeEuphoriaBot extends EuphoriaBot {
    Map<RoomConnection,Message> openBridges = new HashMap<>();

    Map<RoomConnection, BiMap<Snowflake,Snowflake>> roomSnowflakeMappings = new HashMap<>(); //local to room

    Map<RoomConnection,Integer> lastMessageTimes = new HashMap<>();
    public EuphBridgeEuphoriaBot(String name) {
        super(name);
        if(bridgeInactivityCheck) {
            new Timer().scheduleAtFixedRate(new TimerTask() {
                public void run() {
                    List<RoomConnection> inactivebridges = new ArrayList<>();
                    lastMessageTimes.forEach((key, value) -> {
                        if ((int) (System.currentTimeMillis() / 1000) - value > (bridgeInactivityPeriod)) {
                            Bukkit.broadcastMessage("Bridge to &" + key.roomName() + " closed due to inactivity");
                            key.sendEuphoriaMessage(new Message("Bridge closed due to inactivity", openBridges.get(key).parent));
                            openBridges.remove(key);
                            inactivebridges.add(key);
                        }
                    });
                    for (RoomConnection connection : inactivebridges) {
                        lastMessageTimes.remove(connection);
                    }

                }
            },0,5*1000);
        }
    }


    @Override
    public void onJoinRoom(RoomConnection connection) {
    }

    @Override
    public void onMessage(Message message, RoomConnection connection) {
        if(message.content.equalsIgnoreCase("!openbridge")){
            if(openBridges.containsKey(connection)){
                openBridges.put(connection,message);
                Bukkit.broadcastMessage(euphName(message.sender.name)+" moved the bridge in &"+connection.roomName());
                connection.sendEuphoriaMessage(new Message("Bridge moved",message.parent));
            }else{
                openBridges.put(connection,message);
                Bukkit.broadcastMessage(euphName(message.sender.name)+" opened a bridge in &"+connection.roomName());
                connection.sendEuphoriaMessage(new Message("Bridge opened",message.parent));
            }
            roomSnowflakeMappings.putIfAbsent(connection, HashBiMap.create());
            roomSnowflakeMappings.get(connection).put(rootSnowflake,message.parent);
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    if(openBridges.containsKey(connection)) {
                        if (openBridges.get(connection).equals(message)) {
                            connection.sendEuphoriaMessage(new Message("Bridge closed", message.parent));
                            Bukkit.broadcastMessage("Bridge in &"+connection.roomName()+" closed");
                            if(bridgeInactivityCheck) {
                                lastMessageTimes.remove(connection);
                            }
                            openBridges.remove(connection);
                        }
                    }
                }
            }, (int)(maximumBridgeLife*1000));
            if(bridgeInactivityCheck) {
                lastMessageTimes.put(connection, (int) (System.currentTimeMillis() / 1000));
            }
            return;

        }
        if(message.content.equalsIgnoreCase("!closebridge")){
            if(openBridges.containsKey(connection)){
                connection.sendEuphoriaMessage(new Message("Bridge closed",message.parent));
                openBridges.remove(connection);
                if(bridgeInactivityCheck) {
                    lastMessageTimes.remove(connection);
                }
            }else{
                connection.sendEuphoriaMessage(new Message("Closed the non-existent bridge",message.parent));
            }
            return;

        }
        if(openBridges.get(connection)!=null){
            if(threadedChat) {
                if (roomSnowflakeMappings.get(connection).containsValue(message.parent)) {
                    roomSnowflakeMappings.get(connection).put(message.id, message.id);
                    message.parent = roomSnowflakeMappings.get(connection).inverse().getOrDefault(message.parent, rootSnowflake);

                    //PARENT IS LOCAL NOW
                    openBridges.forEach((key, value) -> {
                        if (!key.equals(connection) && roomSnowflakeMappings.get(key).containsKey(message.parent)) {
                            key.sendEuphoriaMessage(new Message(message.content, roomSnowflakeMappings.get(key).get(message.parent))).thenAccept((sentMessage) -> {
                                roomSnowflakeMappings.get(key).put(message.id, sentMessage.id);
                            });
                        }
                    });
                    addMessage(message);

                }
            }else{
                addMessage(message);
                openBridges.forEach((key, value) -> {
                    if (!key.equals(connection)) {
                        key.sendEuphoriaMessage(new Message(message.content, value.parent));
                    }
                });
            }
        }

    }
    public String euphName(String name){
        return ChatColor.of(new Color(nickColor(name))).toString()+name+ChatColor.RESET;
    }

    public void uploadLocalMessage(Message localMessage){
        //map message parent
        openBridges.forEach((key, value) -> {
            if(roomSnowflakeMappings.get(key)!=null){
                if(roomSnowflakeMappings.get(key).containsKey(localMessage.parent)){
                    key.setName("<"+localMessage.sender.name+">");
                    key.sendEuphoriaMessage(new Message(localMessage.content,roomSnowflakeMappings.get(key).get(localMessage.parent))).thenAccept((sentMessage) -> {
                        roomSnowflakeMappings.get(key).put(localMessage.id,sentMessage.id);
                    });
                    key.setName(this.name);
                }
            }
        });
    }
    public void bridgeMessage(String name, String message){
        Message msg = new Message(message,null);
        openBridges.forEach((key, value) -> {
            msg.parent = value.parent;
            key.setName(name);
            key.sendEuphoriaMessage(msg);
            key.setName(this.name);
        });
    }

}