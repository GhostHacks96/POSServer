package me.ghosthacks96.pos.server.utils.console;

import me.ghosthacks96.pos.server.POSServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

import static me.ghosthacks96.pos.server.POSServer.shutdownSystem;
import static me.ghosthacks96.pos.server.POSServer.startServerThread;

public class ConsoleHandler {
    private static final Logger logger = LoggerFactory.getLogger(ConsoleHandler.class);
    private static final Scanner scanner = new Scanner(System.in);
    private static final String PROMPT = ConsoleColors.GREEN_BOLD + "Server> " + ConsoleColors.RESET;
    public static boolean DEBUG = false; // Default to false, can be set via config
    private static volatile String currentInput = "";
    private static volatile boolean waitingForInput = false;

    public ConsoleHandler() {
        // Initialize the console handler
        start();
    }

    public static void start() {
        Thread CThread = new Thread(() -> {
            while (true) {
                waitingForInput = true;
                printPrompt();

                currentInput = scanner.nextLine();
                waitingForInput = false;

                handleCommand(currentInput);
                currentInput = "";
            }
        }, "Console-Input-Thread");
        CThread.setDaemon(true); // Make it a daemon thread
        CThread.start();
    }

    private static void printPrompt() {
        System.out.print(PROMPT);
        System.out.flush();
    }

    private static void handleCommand(String input) {
        input = input.toLowerCase().trim();
        logger.info("Received command: {}", input);
        if(input.equals("quit")) input = "exit"; // Alias for exit command
        switch (input) {
            case "start":
                printInfo("Starting the server...");
                POSServer.running = true;
                startServerThread();
                break;
            case "stop":
                printInfo("Stopping the server...");
                POSServer.running = false;
                POSServer.shutdownServer();
                break;
            case "debug":
                if (!DEBUG) {
                    DEBUG = true;
                    printInfo("Debug mode enabled.");
                } else {
                    printWarning("Debug mode is already enabled.");
                }
                break;
            case "exit":
                printInfo("Exiting the console...");
                shutdownSystem();
                break;
            default:
                printInfo("--Help--");
                printInfo("- start: Start the server");
                printInfo("- stop: Stop the server");
                printInfo("- debug: toggle debug mode (does not change config setting)");
                printInfo("- exit/quit: Shut down the server and exit the console");
                printInfo("- help: Show this help message");
        }
    }

    public static void printInfo(String msg) {
        logger.info(msg);
        printMessage("[INFO] ", ConsoleColors.BLUE_BOLD, msg);
    }

    public static void printWarning(String msg) {
        logger.warn(msg);
        printMessage("[WARN] ", ConsoleColors.YELLOW_BOLD, msg);
    }

    public static void printDebug(String msg) {
        if (DEBUG) {
            logger.debug(msg);
            printMessage("[DEBUG] ", ConsoleColors.CYAN_BOLD, msg);
        }
    }

    public static void printError(String msg) {
        printMessage("[ERROR] ", ConsoleColors.RED_BOLD, msg);
    }

    private static synchronized void printMessage(String prefix, String color, String msg) {
        if (waitingForInput) {
            // Clear the current prompt line
            System.out.print("\r\033[2K"); // Move to beginning and erase line
        }

        // Print the message
        System.out.println(color + prefix + ConsoleColors.RESET + msg);

        if (waitingForInput) {
            // Reprint prompt + restore any typed input (if you track it)
            System.out.print(PROMPT);
            System.out.flush();
        }
    }
}

