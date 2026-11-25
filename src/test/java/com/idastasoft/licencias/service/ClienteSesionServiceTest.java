package com.idastasoft.licencias.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** H-02: el correo solo NO autoriza; hace falta un token de sesion vigente. */
class ClienteSesionServiceTest {

    private final ClienteSesionService service = new ClienteSesionService();

    @Test
    void login_emiteToken_y_autorizaSoloAlCorreoCorrecto() {
        String token = service.emitir("Cliente@Ejemplo.COM");
        assertNotNull(token);
        assertFalse(token.isBlank());
        assertTrue(service.autoriza(token, "cliente@ejemplo.com"));
        assertTrue(service.autoriza(token, "CLIENTE@ejemplo.com"));
        assertFalse(service.autoriza(token, "otro@ejemplo.com"));
    }

    @Test
    void sinToken_noAutoriza() {
        service.emitir("cliente@ejemplo.com");
        assertFalse(service.autoriza(null, "cliente@ejemplo.com"));
        assertFalse(service.autoriza("", "cliente@ejemplo.com"));
        assertFalse(service.autoriza("token-inexistente", "cliente@ejemplo.com"));
    }

    @Test
    void logout_revocaInmediatamente() {
        String token = service.emitir("cliente@ejemplo.com");
        assertTrue(service.autoriza(token, "cliente@ejemplo.com"));
        service.revocar(token);
        assertFalse(service.autoriza(token, "cliente@ejemplo.com"));
    }

    @Test
    void nuevoLogin_revocaLaSesionAnteriorDelMismoCorreo() {
        String primero = service.emitir("cliente@ejemplo.com");
        String segundo = service.emitir("cliente@ejemplo.com");
        assertNotEquals(primero, segundo);
        assertFalse(service.autoriza(primero, "cliente@ejemplo.com"));
        assertTrue(service.autoriza(segundo, "cliente@ejemplo.com"));
    }

    @Test
    void sesionesDeCorreosDistintos_sonIndependientes() {
        String a = service.emitir("a@ejemplo.com");
        String b = service.emitir("b@ejemplo.com");
        assertTrue(service.autoriza(a, "a@ejemplo.com"));
        assertTrue(service.autoriza(b, "b@ejemplo.com"));
        assertFalse(service.autoriza(a, "b@ejemplo.com"));
        service.revocar(a);
        assertTrue(service.autoriza(b, "b@ejemplo.com"));
        assertEquals(1, service.activas());
    }

    @Test
    void correoVacio_seRechaza() {
        assertThrows(IllegalArgumentException.class, () -> service.emitir("   "));
        assertEquals(0, service.activas());
    }
}
