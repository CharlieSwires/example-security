package com.example.security.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Fails startup before serving traffic when required external-service settings are unsafe. */
@Component
public class ExternalServicesConfigurationValidator implements ApplicationRunner {

    private final Environment environment;
    private final boolean required;

    public ExternalServicesConfigurationValidator(
            Environment environment,
            @Value("${app.external-services.require:false}") boolean required) {
        this.environment = environment;
        this.required = required;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!required) {
            return;
        }

        List<String> errors = new ArrayList<>();
        String mongoUri = value("spring.data.mongodb.uri");
        if (placeholder(mongoUri) || !mongoUri.startsWith("mongodb+srv://")) {
            errors.add("MONGODB_URI must be a non-placeholder MongoDB Atlas mongodb+srv:// URI");
        } else {
            String authorityAndPath = mongoUri.substring("mongodb+srv://".length());
            int at = authorityAndPath.lastIndexOf('@');
            int slash = authorityAndPath.indexOf('/', Math.max(0, at));
            int query = authorityAndPath.indexOf('?', slash + 1);
            int databaseEnd = query < 0 ? authorityAndPath.length() : query;
            if (at < 1 || slash < at || slash + 1 >= databaseEnd) {
                errors.add("MONGODB_URI must include Atlas credentials, cluster host, and database name");
            }
        }

        requireExternal(errors, "MAIL_HOST", "spring.mail.host", "mailpit", "localhost", "127.0.0.1");
        requireValue(errors, "MAIL_USERNAME", "spring.mail.username");
        requireValue(errors, "MAIL_PASSWORD", "spring.mail.password");
        requireValue(errors, "MAIL_FROM", "app.mail.from");
        requireTrue(errors, "MAIL_SMTP_AUTH", "spring.mail.properties.mail.smtp.auth");
        requireTrue(errors, "MAIL_SMTP_STARTTLS", "spring.mail.properties.mail.smtp.starttls.enable");
        requireValue(errors, "INITIAL_SUPER_USERNAME", "app.initial-super.username");
        requireValue(errors, "INITIAL_SUPER_PASSWORD", "app.initial-super.password");

        String port = value("spring.mail.port");
        try {
            int parsed = Integer.parseInt(port);
            if (parsed < 1 || parsed > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            errors.add("MAIL_PORT must be an integer from 1 to 65535");
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("External service configuration is invalid: "
                    + String.join("; ", errors));
        }
    }

    private void requireValue(List<String> errors, String envName, String propertyName) {
        if (placeholder(value(propertyName))) {
            errors.add(envName + " is required and must not contain a placeholder");
        }
    }

    private void requireExternal(List<String> errors, String envName, String propertyName, String... forbidden) {
        String value = value(propertyName);
        if (placeholder(value)) {
            errors.add(envName + " is required and must not contain a placeholder");
            return;
        }
        for (String item : forbidden) {
            if (value.equalsIgnoreCase(item)) {
                errors.add(envName + " must name an external service, not " + item);
                return;
            }
        }
    }

    private void requireTrue(List<String> errors, String envName, String propertyName) {
        if (!Boolean.parseBoolean(value(propertyName))) {
            errors.add(envName + " must be true");
        }
    }

    private String value(String propertyName) {
        return environment.getProperty(propertyName, "").trim();
    }

    private static boolean placeholder(String value) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("change_me")
                || lower.contains("replace-with")
                || lower.contains("example.invalid")
                || lower.equals("changethispassword123!");
    }
}
