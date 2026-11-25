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

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.CommandLineRunner;

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
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                                   @org.springframework.beans.factory.annotation.Value("${spring.h2.console.enabled:false}") boolean h2Consola) throws Exception {
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
            .headers(headers -> {
                // H-07: CSP endurecida. Los paneles Vue-CDN cargan Vue desde
                // unpkg y Bootstrap desde jsdelivr; 'unsafe-inline' sigue
                // necesario porque el createApp vive inline en el HTML.
                headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; "
                        // 'unsafe-eval' es requerido por el compilador de
                        // templates de Vue CDN (vue.global.prod.js). Sin el,
                        // el panel no monta. Compromiso conocido H-07.
                        + "script-src 'self' https://unpkg.com 'unsafe-inline' 'unsafe-eval'; "
                        + "style-src 'self' https://cdn.jsdelivr.net 'unsafe-inline'; "
                        + "img-src 'self' data: https:; "
                        + "font-src 'self' data: https://cdn.jsdelivr.net; "
                        + "connect-src 'self'; object-src 'none'; base-uri 'self'; "
                        + "frame-ancestors 'none'"));
                if (h2Consola) headers.frameOptions(fo -> fo.disable()); // solo si se habilito
            })
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

    // H-10: Comprape es un modulo OFICIAL del catalogo. Sin esta entrada el
    // desktop no lo muestra (bloqueo por licencia del core §11.42) y la
    // tarjeta "Comprape" del panel no tiene a donde apuntar. Se siembra SOLO
    // si falta; nunca sobrescribe lo que el admin ya edito. El plan GRATUITO
    // (20 items / 15 dias) se gestiona por licencia (PlanComprape), no aqui.
    @Bean
    public CommandLineRunner registrarModuloComprape(
            com.idastasoft.licencias.repository.ModuloCatalogoRepo repo) {
        return args -> {
            try {
                if (repo.findByClave("comprape") != null) return;
                var m = new com.idastasoft.licencias.model.ModuloCatalogo();
                m.setClave("comprape");
                m.setNombre("Comprape");
                m.setDescripcion("Tienda mayorista: publica tu mercaderia al por mayor y gestiona tu perfil de comprador.");
                m.setDesarrollador("idastaSoft");
                m.setOrigen(com.idastasoft.licencias.model.ModuloCatalogo.OrigenModulo.OFICIAL);
                m.setTipo(com.idastasoft.licencias.model.ModuloCatalogo.TipoLicencia.GRATIS);
                m.setPrecio(java.math.BigDecimal.ZERO);
                m.setVersion("0.1.0");
                m.setFechaPublicacion(java.time.LocalDate.now());
                m.setActivo(true);
                repo.save(m);
                System.out.println("[Hub] HUB-10: modulo 'comprape' registrado en el catalogo.");
            } catch (Exception e) {
                System.err.println("[Hub] HUB-10: no se pudo registrar comprape: " + e.getMessage());
            }
        };
    }

    // R2 cost-plus: al arrancar, corrige precio de Recepción a 3500 si aún está en 1500 (instalaciones viejas)
    @Bean
    public CommandLineRunner actualizarPreciosCostPlus(com.idastasoft.licencias.repository.ModuloCatalogoRepo repo) {
        return args -> {
            var m = repo.findByClave("recepcion");
            if (m != null && m.getPrecio() != null && m.getPrecio().compareTo(new BigDecimal("1500")) == 0) {
                m.setPrecio(new BigDecimal("3500"));
                repo.save(m);
            }
        };
    }

    // REQ-HUB-MODULOS-01: al arrancar, migra la lista legacy `modulos` a
    // terminos por modulo que heredan la vigencia global (idempotente: solo
    // licencias con lista legacy y sin filas de terminos). Fuente unica: la
    // tabla de terminos; la lista legacy se vacia al migrar.
    @Bean
    @org.springframework.core.annotation.Order(1)
    public CommandLineRunner migrarTerminosModulos(
            com.idastasoft.licencias.repository.LicenciaRepo licenciaRepo,
            com.idastasoft.licencias.service.LicenciaService licenciaService) {
        return args -> {
            for (var l : licenciaRepo.findAll()) {
                try {
                    var terms = licenciaService.detallesModulos(l);
                    var legacy = l.getModulos();
                    if (!terms.isEmpty() || legacy == null || legacy.isEmpty()) continue;
                    licenciaService.heredarTerminos(l, legacy);
                    l.setModulos(new java.util.ArrayList<>());
                    licenciaRepo.save(l);
                } catch (Exception e) {
                    System.err.println("[Hub] Migracion de terminos fallida para licencia "
                            + l.getId() + ": " + e.getMessage());
                }
            }
        };
    }

    // HUB-11: PERMANENTE de pago -> TRIENAL desde hoy (los GRATIS se saltan).
    // Corre DESPUES de migrarTerminosModulos (@Order 1) para cubrir tambien
    // las licencias cuyos terminos se crean en esa migracion. Idempotente.
    @Bean
    @org.springframework.core.annotation.Order(2)
    public CommandLineRunner migrarPlanesTrienales(
            com.idastasoft.licencias.service.LicenciaService licenciaService,
            com.idastasoft.licencias.service.ModuloPlanService moduloPlanService) {
        return args -> {
            try {
                int terminos = licenciaService.convertirPermanentesDePago();
                int planes = moduloPlanService.convertirPermanentesDePago();
                if (terminos > 0 || planes > 0) {
                    System.out.println("[Hub] HUB-11: " + terminos + " terminos y " + planes
                            + " planes Permanente -> TRIENAL (desde hoy).");
                }
            } catch (Exception e) {
                System.err.println("[Hub] HUB-11 migration fallida: " + e.getMessage());
            }
        };
    }

    // REQ-AYU-02: siembra la guia de uso de los 6 modulos BETA una sola vez
    // por clave. Nunca sobrescribe una edicion del admin; al segundo arranque
    // no hace nada (idempotente por ModuloCatalogo.manual != null).
    @Bean
    public CommandLineRunner sembrarManualesFabrica(com.idastasoft.licencias.service.ManualCatalogoService service) {
        return args -> {
            int n = service.sembrarFabrica();
            if (n > 0) {
                System.out.println("[Hub] Guias de uso sembradas: " + n + " modulos.");
            }
        };
    }

    // REQ-HUB-TEMAS-01: publica los 8 presets de fabrica de MOODU en el catalogo
    // de themes (idempotente: solo si no hay ninguna fila creada por "sistema").
    @Bean
    public CommandLineRunner sembrarTemasFabrica(com.idastasoft.licencias.service.TemaCatalogoService service) {
        return args -> {
            int n = service.sembrarFabrica();
            if (n > 0) {
                System.out.println("[Hub] Semilla de themes de fabrica: " + n + " publicados.");
            }
        };
    }
}
