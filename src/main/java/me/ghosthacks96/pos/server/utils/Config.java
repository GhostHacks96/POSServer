package me.ghosthacks96.pos.server.utils;

import me.ghosthacks96.pos.server.POSServer;
import me.ghosthacks96.pos.server.utils.console.ConsoleHandler;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.Map;

public class Config {

    Yaml config;
    Map<String,Object> configMap;
    ConsoleHandler console;

    public Config() {

        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setProcessComments(true);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setProcessComments(true);
        loaderOptions.setAllowDuplicateKeys(false); // Prevent duplicate keys in YAML

        this.config = new Yaml(loaderOptions,options);

        console = POSServer.console;
    }

    public void saveDefaultConfig() {
        try (InputStream in = POSServer.class.getClassLoader().getResourceAsStream("config.yml")) {
            this.configMap = config.load(in);
            if (this.configMap == null) {
                console.printError("Failed to load default configuration from config.yml");
            }
            BufferedWriter output = new BufferedWriter(new FileWriter(new File("config.yml")));
            this.config.dump(this.configMap, output);
        } catch (Exception e) {
            console.printError("Failed to save default configuration: " + e.getMessage());
        }
    }

    public static Map<String, Object> getConfig() {
        return configMap;
    }

    public void loadConfig() {
        try {
            this.configMap = config.load(new FileInputStream("config.yml"));
            if (this.configMap == null) {
                console.printError("Failed to load configuration from config.yml");
            }
        } catch (Exception e) {
            console.printError("Failed to load configuration: " + e.getMessage());
        }
    }
}
