package com.idastasoft.licencias.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Carga las credenciales del administrador desde admin-credentials.json.
 * Las claves se comparan con BCrypt (si el JSON tiene hash BCrypt, se usa
 * directamente; si tiene texto plano, se compara en claro para retrocompat
 * y se loggea un warning).
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final PasswordEncoder passwordEncoder;

    private static final String ADMIN_JSON = "admin-credentials.json";

    private List<Map<String, String>> usuarios = new ArrayList<>();

    public AdminService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
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
                // Warn si la clave no esta hasheada con BCrypt
                if (!claveRaw.startsWith("$2a$") && !claveRaw.startsWith("$2b$")) {
                    log.warn("ADVERTENCIA: la clave del usuario '{}' en admin-credentials.json NO esta hasheada con BCrypt. "
                        + "Se recomienda hashear: java -cp <bcrypthash.jar> org.mindrot.jbcrypt.BCrypt hash '{}'",
                        n.path("usuario").asText(), n.path("usuario").asText());
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
