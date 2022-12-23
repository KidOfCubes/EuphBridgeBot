package io.github.kidofcubes;

import euphoria.EuphoriaBot;
import euphoria.RoomConnection;
import euphoria.types.Message;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import java.awt.*;
import java.util.*;


import static io.github.kidofcubes.ChatManager.onEuphBridgeChat;
import static io.github.kidofcubes.ColorUtils.nickColor;
import static io.github.kidofcubes.EuphBridgeBot.maximumBridgeLife;

public class EuphBridgeEuphoriaBot extends EuphoriaBot {
    Map<RoomConnection,Message> openBridges = new HashMap<>();
    public EuphBridgeEuphoriaBot(String name) {
        super(name);
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
        }
        if(openBridges.get(connection)!=null){
            if(Objects.equals(message.parent,openBridges.get(connection).parent)){
                onEuphBridgeChat(message);
//                Bukkit.broadcastMessage(euphName(message.sender.name)+": "+message.content);
            }
        }

    }
    public String euphName(String name){
        return ChatColor.of(new Color(nickColor(name))).toString()+name+ChatColor.RESET;
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
