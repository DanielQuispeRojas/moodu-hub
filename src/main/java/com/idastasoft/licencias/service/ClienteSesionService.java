package com.idastasoft.licencias.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sesiones del panel de cliente (H-02, auditoria HUB-12).
 *
 * <p>ANTES: los endpoints de {@code /api/cliente/**} se autorizaban solo con
 * {@code ?correo=}. Cualquier persona que conociera el correo de un cliente podia
 * listar, desligar y borrar sus maquinas ( denial of service de su cupo ).</p>
 *
 * <p>El token es OPACO y se guarda solo en memoria. NO es un JWT: el JwtAuthFilter
 * del Hub otorga {@code ROLE_ADMIN} a cualquier JWT valido (H-20), asi que firmar
 * tokens de cliente con el mismo secreto seria una escalada de privilegios. Un
 * valor aleatorio no lo acepta el filtro y ademas se puede revocar al instante.</p>
 *
 * <p>Vigencia de 8 horas, igual que la politica de invitados de MOODU. Al reiniciar
 * el Hub las sesiones se pierden y el cliente vuelve a iniciar sesion; es aceptable
 * porque el panel cliente es un quiosco de gestion de dispositivos, no una API de
 * negocio.</p>
 */
@Service
public class ClienteSesionService {

    /** 8 horas, en milisegundos. */
    static final long VIGENCIA_MS = 8L * 60 * 60 * 1000;

    private final Map<String, String> correosPorToken = new ConcurrentHashMap<>();
    private final Map<String, Long> expiraPorToken = new ConcurrentHashMap<>();

    /** Emite un token nuevo para el correo indicado. Revoca cualquier sesion previa del mismo correo. */
    public String emitir(String correo) {
        if (correo == null || correo.isBlank()) {
            throw new IllegalArgumentException("Correo requerido para iniciar sesion");
        }
        String normalizado = correo.trim().toLowerCase();
        correosPorToken.entrySet().removeIf(e -> normalizado.equals(e.getValue()));
        String token = UUID.randomUUID().toString().replace("-", "");
        correosPorToken.put(token, normalizado);
        expiraPorToken.put(token, Instant.now().toEpochMilli() + VIGENCIA_MS);
        limpiarVencidos();
        return token;
    }

    /**
     * Devuelve el correo asociado a un token vigente, o {@code null} si no existe,
     * expiro o es de otro correo. Nunca lanza: es un gate de acceso.
     */
    public String correoDe(String token, String correoEsperado) {
        if (token == null || token.isBlank()) return null;
        Long expira = expiraPorToken.get(token);
        if (expira == null) return null;
        if (expira <= Instant.now().toEpochMilli()) {
            revocar(token);
            return null;
        }
        String correo = correosPorToken.get(token);
        if (correo == null) return null;
        if (correoEsperado != null && !correo.equals(correoEsperado.trim().toLowerCase())) return null;
        return correo;
    }

    /** {@code true} solo si el token pertenece al correo y sigue vigente. */
    public boolean autoriza(String token, String correo) {
        return correoDe(token, correo) != null;
    }

    /** Revoca una sesion (logout). */
    public void revocar(String token) {
        if (token == null) return;
        correosPorToken.remove(token);
        expiraPorToken.remove(token);
    }

    /** Cantidad de sesiones vigentes (diagnostico / tests). */
    public int activas() {
        limpiarVencidos();
        return correosPorToken.size();
    }

    private void limpiarVencidos() {
        long ahora = Instant.now().toEpochMilli();
        expiraPorToken.entrySet().removeIf(e -> {
            if (e.getValue() > ahora) return false;
            correosPorToken.remove(e.getKey());
            return true;
        });
        correosPorToken.keySet().removeIf(k -> !expiraPorToken.containsKey(k));
    }
}
