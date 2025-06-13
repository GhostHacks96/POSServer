package me.ghosthacks96.pos.server;

import me.ghosthacks96.pos.server.utils.Config;
import me.ghosthacks96.pos.server.utils.Logging;
import me.ghosthacks96.pos.server.utils.console.ConsoleHandler;
import me.ghosthacks96.pos.server.utils.controllers.ClientHandler;
import me.ghosthacks96.pos.server.utils.controllers.DatabaseHandler;

import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Set;

public class POSServer {

    public static Config config;
    public static Logging log;
    public static ConsoleHandler console;
    public static DatabaseHandler databaseHandler;



    public static boolean running = false;
    static int port = 666;
    static ArrayList<ClientHandler> clientSockets = new ArrayList<>();

    public static void main(String[] args) {

        console = new ConsoleHandler();

        // Initialize the server
        console.printInfo("Starting POS Server System...");
        config = new Config();

        if (!new File("config.yml").exists()) {
            console.printInfo("No configuration found, creating default configuration...");
            config.saveDefaultConfig();
        }else{
            config.loadConfig();
        }

        port = config.getConfig().get("port") != null ? (int) config.getConfig().get("port") : 666;
        if (config.getConfig().get("auto-start") != null && (boolean) config.getConfig().get("auto-start")) {
            console.printInfo("Auto-start is enabled, starting server thread...");
            startServerThread();
        } else {
            console.printInfo("Auto-start is disabled, please start the server manually.");
        }

        log = new Logging(config);

        console.DEBUG = config.getConfig().get("debug") != null && (boolean) config.getConfig().get("debug");

        String dburl = config.getConfig().get("db_host") != null ? (String) config.getConfig().get("db_host") : "NULL";
        String dbuser = config.getConfig().get("db_username") != null ? (String) config.getConfig().get("db_username") : "NULL";
        String dbpassword = config.getConfig().get("db_password") != null ? (String) config.getConfig().get("db_password") : "NULL";
        int dbport = config.getConfig().get("db_port") != null ? (Integer) config.getConfig().get("db_port") : 3306;
        if(dburl == null || dburl.equals("NULL") || dbuser == null || dbuser.equals("NULL") || dbpassword == null || dbpassword.equals("NULL")) {
            console.printError("Database configuration: URL:"+ dburl + ", User:" + dbuser + ", Password:" + dbpassword + ", Port:" + dbport);
            console.printError("Database configuration is missing or invalid. Please check your config.yml file.");
            System.exit(1);
        } else {
            console.printInfo("Database configuration loaded successfully.");
        }
        databaseHandler = new DatabaseHandler(dburl,dbuser,dbpassword,dbport);


    }

    public static void shutdownSystem() {
       try{
           running = false;
           console.printInfo("Shutting down POS Server... sending kill signal to all clients.");

           if(clientSockets.size() > 0) {
               for (ClientHandler client : clientSockets) {
                   if (client.isConnected()) {
                       client.sendToClient("CMD[:_:]SHUTDOWN");
                       client.clientThread.interrupt();
                       console.printInfo("Sent shutdown signal to client: " + client.getIp());
                   }
               }
           } else {
               console.printInfo("No clients connected.");
           }

           log.onShutdown();
       }catch (Exception e) {
           console.printError("Error during shutdown: " + e.getMessage());
       }
        System.exit(0);
    }

    public static void startServerThread() {
        running = true;
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                console.printInfo("POS Server started on port " + port + ". Waiting for clients...");

                while (running) {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    if(clientSockets.contains(clientHandler)) {
                        console.printInfo("Client already connected: " + clientHandler.getIp());
                        clientHandler.sendToClient("CMD[:_:]ALREADY_CONNECTED");
                    }else{
                        clientSockets.add(clientHandler);
                    }
                    console.printInfo("New client connected: " + clientHandler.getIp());

                }
                shutdownSystem();
            } catch (Exception e) {
                console.printError("Error starting server: " + e.getMessage());

            }
        }).start();
    }

    public static void shutdownServer() {
        running = false;
        console.printInfo("Shutting down server...");

        if (clientSockets != null && !clientSockets.isEmpty()) {
            for (ClientHandler client : clientSockets) {
                if (client.isConnected()) {
                    client.sendToClient("CMD[:_:]SHUTDOWN");
                    client.clientThread.interrupt();
                    console.printInfo("Sent shutdown signal to client: " + client.getIp());
                }
            }
        } else {
            console.printInfo("No clients connected.");
        }

        log.onShutdown();
        console.printInfo("Server shutdown complete.");
    }
}
