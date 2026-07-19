package com.idastasoft.licencias.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Carga las credenciales del administrador desde un JSON en el backend
 * (admin-credentials.json), editable manualmente por ti. El dashboard admin
 * hace login contra este servicio.
 */
@Service
public class AdminService {

    private final ObjectMapper mapper = new ObjectMapper();

    // Ruta del archivo JSON con credenciales (editable por ti). Por defecto en
    // el classpath; si lo quieres fuera del JAR, cambia aqui la ruta.
    private static final String ADMIN_JSON = "admin-credentials.json";

    private List<Map<String, String>> usuarios = new ArrayList<>();

    @PostConstruct
    public void init() {
        try (InputStream is = new ClassPathResource(ADMIN_JSON).getInputStream()) {
            JsonNode root = mapper.readTree(is);
            JsonNode lista = root.path("usuarios");
            lista.forEach(n -> usuarios.add(Map.of(
                "usuario", n.path("usuario").asText(),
                "clave", n.path("clave").asText(),
                "nombre", n.path("nombre").asText()
            )));
        } catch (Exception e) {
            throw new RuntimeException("No se pudo leer admin-credentials.json", e);
        }
    }

    public boolean autenticar(String usuario, String clave) {
        return usuarios.stream().anyMatch(u ->
            u.get("usuario").equals(usuario) && u.get("clave").equals(clave));
    }

    public String nombreAdmin(String usuario) {
        return usuarios.stream()
            .filter(u -> u.get("usuario").equals(usuario))
            .map(u -> u.get("nombre"))
            .findFirst().orElse(usuario);
    }
}
