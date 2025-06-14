package me.ghosthacks96.pos.server;

import me.ghosthacks96.pos.server.utils.Config;
import me.ghosthacks96.pos.server.utils.RemoteFile;
import me.ghosthacks96.pos.server.utils.console.ConsoleHandler;
import me.ghosthacks96.pos.server.utils.controllers.ClientHandler;
import me.ghosthacks96.pos.server.utils.controllers.DatabaseHandler;
import me.ghosthacks96.pos.server.utils.controllers.LogfileHandler;
import me.ghosthacks96.pos.server.utils.controllers.WebInterfaceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class POSServer {

    public static Config config;
    public static ConsoleHandler console;
    public static DatabaseHandler databaseHandler;

    public static JmDNS jmdns;

    private static Logger logger;

    public static boolean running = false;
    static int port = 666;
    static ArrayList<ClientHandler> clientSockets = new ArrayList<>();
    private static LogfileHandler logfileHandler = new LogfileHandler();
    static WebInterfaceHandler webInterface;

    public static void main(String[] args) throws Exception{

        // Move previous log if it exists and is not locked
        File prevLog = new File("logs/latest.log");
        if (prevLog.exists()) {
            String archiveName = "logs/" + new java.text.SimpleDateFormat("dd-MM-yy-hh-mm-ss").format(new java.util.Date(prevLog.lastModified())) + ".log";
            try {
                java.nio.file.Files.move(prevLog.toPath(), new File(archiveName).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                // If file is locked or move fails, just continue
                System.err.println("Could not archive previous log: " + e.getMessage());
            }
        }

        logger = LoggerFactory.getLogger(POSServer.class);

        console = new ConsoleHandler();

        logger.info("Starting POS Server System...");
        console.printInfo("Starting POS Server System...");
        config = new Config();

        if (!new File("config.yml").exists()) {
             logger.warn("No configuration found, creating default configuration...");
            console.printInfo("No configuration found, creating default configuration...");
            config.saveDefaultConfig();
        }else{
            logger.info("Loading configuration from config.yml");
            config.loadConfig();
        }

        port = config.getConfig().get("port") != null ? (int) config.getConfig().get("port") : 666;
        if (console.DEBUG) logger.debug("Server port set to: {}", port);

        if (console.DEBUG) logger.debug("Server claiming internal net url.");
        console.printInfo("Server claiming internal net url.");

        Map<String, String> properties = new HashMap<>();
        properties.put("version", "1.0");
        properties.put("description", "POS Server");

        ServiceInfo serviceInfo = ServiceInfo.create(
                "_poserver._tcp.local.",           // Custom service type for chat
                "POS_Server",                   // Service name (e.g., "MyChat")
                port,                         // Port number
                0,                            // Weight (0 = default)
                0,                            // Priority (0 = default)
                properties                    // Additional properties
        );


        if (console.DEBUG) logger.debug("Registering mDNS service with properties: {}", properties);
        jmdns = JmDNS.create(InetAddress.getLocalHost());

        jmdns.registerService(serviceInfo);
        if (console.DEBUG) logger.debug("Service registered: _posserver._tcp.local");
        console.printInfo("Service registered: _posserver._tcp.local");


        if (config.getConfig().get("auto-start") != null && (boolean) config.getConfig().get("auto-start")) {
            if (console.DEBUG) logger.debug("Auto-start is enabled, starting server thread...");
            console.printInfo("Auto-start is enabled, starting server thread...");
            startServerThread();
        } else {
            if (console.DEBUG) logger.debug("Auto-start is disabled, please start the server manually.");
            console.printInfo("Auto-start is disabled, please start the server manually.");
        }


        console.DEBUG = config.getConfig().get("debug") != null && (boolean) config.getConfig().get("debug");

        if(config.getConfig().get("db_getremote") != null && (boolean) config.getConfig().get("db_getremote")) {
            console.printInfo("Remote database access enabled, please ensure the database file is accessible.");
            new RemoteFile(config.getConfig().get("db-r-host")+"").download();
        } else {
            console.printInfo("Remote database access disabled, using local database.");
        }

        databaseHandler = new DatabaseHandler(config.getConfig().get("db_file") != null ? (String) config.getConfig().get("db_file") : "pos.db");


        if(console.DEBUG) logger.debug("Starting Web Interface");
        webInterface = new WebInterfaceHandler(8080);

        // Add shutdown hook to gracefully stop the server
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            console.printInfo("Shutting down web interface...");
            webInterface.stop();
        }));

        try {
            webInterface.start();
            webInterface.join(); // Keep the server running
        } catch (Exception e) {
            System.err.println("Failed to start web interface: " + e.getMessage());
            e.printStackTrace();
        }

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

           config.saveConfig();
           console.printInfo("Unregistering local url...");
           jmdns.unregisterAllServices();
           jmdns.close();

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

        console.printInfo("Server shutdown complete.");
    }

}
