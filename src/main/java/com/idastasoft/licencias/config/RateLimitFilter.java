package com.idastasoft.licencias.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * H-08: throttle de endpoints publicos sensibles del Hub.
 *
 * <p>El core ya tenia rate-limit para el invitado, pero el Hub no tenia ninguno:
 * `/api/admin/login` admitia fuerza bruta ilimitada y `/api/licencias/validar`
 * permitia probar claves sin limite desde una misma IP.</p>
 *
 * <p>Solo cuenta FALLOS (status >= 400): un cliente con licencia valida no se
 * bloquea nunca, aunque haga cientos de validaciones. Se mide por IP (respeta
 * X-Forwarded-For de Render) y grupo de endpoint. Las ventanas se limpian
 * solas al consultarlas; no hay hilo extra.</p>
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long VENTANA_MS = 5L * 60 * 1000;

    private final Map<String, Deque<Long>> fallos = new ConcurrentHashMap<>();

    /** (limite, grupo) por prefijo de ruta; el primero que coincide manda. */
    private record Regla(String prefijo, int limite, String grupo) { }

    private static final Regla[] REGLAS = {
            new Regla("/api/admin/login", 5, "admin-login"),
            new Regla("/api/valoraciones", 10, "valoracion"),
            new Regla("/api/cliente/login", 10, "cliente-login"),
            new Regla("/api/cliente/registro", 10, "cliente-registro"),
            new Regla("/api/licencias/activar-gratuito", 10, "activar-gratuito"),
            new Regla("/api/licencias/validar", 20, "validar"),
    };

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return reglaDe(request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Regla regla = reglaDe(request.getRequestURI());
        String clave = ipDe(request) + "|" + regla.grupo();
        Deque<Long> historial = fallos.computeIfAbsent(clave, k -> new ArrayDeque<>());
        long ahora = Instant.now().toEpochMilli();
        synchronized (historial) {
            while (!historial.isEmpty() && ahora - historial.peekFirst() > VENTANA_MS) historial.pollFirst();
            if (historial.size() >= regla.limite()) {
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(60));
                response.setContentType("application/json");
                response.getWriter().write("{\"ok\":false,\"mensaje\":\"Demasiados intentos. Intente nuevamente en unos minutos.\"}");
                return;
            }
        }
        Capturador capturado = new Capturador(response);
        chain.doFilter(request, capturado);
        if (capturado.getStatus() >= 400) {
            synchronized (historial) { historial.addLast(Instant.now().toEpochMilli()); }
        } else {
            // Un exito limpia la cuenta: no castigamos a quien hace lo correcto.
            synchronized (historial) { historial.clear(); }
        }
    }

    private Regla reglaDe(String uri) {
        for (Regla r : REGLAS) if (uri != null && uri.startsWith(r.prefijo())) return r;
        return null;
    }

    private String ipDe(HttpServletRequest request) {
        String reenviada = request.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) return reenviada.split(",")[0].trim();
        return request.getRemoteAddr() == null ? "?" : request.getRemoteAddr();
    }

    /** Solo capture el status; no amortigua el cuerpo (no cambia el contrato). */
    private static final class Capturador extends HttpServletResponseWrapper {
        private int status = 200;
        Capturador(HttpServletResponse response) { super(response); }
        @Override public void setStatus(int status) { this.status = status; super.setStatus(status); }
        @Override public void sendError(int status) throws IOException { this.status = status; super.sendError(status); }
        @Override public void sendError(int status, String msg) throws IOException { this.status = status; super.sendError(status, msg); }
        @Override public int getStatus() { return status; }
    }
}
