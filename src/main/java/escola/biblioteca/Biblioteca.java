package escola.biblioteca;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootApplication
public class Biblioteca {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Biblioteca.class);
        String renderUrl = System.getenv("DATABASE_URL");
        if (renderUrl != null && !renderUrl.isBlank() && System.getenv("DB_JDBC_URL") == null) {
            Map<String, Object> settings = renderSettings(renderUrl);
            app.addInitializers(context -> context.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("render-postgres", settings)));
        }
        app.run(args);
    }

    private static Map<String, Object> renderSettings(String url) {
        try {
            URI uri = URI.create(url);
            String[] credentials = uri.getRawUserInfo() == null ? new String[0] : uri.getRawUserInfo().split(":", 2);
            if (!("postgresql".equals(uri.getScheme()) || "postgres".equals(uri.getScheme()))
                    || credentials.length != 2 || uri.getHost() == null
                    || uri.getRawPath() == null || uri.getRawPath().length() < 2 || uri.getRawFragment() != null) {
                throw new IllegalArgumentException();
            }
            int port = uri.getPort() == -1 ? 5432 : uri.getPort();
            String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getRawPath()
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            return Map.of("spring.datasource.url", jdbcUrl,
                    "spring.datasource.username", decodeCredential(credentials[0]),
                    "spring.datasource.password", decodeCredential(credentials[1]));
        } catch (IllegalArgumentException error) {
            // URLs inválidas podem conter credenciais e não devem aparecer nos registros.
            throw new IllegalStateException("DATABASE_URL precisa ser uma conexão PostgreSQL válida.");
        }
    }

    private static String decodeCredential(String raw) {
        return URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    @Bean
    Clock schoolClock() {
        return Clock.system(ZoneId.of("America/Sao_Paulo"));
    }

    @Bean
    ApplicationRunner initialize(JdbcTemplate jdbc, PasswordEncoder encoder, TransactionTemplate transactions,
                                 @Value("${biblioteca.admin-email}") String email,
                                 @Value("${biblioteca.admin-password}") String password,
                                 @Value("${biblioteca.demo}") boolean demo) {
        return args -> {
            if (!email.isBlank() || !password.isEmpty()) {
                if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                        || email.length() > 254 || password.length() < 14
                        || password.getBytes(StandardCharsets.UTF_8).length > 72) {
                    throw new IllegalStateException("Defina ADMIN_EMAIL válido e ADMIN_PASSWORD de 14 a 72 bytes.");
                }
                // O segredo de implantação só cria a conta inicial. Reinícios não reativam nem alteram contas.
                jdbc.update("INSERT INTO administrators(email,password_hash) VALUES (?,?) ON CONFLICT DO NOTHING",
                        email.strip().toLowerCase(Locale.ROOT), encoder.encode(password));
            }
            if (demo) {
                transactions.executeWithoutResult(status -> {
                    if (jdbc.update("INSERT INTO app_metadata VALUES ('demo-v1') ON CONFLICT DO NOTHING") == 1) {
                        ResourceDatabasePopulator seed = new ResourceDatabasePopulator(new ClassPathResource("demo.sql"));
                        seed.setSqlScriptEncoding("UTF-8");
                        seed.execute(jdbc.getDataSource());
                    }
                });
            }
        };
    }
}
