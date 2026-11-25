package com.idastasoft.licencias.service;

import com.idastasoft.licencias.model.Valoracion;
import com.idastasoft.licencias.repository.ValoracionRepo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** H-05: la lista publica enmascara el correo; "mia" lo conserva para editar. */
class ValoracionServiceTest {

    private Valoracion valoracion() {
        Valoracion v = new Valoracion();
        v.setId(1L);
        v.setDestino("moodu");
        v.setCorreo("da12328.dq@gmail.com");
        v.setPuntaje(3);
        v.setFechaCreacion(LocalDateTime.now().minusDays(2));
        v.setFechaModificacion(LocalDateTime.now());
        v.setVisible(true);
        return v;
    }

    @Test
    void mascaraCorreo() {
        assertEquals("da***@gmail.com", ValoracionService.mascaraCorreo("da12328.dq@gmail.com"));
        assertEquals("an***@correo.com", ValoracionService.mascaraCorreo("ana@correo.com"));
        assertEquals("a***@x.com", ValoracionService.mascaraCorreo("a@x.com"));
        assertEquals("***", ValoracionService.mascaraCorreo("sin-arroba"));
        assertNull(ValoracionService.mascaraCorreo(null));
    }

    @Test
    void listaPublica_enmascaraCorreo() {
        ValoracionRepo repo = mock(ValoracionRepo.class);
        when(repo.findByDestinoAndVisibleTrueOrderByFechaModificacionDesc("moodu"))
                .thenReturn(List.of(valoracion()));
        ValoracionService service = new ValoracionService(repo);
        Map<String, Object> r = service.listar("moodu");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) r.get("items");
        assertEquals("da***@gmail.com", items.get(0).get("correo"));
        assertEquals(3.0, r.get("promedio"));
    }

    @Test
    void mia_conservaCorreoParaEditar() {
        ValoracionRepo repo = mock(ValoracionRepo.class);
        when(repo.findByDestinoAndCorreo("moodu", "da12328.dq@gmail.com"))
                .thenReturn(java.util.Optional.of(valoracion()));
        ValoracionService service = new ValoracionService(repo);
        Map<String, Object> r = service.mia("moodu", "da12328.dq@gmail.com");
        assertEquals(Boolean.TRUE, r.get("tiene"));
        assertEquals("da12328.dq@gmail.com", r.get("correo"));
    }
}
