package com.idastasoft.licencias;

import com.idastasoft.licencias.config.JwtAuthFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.util.List;

@SpringBootApplication
@EnableWebSecurity
public class LicenciasServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LicenciasServerApplication.class, args);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Publicos: login admin, catalogo, cliente, licencias validacion
                .requestMatchers("/api/admin/login").permitAll()
                .requestMatchers("/api/catalogo/**").permitAll()
                .requestMatchers("/api/cliente/**").permitAll()
                .requestMatchers("/api/licencias/validar").permitAll()
                // Actuator: health (Render check), prometheus, info
                .requestMatchers("/actuator/**").permitAll()
                // Admin protegido: todo lo demas bajo /api/admin/**
                .requestMatchers("/api/admin/**").authenticated()
                // Frontend estatico (admin-web, cliente-web, swagger)
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
                .requestMatchers("/admin", "/admin/**", "/cliente", "/cliente/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/webjars/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .anyRequest().permitAll()
            )
            .headers(headers -> headers.frameOptions(fo -> fo.disable())) // H2 console
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // Origenes permitidos. Por defecto: el desktop MOODU (localhost:8080/5173
        // y variantes 127.0.0.1) y 'null' para que los dashboards abiertos como
        // archivo local (file://) sigan funcionando. En produccion se puede
        // restringir mas via CORS_ALLOWED_ORIGINS (separados por coma).
        String corsEnv = System.getenv("CORS_ALLOWED_ORIGINS");
        List<String> allowed;
        if (corsEnv != null && !corsEnv.isBlank()) {
            allowed = List.of(corsEnv.split("\\s*,\\s*"));
        } else {
            allowed = List.of(
                "http://localhost:8080",
                "http://localhost:5173",
                "http://127.0.0.1:8080",
                "http://127.0.0.1:5173",
                "null"
            );
        }
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(allowed);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }

    @Bean
    public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
        FilterRegistrationBean<ForwardedHeaderFilter> bean = new FilterRegistrationBean<>(new ForwardedHeaderFilter());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
