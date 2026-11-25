package com.idastasoft.licencias.controller;

import com.idastasoft.licencias.model.ModuloCatalogo;
import com.idastasoft.licencias.repository.*;
import com.idastasoft.licencias.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * MEJ-10: contrato publico Hub -> core -> desktop. Si falta una clave aqui,
 * la Tienda o el espejo del core se quedan sin dato y nadie lo nota hasta que
 * un cliente lo reporta. Este test es la red.
 *
 * <p>OJO (§11.442): en este JDK Mockito inline NO puede mockear clases concretas
 * como ModuloPlanService/ParametroService. Se instancian REALES con repos
 * mockeados (interfaces), que es ademas mas fiel al runtime.</p>
 */
class ContratoCatalogoTest {

    private CatalogoController controller(ModuloCatalogoRepo moduloRepo) {
        ModuloFotoRepo fotoRepo = mock(ModuloFotoRepo.class);
        VersionSistemaRepo versionRepo = mock(VersionSistemaRepo.class);
        HubParametroRepo parametroRepo = mock(HubParametroRepo.class);
        ModuloPlanRepo planRepo = mock(ModuloPlanRepo.class);
        when(parametroRepo.findByClaveIgnoreCase(any())).thenReturn(Optional.empty());
        when(planRepo.findByModuloIdOrderByOrdenAscIdAsc(any())).thenReturn(List.of());
        when(fotoRepo.findByModuloIdOrderByOrdenAscIdAsc(any())).thenReturn(List.of());
        when(versionRepo.findAll()).thenReturn(List.of());

        ModuloPlanService planes = new ModuloPlanService(planRepo, new ParametroService(parametroRepo));
        return new CatalogoController(moduloRepo, fotoRepo, versionRepo, planes,
                new HubConfigService(), new ContactoService(mock(HubContactoRepo.class)),
                new ParametroService(parametroRepo),
                new ValoracionService(mock(ValoracionRepo.class)),
                new ManualCatalogoService(moduloRepo));
    }

    @Test
    void catalogoPublico_exponeTodasLasClavesQueConsumeElCore() {
        ModuloCatalogoRepo repo = mock(ModuloCatalogoRepo.class);
        ModuloCatalogo m = new ModuloCatalogo();
        m.setId(1L);
        m.setClave("almacen");
        m.setNombre("Almacen");
        m.setTipo(ModuloCatalogo.TipoLicencia.PAGA);
        m.setOrigen(ModuloCatalogo.OrigenModulo.OFICIAL);
        m.setPrecio(new BigDecimal("3000"));
        m.setActivo(true);
        when(repo.findAll()).thenReturn(List.of(m));

        ResponseEntity<?> r = controller(repo).listarModulos();
        assertEquals(200, r.getStatusCode().value());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mods = (List<Map<String, Object>>) r.getBody();
        assertEquals(1, mods.size());
        Map<String, Object> x = mods.get(0);
        for (String clave : List.of("clave", "nombre", "tipo", "precio", "precioLegacy",
                "precioConcuerda", "planes", "tieneManual", "valoracion", "dispositivosIncluidos")) {
            assertTrue(x.containsKey(clave), "falta la clave de contrato: " + clave);
        }
        assertEquals("almacen", x.get("clave"));
        assertNotNull(x.get("precioConcuerda"));
        assertEquals(1, ((List<?>) x.get("planes")).size());
    }

    @Test
    void feedSinVersiones_nuncaDevuelveCadenaVacia() {
        ResponseEntity<?> r = controller(mock(ModuloCatalogoRepo.class)).versionSistema();
        assertEquals(200, r.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) r.getBody();
        assertEquals("0.0.0", body.get("version"));
    }
}
