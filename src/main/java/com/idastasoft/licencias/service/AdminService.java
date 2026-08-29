package com.idastasoft.licencias.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Credenciales del administrador del MOODU Hub.
 * Prioridad: variables de entorno (ADMIN_USER, ADMIN_PASSWORD, ADMIN_NAME) ->
 * en produccion SIEMPRE desde entorno. Fallback: admin-credentials.json (dev).
 * Las claves se comparan con BCrypt (si empiezan con $2a$/$2b$ se usa matches();
 * si es texto plano se compara en claro y se loggea un warning de seguridad).
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final PasswordEncoder passwordEncoder;

    private static final String ADMIN_JSON = "admin-credentials.json";

    @Value("${ADMIN_USER:}")
    private String envUsuario;
    @Value("${ADMIN_PASSWORD:}")
    private String envPassword;
    @Value("${ADMIN_NAME:Administrador MOODU}")
    private String envNombre;

    private List<Map<String, String>> usuarios = new ArrayList<>();

    public AdminService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        if (envUsuario != null && !envUsuario.isBlank()
                && envPassword != null && !envPassword.isBlank()) {
            String claveRaw = envPassword;
            usuarios.add(Map.of(
                "usuario", envUsuario.trim(),
                "clave", claveRaw,
                "nombre", envNombre == null || envNombre.isBlank() ? "Administrador MOODU" : envNombre
            ));
            if (!esHashBcrypt(claveRaw)) {
                log.warn("ADVERTENCIA: ADMIN_PASSWORD no tiene hash BCrypt. "
                    + "Se recomienda hashear antes de setearla como variable de entorno.");
            }
            log.info("Credenciales de administrador cargadas desde variables de entorno (ADMIN_USER='{}').",
                envUsuario);
            return;
        }
        cargarDesdeJson();
        log.warn("USO DE admin-credentials.json para el administrador. "
            + "En produccion se recomienda definir las variables de entorno ADMIN_USER y ADMIN_PASSWORD.");
    }

    private boolean esHashBcrypt(String clave) {
        return clave.startsWith("$2a$") || clave.startsWith("$2b$");
    }

    private void cargarDesdeJson() {
        try (InputStream is = new ClassPathResource(ADMIN_JSON).getInputStream()) {
            JsonNode root = mapper.readTree(is);
            JsonNode lista = root.path("usuarios");
            lista.forEach(n -> {
                String claveRaw = n.path("clave").asText();
                usuarios.add(Map.of(
                    "usuario", n.path("usuario").asText(),
                    "clave", claveRaw,
                    "nombre", n.path("nombre").asText()
                ));
                if (!esHashBcrypt(claveRaw)) {
                    log.warn("ADVERTENCIA: la clave del usuario '{}' en admin-credentials.json NO esta hasheada con BCrypt.",
                        n.path("usuario").asText());
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("No se pudo leer admin-credentials.json", e);
        }
    }

    public boolean autenticar(String usuario, String clave) {
        return usuarios.stream().anyMatch(u -> {
            if (!u.get("usuario").equals(usuario)) return false;
            String claveAlmacenada = u.get("clave");
            // Si es hash BCrypt, comparar con matches(); si no, equals() (legacy)
            if (claveAlmacenada.startsWith("$2a$") || claveAlmacenada.startsWith("$2b$")) {
                return passwordEncoder.matches(clave, claveAlmacenada);
            }
            return claveAlmacenada.equals(clave);
        });
    }

    public String nombreAdmin(String usuario) {
        return usuarios.stream()
            .filter(u -> u.get("usuario").equals(usuario))
            .map(u -> u.get("nombre"))
            .findFirst().orElse(usuario);
    }
}
