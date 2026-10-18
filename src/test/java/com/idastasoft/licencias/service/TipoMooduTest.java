package com.idastasoft.licencias.service;

import com.idastasoft.licencias.model.TipoMoodu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Los TRES modos de MOODU (REQ-HUB-TIER-01).
 *
 * <p>MOODU no es un modulo: es el sistema. Estos tres modos definen el
 * ALCANCE (que puede hacer el core) y su tipo se deduce de los modulos de la
 * licencia, no de una columna que pueda contradecir a la lista.</p>
 */
class TipoMooduTest {

    // ─── Deduccion desde los modulos de la licencia ─────────────────

    @Test
    @DisplayName("sin modulos de MOODU -> GRATIS (lo que todos pueden tener)")
    void sinModulosEsGratis() {
        assertEquals(TipoMoodu.GRATIS, TipoMoodu.deLicencia(List.of("recepcion", "operador_caja_chica")));
        assertEquals(TipoMoodu.GRATIS, TipoMoodu.deLicencia(List.of()));
        assertEquals(TipoMoodu.GRATIS, TipoMoodu.deLicencia(null));
    }

    @Test
    @DisplayName("con moodu_plus -> PLUS")
    void conPlusEsPlus() {
        assertEquals(TipoMoodu.PLUS, TipoMoodu.deLicencia(List.of("recepcion", "moodu_plus")));
    }

    @Test
    @DisplayName("con moodu_pro -> PRO, aunque tambien traiga moodu_plus (manda el mayor alcance)")
    void conProEsPro() {
        assertEquals(TipoMoodu.PRO, TipoMoodu.deLicencia(List.of("moodu_plus", "moodu_pro")));
    }

    @Test
    @DisplayName("la clave se compara sin distincion de mayus/espacios")
    void claveToleraMayusYEspacios() {
        assertEquals(TipoMoodu.PLUS, TipoMoodu.deLicencia(List.of("  MOODU_PLUS ")));
    }

    // ─── Capacidades (lo que habilita cada modo) ─────────────────────

    @Test
    @DisplayName("GRATIS: maquina unica y NODO, sin maquina central ni dispositivos ilimitados")
    void gratisNoHabilitaCentral() {
        assertTrue(TipoMoodu.GRATIS.capacidades().isEmpty());
        assertFalse(TipoMoodu.GRATIS.maquinaCentral());
        assertFalse(TipoMoodu.GRATIS.maquinasIlimitadas());
    }

    @Test
    @DisplayName("PLUS: habilita maquina central, no los dispositivos ilimitados")
    void plusHabilitaCentral() {
        assertTrue(TipoMoodu.PLUS.maquinaCentral());
        assertEquals(Set.of(TipoMoodu.CAP_MODO_CENTRAL), TipoMoodu.PLUS.capacidades());
        assertFalse(TipoMoodu.PLUS.maquinasIlimitadas());
    }

    @Test
    @DisplayName("PRO: central + todos los modulos oficiales + dispositivos ilimitados (acumulativo)")
    void proAcumulaPlus() {
        assertTrue(TipoMoodu.PRO.maquinaCentral());
        assertTrue(TipoMoodu.PRO.tiene(TipoMoodu.CAP_TODOS_MODULOS));
        assertTrue(TipoMoodu.PRO.maquinasIlimitadas());
        // Acumulativo: PRO tiene todo lo de PLUS.
        assertTrue(TipoMoodu.PRO.capacidades().containsAll(TipoMoodu.PLUS.capacidades()));
    }

    // ─── Formas de pago admitidas ────────────────────────────────────

    @Test
    @DisplayName("PLUS es PERMANENTE y PRO es solo MENSUAL o ANUAL (§11.91)")
    void periodicidadesPorTipo() {
        assertTrue(TipoMoodu.PLUS.admite("PERMANENTE"));
        assertFalse(TipoMoodu.PLUS.admite("MENSUAL"));
        assertFalse(TipoMoodu.PLUS.admite("ANUAL"));

        assertTrue(TipoMoodu.PRO.admite("MENSUAL"));
        assertTrue(TipoMoodu.PRO.admite("ANUAL"));
        assertFalse(TipoMoodu.PRO.admite("PERMANENTE"));
    }

    // ─── Lectura defensiva ───────────────────────────────────────────

    @Test
    @DisplayName("un valor desconocido cae a GRATIS (fail-safe: nunca deja al cliente sin MOODU)")
    void desconocidoEsGratis() {
        assertEquals(TipoMoodu.GRATIS, TipoMoodu.de(null));
        assertEquals(TipoMoodu.GRATIS, TipoMoodu.de(""));
        assertEquals(TipoMoodu.GRATIS, TipoMoodu.de("enterprise-plus"));
        assertEquals("GRATIS", TipoMoodu.normalizar("cualquierCosa"));
    }

    @Test
    @DisplayName("deClaveCatalogo distingue los tiers de los modulos normales")
    void claveDeCatalogo() {
        assertEquals(TipoMoodu.PLUS, TipoMoodu.deClaveCatalogo("moodu_plus"));
        assertEquals(TipoMoodu.PRO, TipoMoodu.deClaveCatalogo(" MOODU_PRO "));
        assertNull(TipoMoodu.deClaveCatalogo("recepcion"));
        assertNull(TipoMoodu.deClaveCatalogo(null));
        assertTrue(TipoMoodu.esClaveTier("moodu_plus"));
        assertFalse(TipoMoodu.esClaveTier("almacen"));
    }
}