package me.ghosthacks96.pos.server.utils.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class TemplateLoader {
    private static final Logger logger = LoggerFactory.getLogger(TemplateLoader.class);

    /**
     * Load HTML template from resources
     */
    public static String loadTemplate(String templateName) {
        String templatePath = "/web/templates/" + templateName;

        try (InputStream inputStream = TemplateLoader.class.getResourceAsStream(templatePath)) {
            if (inputStream != null) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            } else {
                logger.error("Template not found: {}", templatePath);
                return getErrorTemplate("Template not found: " + templateName);
            }
        } catch (IOException e) {
            logger.error("Error loading template: {}", templatePath, e);
            return getErrorTemplate("Error loading template: " + templateName);
        }
    }

    /**
     * Load template and replace placeholders with actual data
     */
    public static String loadTemplate(String templateName, Map<String, String> replacements) {
        String template = loadTemplate(templateName);

        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }

        return template;
    }

    private static String getErrorTemplate(String errorMessage) {
        return """
            <!DOCTYPE html>
            <html>
            <head><title>Error</title></head>
            <body>
                <h1>Template Error</h1>
                <p>%s</p>
            </body>
            </html>
            """.formatted(errorMessage);
    }
}