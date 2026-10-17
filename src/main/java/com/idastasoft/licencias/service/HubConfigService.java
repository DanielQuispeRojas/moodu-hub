package com.idastasoft.licencias.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuracion del MOODU Hub leida desde hub-config.json (editable por ti).
 *
 * <p>Secciones que expone:</p>
 * <ul>
 *   <li><b>comision</b>: lo que MOODU se queda por venta de modulos de terceros.</li>
 *   <li><b>contacto</b>: tus medios (whatsapp, correo, fanpage, web...). Es la
 *       SEMILLA de la primera vez; el dato que vale es el de la tabla
 *       {@code hub_contacto}, que edita el panel admin (el disco del hosting
 *       es efimero: lo que se deje solo en el archivo se pierde al redesplegar).</li>
 * </ul>
 */
@Service
public class HubConfigService {

    private final ObjectMapper mapper = new ObjectMapper();
    private static final String HUB_JSON = "hub-config.json";

    private Map<String, Object> comision = Map.of();
    private Map<String, Object> contacto = Map.of();
    private String sitioWeb = "";

    @PostConstruct
    public void init() {
        try (InputStream is = new ClassPathResource(HUB_JSON).getInputStream()) {
            JsonNode root = mapper.readTree(is);
            JsonNode c = root.path("comision");
            if (!c.isMissingNode()) {
                comision = Map.of(
                    "tipo", c.path("tipo").asText("PORCENTAJE"),
                    "valor", c.path("valor").asDouble(0.0),
                    "moneda", c.path("moneda").asText("USD"),
                    "notas", c.path("notas").asText("")
                );
            }
            // Contacto: se arma sobre lo que exista; vacio = la Tienda avisa
            // "escribenos" sin medios, nunca rompe.
            JsonNode k = root.path("contacto");
            Map<String, Object> m = new LinkedHashMap<>();
            for (String clave : new String[]{"whatsapp", "correo", "telefono",
                                             "fanpage", "web", "telegram", "instagram"}) {
                String v = k.path(clave).asText("");
                if (v != null && !v.isBlank()) m.put(clave, v.trim());
            }
            contacto = m;
            sitioWeb = root.path("sitioWeb").asText("");
        } catch (Exception e) {
            // Si no existe el archivo, comision/contacto quedan vacios.
        }
    }

    public Map<String, Object> getComision() {
        return comision;
    }

    /** Semilla del contacto (primera vez). El valor vigente vive en BD. */
    public Map<String, Object> getContactoSemilla() {
        return contacto;
    }

    public String getSitioWeb() {
        return sitioWeb;
    }
}