package com.idastasoft.licencias.service;

import com.idastasoft.licencias.model.HubParametro;
import com.idastasoft.licencias.repository.HubParametroRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** HUB-10 (TC-HUB-10): publicacion en caliente de la URL del catalogo de normativas. */
@ExtendWith(MockitoExtension.class)
class NormativasParamsTest {

    @Mock private HubParametroRepo repo;

    private ParametroService con(Map<String, String> valores) {
        when(repo.findByClaveIgnoreCase(anyString())).thenAnswer(inv -> {
            String clave = inv.getArgument(0, String.class);
            String valor = valores.get(clave);
            return valor == null ? Optional.empty()
                    : Optional.of(new HubParametro(clave, valor));
        });
        return new ParametroService(repo);
    }

    @Test
    void sinParametro_fallbackEmbebidoConAviso() {
        var r = con(Map.of()).normativas();
        assertEquals(ParametroService.NORMATIVAS_REPO_URL_DEFAULT, r.get("normativasRepoUrl"));
        assertEquals(ParametroService.NORMATIVAS_MANIFEST_URL_DEFAULT, r.get("normativasManifestUrl"));
        assertEquals(ParametroService.NORMATIVAS_VERSION_DEFAULT, r.get("normativasVersion"));
        assertEquals("EMBEBIDO", r.get("normativasOrigen"));
        assertTrue(String.valueOf(r.get("normativasAviso")).contains("embebida"));
    }

    @Test
    void conParametro_valoresDeBDsePublican() {
        Map<String, String> v = new HashMap<>();
        v.put(ParametroService.NORMATIVAS_REPO_URL, "https://github.com/otro/moodu_normativas.git");
        v.put(ParametroService.NORMATIVAS_MANIFEST_URL, "https://raw.githubusercontent.com/otro/moodu_normativas/main/manifest.json");
        v.put(ParametroService.NORMATIVAS_VERSION, "2027.2");
        var r = con(v).normativas();
        assertEquals("https://github.com/otro/moodu_normativas.git", r.get("normativasRepoUrl"));
        assertEquals("https://raw.githubusercontent.com/otro/moodu_normativas/main/manifest.json",
                r.get("normativasManifestUrl"));
        assertEquals("2027.2", r.get("normativasVersion"));
        assertEquals("HUB", r.get("normativasOrigen"));
        assertFalse(r.containsKey("normativasAviso"));
    }

    @Test
    void urlInvalida_noSePropagaComoActiva() {
        Map<String, String> v = new HashMap<>();
        v.put(ParametroService.NORMATIVAS_REPO_URL, "ftp:/mal escrito");
        v.put(ParametroService.NORMATIVAS_MANIFEST_URL, "no-es-url");
        v.put(ParametroService.NORMATIVAS_VERSION, "2026.1");
        var r = con(v).normativas();
        assertEquals(ParametroService.NORMATIVAS_REPO_URL_DEFAULT, r.get("normativasRepoUrl"));
        assertEquals(ParametroService.NORMATIVAS_MANIFEST_URL_DEFAULT, r.get("normativasManifestUrl"));
        assertEquals("EMBEBIDO", r.get("normativasOrigen"));
        assertTrue(String.valueOf(r.get("normativasAviso")).contains("repo invalido"));
    }

    @Test
    void versionInvalida_caeAlFallback() {
        var r = con(Map.of(ParametroService.NORMATIVAS_VERSION, "cualquiera")).normativas();
        assertEquals(ParametroService.NORMATIVAS_VERSION_DEFAULT, r.get("normativasVersion"));
        assertEquals("EMBEBIDO", r.get("normativasOrigen"));
    }

    @Test
    void contratoPublico_incluyeLasTresClaves() {
        var r = con(Map.of()).normativas();
        assertTrue(r.containsKey("normativasRepoUrl"));
        assertTrue(r.containsKey("normativasManifestUrl"));
        assertTrue(r.containsKey("normativasVersion"));
    }
}
