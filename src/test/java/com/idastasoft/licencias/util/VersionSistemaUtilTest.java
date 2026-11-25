package com.idastasoft.licencias.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionSistemaUtilTest {

    @Test
    void aceptaSemverValido() {
        assertTrue(VersionSistemaUtil.esValida("2.0.1"));
        assertTrue(VersionSistemaUtil.esValida("  10.2.0  "));
    }

    @Test
    void rechazaVacioYMalformado() {
        assertFalse(VersionSistemaUtil.esValida(null));
        assertFalse(VersionSistemaUtil.esValida(""));
        assertFalse(VersionSistemaUtil.esValida("   "));
        assertFalse(VersionSistemaUtil.esValida("abc"));
        assertFalse(VersionSistemaUtil.esValida("2.0"));
        assertFalse(VersionSistemaUtil.esValida("v2.0.1"));
        assertFalse(VersionSistemaUtil.esValida("2.0.1-beta"));
    }

    @Test
    void valorSeguroNuncaEsVacio() {
        assertEquals("0.0.0", VersionSistemaUtil.segura(null));
        assertEquals("0.0.0", VersionSistemaUtil.segura(""));
        assertEquals("2.0.1", VersionSistemaUtil.segura("2.0.1"));
    }

    @Test
    void comparaNumericamente() {
        assertTrue(VersionSistemaUtil.comparar("2.10.0", "2.9.0") > 0);
        assertTrue(VersionSistemaUtil.comparar("2.0.1", "2.0.0") > 0);
        assertEquals(0, VersionSistemaUtil.comparar("2.0.0", "2.0.0"));
    }
}
