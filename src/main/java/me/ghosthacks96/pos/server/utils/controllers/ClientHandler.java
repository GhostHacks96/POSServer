package me.ghosthacks96.pos.server.utils.controllers;

import me.ghosthacks96.pos.server.POSServer;
import me.ghosthacks96.pos.server.utils.models.UserModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.util.regex.Pattern;

import static me.ghosthacks96.pos.server.POSServer.console;

public class ClientHandler {

    // Define the delimiter and create a compiled pattern for better performance
    private static final String DELIMITER = "[:_:]";
    private static final Pattern DELIMITER_PATTERN = Pattern.compile(Pattern.quote(DELIMITER));
    // Constants for protocol commands
    private static final String CMD_TEST = "TEST";
    private static final String CMD_LOGIN = "LOGIN";
    private static final String CMD_LOGOUT = "LOGOUT";
    private static final String CMD_DISCONNECT = "DISCONNECT";
    private static final String CMD = "CMD";
    private static final String REC = "REC";
    private static final String TRA = "TRA";
    private static final String DAT = "DAT";

    // Response constants
    private static final String RESPONSE_OK = "OK";
    private static final String RESPONSE_SUCCESS = "SUCCESS";
    private static final String RESPONSE_FAIL = "FAIL";
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    public Thread clientThread;
    public Socket socket;
    UserModel user;
    String ip;
    BufferedWriter output;
    BufferedReader input;

    private static final String[] ALLOWED_PREFIXES = {"CMD", "REC", "TRA", "DAT"};

    public ClientHandler(Socket socket) throws Exception {
        this.socket = socket;
        this.ip = socket.getInetAddress().getHostAddress();
        this.output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        sendToClient("CMD"+DELIMITER+"LOGINREQUEST");
        handleClient();
    }

    public String getUsername() {
        return user.getUsername();
    }

    public String getIp() {
        return ip;
    }

    public boolean isConnected() {
        return output != null && input != null;
    }

    public boolean sendToClient(String message) {
        if (output != null) {
            try {
                output.write(message);
                output.newLine();
                output.flush();
                if (POSServer.config != null && console.DEBUG) logger.debug("Sent to client {}: {}", ip, message);
                return true;
            } catch (Exception e) {
                logger.error("Error sending message to client {}: {}", ip, e.getMessage(), e);
            }
        }
        return false;
    }

    /**
     * Safely parse a message and return the command and arguments
     */
    private MessageParts parseMessage(String message) {
        if (POSServer.config != null && console.DEBUG) logger.debug("Parsing message from client {}: {}", ip, message);
        if (message == null || message.trim().isEmpty()) {
            return new MessageParts("", new String[0]);
        }

        // Use the compiled pattern for better performance
        String[] parts = DELIMITER_PATTERN.split(message.trim());

        if (parts.length == 0) {
            return new MessageParts("", new String[0]);
        }

        String command = parts[0].toUpperCase().trim();
        boolean validPrefix = false;
        for (String prefix : ALLOWED_PREFIXES) {
            if (command.startsWith(prefix)) {
                validPrefix = true;
                break;
            }
        }
        if (!validPrefix) {
            sendToClient(buildResponse("ERROR", RESPONSE_FAIL, "Invalid message prefix. Must start with CMD, REC, TRA, or DAT."));
            return new MessageParts("", new String[0]);
        }

        String[] args = new String[parts.length - 1];

        // Copy arguments, handling potential null/empty values
        for (int i = 1; i < parts.length; i++) {
            args[i - 1] = parts[i] != null ? parts[i].trim() : "";
        }

        return new MessageParts(command, args);
    }

    /**
     * Build a response message with proper delimiter
     */
    private String buildResponse(String type,String command, String... parts) {
        StringBuilder response = new StringBuilder(type + DELIMITER + command);
        for (String part : parts) {
            response.append(DELIMITER).append(part != null ? part : "");
        }
        return response.toString();
    }

    /**
     * Validate that we have the expected number of arguments
     */
    private boolean validateArgCount(String command, String[] args, int expectedCount) {
        if (args.length != expectedCount) {
            String errorMsg = String.format("Invalid argument count for %s. Expected: %d, Got: %d",
                    command, expectedCount, args.length);
            sendToClient(buildResponse(CMD,command, RESPONSE_FAIL, errorMsg));
            return false;
        }
        return true;
    }

    public void handleClient() {
        clientThread = new Thread(() -> {
            try {
                String message;
                while ((message = input.readLine()) != null && POSServer.running) {
                    System.out.println("Received from " + ip + ": " + message);

                    MessageParts parsed = parseMessage(message);
                    String cmd = parsed.command;
                    String[] args = parsed.args;

                    switch(cmd){
                        case "CMD":
                            handleCMD(args);
                            break;
                        case "REC":
                            handleRec(args);
                            break;
                        case "TRA":
                            handleTra(args);
                            break;
                        case "DAT":
                            handleDat(args);
                            break;
                        default:
                            handleUnknownCommand(message);
                            break;
                    }
                }
            } catch (Exception e) {
                console.printError("Error handling client " + ip + ": " + e.getMessage());
                logger.error("Error handling client {}: {}", ip, e.getMessage(), e);
            } finally {
                closeConnection();
            }
        });
        clientThread.setDaemon(true);
        clientThread.start();
    }

    private void handleDat(String[] args) {
        if (args.length > 0) {
            DatabaseHandler db = POSServer.databaseHandler;
            StringBuilder sb = new StringBuilder();
            switch (args[0].toUpperCase()) {
                case "PROD_LIST":
                    var products = db.getAllProducts();
                    for (var product : products) {
                        sb.append(product.get("id")).append("|")
                          .append(product.get("name")).append("|")
                          .append(product.get("description")).append("|")
                          .append(product.get("price")).append("|")
                          .append(product.get("stock")).append(";");
                    }
                    if (sb.length() > 0) sb.setLength(sb.length() - 1);
                    sendToClient(buildResponse(DAT, "PROD_LIST", sb.toString()));
                    break;
                case "U_PERMS":
                        if(args.length >1){
                            var userlist = db.getAllUsers();
                            var found = false;
                            for (var user : userlist) {
                                if (user.getUsername().equalsIgnoreCase(args[1])) {
                                    for(var perm : user.getPermissions()) {
                                        sb.append(perm).append("|");
                                    }
                                    found = true;
                                    break;
                                }
                            }
                            if (user != null) {
                                if (!sb.isEmpty()) sb.setLength(sb.length() - 1);
                                sendToClient(buildResponse(DAT, "U_PERMS", args[1], sb.toString()));
                            } else {
                                sendToClient(buildResponse(DAT, "U_PERMS", RESPONSE_FAIL, "User not found"));
                            }
                        } else {
                            sendToClient(buildResponse(DAT, "U_PERMS", RESPONSE_FAIL, "Username required"));
                        }

                case "U_DATA":
                    if (args.length > 1) {
                        var usersList = db.getAllUsers();
                        var found = false;
                        for (var user : usersList) {
                            if (user.getUsername().equalsIgnoreCase(args[1])) {
                                sb.append(user.getUsername()).append("|")
                                  .append(user.isAdmin()).append("|")
                                  .append(user.isActive()).append("|")
                                  .append(user.getLastLogin());
                                found = true;
                                break;
                            }
                        }
                        if (found) {
                            sendToClient(buildResponse(DAT, "U_DATA", args[1], sb.toString()));
                        } else {
                            sendToClient(buildResponse(DAT, "U_DATA", RESPONSE_FAIL, "User not found"));
                        }
                    } else {
                        sendToClient(buildResponse(DAT, "U_DATA", RESPONSE_FAIL, "Username required"));
                    }
                    break;
                case "TRANSACTION":
                    if (args.length > 1) {
                        var txn = db.getTransactionById(args[1]);
                        if (txn.isEmpty()) {
                            sendToClient(buildResponse(DAT, "TRANSACTION", RESPONSE_FAIL, "Transaction not found"));
                        } else {
                            for (var entry : txn.entrySet()) {
                                sb.append(entry.getKey()).append("=").append(entry.getValue()).append("|");
                            }
                            if (sb.length() > 0) sb.setLength(sb.length() - 1);
                            sendToClient(buildResponse(DAT, "TRANSACTION", args[1], sb.toString()));
                        }
                    } else {
                        sendToClient(buildResponse(DAT, "TRANSACTION", RESPONSE_FAIL, "Transaction ID required"));
                    }
                    break;
                default:
                    sendToClient(buildResponse(DAT, args[0], RESPONSE_FAIL, "Unknown DAT command"));
                    break;
            }
        } else {
            sendToClient(buildResponse(DAT, "UNKNOWN", RESPONSE_FAIL, "Unknown DAT command"));
        }
    }

    private void handleTra(String[] args) {
    }

    private void handleRec(String[] args) {
    }

    private void handleCMD(String[] args) {
        switch (args[0]) {
            case CMD_TEST:
                handleTestCommand();
                break;
            case CMD_LOGIN:
                handleLoginCommand(args);
                break;
            case CMD_LOGOUT:
                handleLogoutCommand();
                break;
            case CMD_DISCONNECT:
                handleDisconnectCommand();
                break;
            default:
                handleUnknownCommand(args[0]);
                break;
        }
    }

    private void handleTestCommand() {
        sendToClient(buildResponse(CMD, CMD_TEST, RESPONSE_OK));
    }

    private void handleLoginCommand(String[] args) {
        if (!validateArgCount(CMD_LOGIN, args, 3)) {
            return;
        }
        String username = args[1];
        String password = args[2];
        if (username.isEmpty() || password.isEmpty()) {
            sendToClient(buildResponse(CMD, CMD_LOGIN, RESPONSE_FAIL, "Username and password cannot be empty"));
            return;
        }
        // Authenticate user using the database handler
        UserModel authenticatedUser = POSServer.databaseHandler.authenticateUser(username, password);
        if (authenticatedUser != null) {
            this.user = authenticatedUser;
            sendToClient(buildResponse(CMD, CMD_LOGIN, RESPONSE_SUCCESS, "Login successful"));
        } else {
            sendToClient(buildResponse(CMD, CMD_LOGIN, RESPONSE_FAIL, "Invalid username or password"));
        }
    }

    private void handleLogoutCommand() {
        sendToClient(buildResponse(CMD,CMD_LOGOUT, RESPONSE_SUCCESS));
    }

    private void handleDisconnectCommand() {
        sendToClient(buildResponse(CMD, CMD_DISCONNECT, RESPONSE_SUCCESS, "Disconnected"));
        closeConnection();
    }

    private void handleUnknownCommand(String command) {
        sendToClient(buildResponse("ERROR", RESPONSE_FAIL, "Unknown command: " + command));
    }

    private void closeConnection() {
        try {
            if (output != null) {
                output.close();
            }
            if (input != null) {
                input.close();
            }
        } catch (Exception e) {
            System.err.println("Error closing connection for " + user.getUsername() + ": " + e.getMessage());
        } finally {
            output = null;
            input = null;
        }
    }

    /**
     * Helper class to hold parsed message parts
     */
    private static class MessageParts {
        final String command;
        final String[] args;

        MessageParts(String command, String[] args) {
            this.command = command;
            this.args = args;
        }
    }
}



