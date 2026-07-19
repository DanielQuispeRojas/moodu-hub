package com.idastasoft.licencias.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Map;

/**
 * Configuracion del MOODU Hub leida desde hub-config.json (editable por ti).
 * Hoy expone la seccion "comision" (lo que MOODU se queda por venta de
 * modulos de terceros). Se puede extender con mas claves de config.
 */
@Service
public class HubConfigService {

    private final ObjectMapper mapper = new ObjectMapper();
    private static final String HUB_JSON = "hub-config.json";

    private Map<String, Object> comision = Map.of();

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
        } catch (Exception e) {
            // Si no existe el archivo, comision queda vacia (0).
        }
    }

    public Map<String, Object> getComision() {
        return comision;
    }
}
