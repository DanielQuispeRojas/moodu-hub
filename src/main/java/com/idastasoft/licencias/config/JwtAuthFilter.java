package com.idastasoft.licencias.config;

import com.idastasoft.licencias.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        //OJO: el filterChain.doFilter VA FUERA del try. Si queda dentro, una
        //excepcion del CONTROLLER (ej. un parse de fecha en el body) cae aqui y
        //se responde 401 "Sesion invalida" tapando el error real (bug 2026-09-15:
        //crear modulo con fechaPublicacion vacia parecia fallo de sesion).
        final String username;
        try {
            if (!jwtService.tokenValido(token)) {
                responderNoAutorizado(response);
                return;
            }
            username = jwtService.extractUsername(token);
        } catch (Exception e) {
            log.error("Error procesando token JWT en {}: {}", request.getRequestURI(), e.getMessage());
            responderNoAutorizado(response);
            return;
        }

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(username, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authToken);

        filterChain.doFilter(request, response);
    }

    private void responderNoAutorizado(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/json");
        response.getWriter().write("{\"ok\":false,\"mensaje\":\"Sesion invalida o expirada\"}");
    }
}
