package com.ecopos.common;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * GlobalConstants - Tương đương GlobalConstants.ts
 * Load cấu hình từ file .env dựa theo APP_ENV
 * Dùng custom parser (thay vì dotenv-java) để xử lý duplicate keys trong .env
 */
public class GlobalConstants {

    public enum Environment {
        test, uat, dev
    }

    private static final Map<String, String> envMap;
    public static final Environment APP_ENV;

    // API URLs
    public static final String API_BASE_URL;
    public static final String PORTAL_BASE_URL;

    // Auth
    public static final String EMAIL_LOGIN;
    public static final String PASSWORD_LOGIN;

    // Site config
    public static final String SITE_ID;
    public static final String MNB_STORE_CODE;
    public static final String PARTNER_TOKEN;

    // Partner API
    public static final String PARTNER_PUSH_ORDER_PATH = "/partner/pos/orders/push";
    public static final String PARTNER_APP_KEY;
    public static final String BRANCH_NAME;

    // Telegram
    public static final String TELEGRAM_BOT_TOKEN;
    public static final String TELEGRAM_CHAT_ID;

    // Timeouts
    public static final int OBJECT_TIMEOUT = 60;
    public static final int MAX_TRIES = 5;
    public static final int MAX_TRIES_RUN_TEST = 0;

    // Alias
    public static final String EMAIL;
    public static final String PASSWORD;

    static {
        // Load .env file - tìm từ thư mục hiện tại, cha, ông
        envMap = loadEnvFile();

        System.out.println("[GlobalConstants] Loaded " + envMap.size() + " env vars from .env");

        // Detect environment
        String envStr = getEnv("APP_ENV", "test").toLowerCase();
        APP_ENV = Environment.valueOf(envStr);
        System.out.println("[GlobalConstants] APP_ENV = " + APP_ENV);

        // Set URLs based on environment
        switch (APP_ENV) {
            case uat -> {
                API_BASE_URL = "https://ecopos-api-gateway-uat.finviet.com.vn";
                PORTAL_BASE_URL = "https://ecopos-portal-uat.finviet.com.vn";
            }
            case dev -> {
                API_BASE_URL = "https://ecopos-api-gateway-dev.finviet.com.vn";
                PORTAL_BASE_URL = "https://ecopos-portal-dev.finviet.com.vn";
            }
            default -> {
                API_BASE_URL = "https://ecopos-api-gateway-test.finviet.com.vn";
                PORTAL_BASE_URL = "https://ecopos-portal-test.finviet.com.vn";
            }
        }

        System.out.println("[GlobalConstants] API_BASE_URL = " + API_BASE_URL);

        // Load env-prefixed variables
        EMAIL_LOGIN = getEnvPrefixed("EMAIL_LOGIN");
        PASSWORD_LOGIN = getEnvPrefixed("PASSWORD_LOGIN");
        SITE_ID = getEnvPrefixed("SITE_ID");
        MNB_STORE_CODE = getEnvPrefixed("MNB_STORE_CODE");
        BRANCH_NAME = getEnvPrefixed("BRANCH_NAME");
        PARTNER_TOKEN = getEnvPrefixed("PARTNER_TOKEN");

        // Aliases
        EMAIL = EMAIL_LOGIN;
        PASSWORD = PASSWORD_LOGIN;

        // Direct env vars
        PARTNER_APP_KEY = getEnv("PARTNER_APP_KEY", "");
        TELEGRAM_BOT_TOKEN = getEnv("TELEGRAM_BOT_TOKEN", "");
        TELEGRAM_CHAT_ID = getEnv("TELEGRAM_CHAT_ID", "");

        System.out.println("[GlobalConstants] EMAIL = " + EMAIL);
        System.out.println("[GlobalConstants] SITE_ID = " + SITE_ID);
        System.out.println("[GlobalConstants] MNB_STORE_CODE = " + MNB_STORE_CODE);
        System.out.println("[GlobalConstants] BRANCH_NAME = " + BRANCH_NAME);
        System.out.println("[GlobalConstants] PARTNER_TOKEN = " +
                (PARTNER_TOKEN != null && PARTNER_TOKEN.length() > 10
                        ? PARTNER_TOKEN.substring(0, 10) + "..."
                        : PARTNER_TOKEN));
    }

    /**
     * Custom .env parser - xử lý được duplicate keys (last-wins)
     */
    private static Map<String, String> loadEnvFile() {
        String[] searchPaths = {".", "..", "../.."};
        for (String dir : searchPaths) {
            Path envPath = Paths.get(dir, ".env");
            if (Files.exists(envPath)) {
                System.out.println("[GlobalConstants] Found .env at: " + envPath.toAbsolutePath());
                return parseEnvFile(envPath);
            }
        }
        System.out.println("[GlobalConstants] WARNING: No .env file found in . or .. or ../..");
        return new HashMap<>();
    }

    /**
     * Parse .env file line by line
     * Xử lý: comments (#), blank lines, KEY=VALUE, quoted values
     * Duplicate keys: last-wins (không crash như dotenv-java)
     */
    private static Map<String, String> parseEnvFile(Path path) {
        Map<String, String> map = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Skip comments and blank lines
                if (line.isEmpty() || line.startsWith("#")) continue;

                int eqIdx = line.indexOf('=');
                if (eqIdx <= 0) continue;

                String key = line.substring(0, eqIdx).trim();
                String value = line.substring(eqIdx + 1).trim();

                // Remove surrounding quotes
                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }

                // Remove inline comments (only if not quoted)
                int commentIdx = value.indexOf(" #");
                if (commentIdx > 0) {
                    value = value.substring(0, commentIdx).trim();
                }

                map.put(key, value);
            }
        } catch (IOException e) {
            System.err.println("[GlobalConstants] Error reading .env: " + e.getMessage());
        }
        return map;
    }

    /**
     * Lấy env var với prefix theo APP_ENV (ví dụ TEST_EMAIL_LOGIN, UAT_EMAIL_LOGIN)
     */
    private static String getEnvPrefixed(String key) {
        String prefix = APP_ENV.name().toUpperCase();
        String prefixedKey = prefix + "_" + key;
        String val = getEnv(prefixedKey, null);
        if (val != null) return val;
        return getEnv(key, "");
    }

    /**
     * Lấy env var, ưu tiên System property → OS env → .env file
     */
    public static String getEnv(String key, String defaultValue) {
        // 1. System property (set by Maven -D)
        String sys = System.getProperty(key);
        if (sys != null && !sys.isBlank()) return sys;
        // 2. OS Environment variable
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) return env;
        // 3. .env file
        String dot = envMap.get(key);
        if (dot != null && !dot.isBlank()) return dot;
        return defaultValue;
    }
}
