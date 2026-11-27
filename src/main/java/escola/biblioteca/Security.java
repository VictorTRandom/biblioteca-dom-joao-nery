package escola.biblioteca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
class Security {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    UserDetailsService users(JdbcTemplate jdbc) {
        return email -> jdbc.query("SELECT email,password_hash,active FROM administrators WHERE email=?",
                (rs, row) -> User.withUsername(rs.getString("email")).password(rs.getString("password_hash"))
                        .disabled(!rs.getBoolean("active")).roles("ADMIN").build(),
                email.strip().toLowerCase(Locale.ROOT))
                .stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas."));
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .addFilterBefore(new SameOriginWrites(), LogoutFilter.class)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/api/catalog", "/api/catalog/**", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/login", "/api/catalog/requests").permitAll()
                        .requestMatchers("/api/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .formLogin(form -> form.loginPage("/login.html").loginProcessingUrl("/api/login")
                        .usernameParameter("email")
                        .successHandler((req, res, user) -> res.setStatus(204))
                        .failureHandler((req, res, error) -> jsonError(res, 401, "E-mail ou senha incorretos.")))
                .logout(logout -> logout
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/logout"))
                        .deleteCookies("BIBLIOTECA_SESSION")
                        .logoutSuccessHandler((req, res, user) -> res.setStatus(204)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, error) -> jsonError(res, 401, "Entre para continuar."))
                        .accessDeniedHandler((req, res, error) -> jsonError(res, 403, "Acesso restrito.")))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; img-src 'self' blob:; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .build();
    }

    static void jsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }

    private static class SameOriginWrites extends OncePerRequestFilter {
        private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            // Um formulário externo não consegue enviar este cabeçalho. A API não habilita CORS.
            if (request.getServletPath().startsWith("/api/") && !READ_METHODS.contains(request.getMethod())
                    && !"1".equals(request.getHeader("X-Biblioteca-Request"))) {
                jsonError(response, 403, "Solicitação inválida.");
                return;
            }
            chain.doFilter(request, response);
        }
    }
}
