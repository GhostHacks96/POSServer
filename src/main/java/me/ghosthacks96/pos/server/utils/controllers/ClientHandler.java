package me.ghosthacks96.pos.server.utils.controllers;

import me.ghosthacks96.pos.server.utils.models.UserModel;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.regex.Pattern;

public class ClientHandler {

    // Define the delimiter and create a compiled pattern for better performance
    private static final String DELIMITER = "[:_:]";
    private static final Pattern DELIMITER_PATTERN = Pattern.compile(Pattern.quote(DELIMITER));
    // Constants for protocol commands
    private static final String CMD_TEST = "TEST";
    private static final String CMD_LOGIN = "LOGIN";
    private static final String CMD_LOGOUT = "LOGOUT";
    // Response constants
    private static final String RESPONSE_OK = "OK";
    private static final String RESPONSE_SUCCESS = "SUCCESS";
    private static final String RESPONSE_FAIL = "FAIL";
    public Thread clientThread;
    UserModel user;
    String ip;
    BufferedWriter output;
    BufferedReader input;

    public ClientHandler(UserModel user, String ip, BufferedWriter output, BufferedReader input) {
        this.user = user;
        this.ip = ip;
        this.output = output;
        this.input = input;
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
                return true;
            } catch (Exception e) {
                System.err.println("Error sending message to client: " + e.getMessage());
            }
        }
        return false;
    }

    /**
     * Safely parse a message and return the command and arguments
     */
    private MessageParts parseMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return new MessageParts("", new String[0]);
        }

        // Use the compiled pattern for better performance
        String[] parts = DELIMITER_PATTERN.split(message.trim());

        if (parts.length == 0) {
            return new MessageParts("", new String[0]);
        }

        String command = parts[0].toUpperCase().trim();
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
    private String buildResponse(String command, String... parts) {
        StringBuilder response = new StringBuilder(command);
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
            sendToClient(buildResponse(command, RESPONSE_FAIL, errorMsg));
            return false;
        }
        return true;
    }

    public void handleClient() {
        clientThread = new Thread(() -> {
            try {
                String message;
                while ((message = input.readLine()) != null) {
                    System.out.println("Received from " + user.getUsername() + ": " + message);

                    MessageParts parsed = parseMessage(message);
                    String cmd = parsed.command;
                    String[] args = parsed.args;

                    switch (cmd) {
                        case CMD_TEST:
                            handleTestCommand();
                            break;

                        case CMD_LOGIN:
                            handleLoginCommand(args);
                            break;

                        case CMD_LOGOUT:
                            handleLogoutCommand();
                            break;

                        default:
                            handleUnknownCommand(cmd);
                            break;
                    }
                }
            } catch (Exception e) {
                System.err.println("Error handling client " + user.getUsername() + ": " + e.getMessage());
                e.printStackTrace();
            } finally {
                closeConnection();
            }
        });
        clientThread.start();
    }

    private void handleTestCommand() {
        sendToClient(buildResponse(CMD_TEST, RESPONSE_OK));
    }

    private void handleLoginCommand(String[] args) {
        if (!validateArgCount(CMD_LOGIN, args, 2)) {
            return;
        }

        String username = args[0];
        String password = args[1];

        // Validate that username and password are not empty
        if (username.isEmpty() || password.isEmpty()) {
            sendToClient(buildResponse(CMD_LOGIN, RESPONSE_FAIL, "Username and password cannot be empty"));
            return;
        }

        sendToClient(buildResponse(CMD_LOGIN, RESPONSE_SUCCESS, username));
    }

    private void handleLogoutCommand() {
        sendToClient(buildResponse(CMD_LOGOUT, RESPONSE_SUCCESS));
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