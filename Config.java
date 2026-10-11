package ai;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/** Reads autocare.properties (next to the app) so no secrets are hard-coded in source. */
final class Config {
    private static final Properties P = new Properties();
    static {
        P.setProperty("db.host", "localhost");
        P.setProperty("db.port", "3306");
        P.setProperty("db.name", "autocare_ai");
        P.setProperty("db.user", "root");
        P.setProperty("db.password", "P@ssw0rd1");
        P.setProperty("ai.model", "claude-sonnet-5-5");
        try (FileInputStream in = new FileInputStream("autocare.properties")) { P.load(in); }
        catch (IOException ignored) { /* defaults are fine */ }
    }
    /** JDBC URL built from host/port/name (or taken verbatim from db.url if you set it). */
    static String dbUrl() {
        String custom = get("db.url");
        if (!custom.isEmpty()) return custom;
        return "jdbc:mysql://" + get("db.host") + ":" + get("db.port") + "/" + get("db.name")
                + "?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
    }
    static String get(String key) { return P.getProperty(key, "").trim(); }

    /** API key from env var first, then properties file. Empty = offline assistant. */
    static String apiKey() {
        String k = System.getenv("ANTHROPIC_API_KEY");
        return (k != null && !k.isBlank()) ? k.trim() : get("ai.api.key");
    }
}
