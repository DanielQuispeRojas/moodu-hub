package com.idastasoft.licencias.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/** H-08: 5 fallos de admin login bloquean; un exito limpia el contador. */
class RateLimitFilterTest {

    private MockHttpServletRequest req(String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", uri);
        r.setRemoteAddr("10.0.0.7");
        return r;
    }

    /** FilterChain que solo fija el status (el filtro decide segun >=400). */
    private FilterChain chainCon(int status) {
        return (ServletRequest q, ServletResponse s) -> ((HttpServletResponse) s).setStatus(status);
    }

    @Test
    void cincoFallosBloqueanElSexto() throws Exception {
        RateLimitFilter filtro = new RateLimitFilter();
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filtro.doFilter(req("/api/admin/login"), res, chainCon(401));
            assertEquals(401, res.getStatus());
        }
        MockHttpServletResponse bloqueado = new MockHttpServletResponse();
        filtro.doFilter(req("/api/admin/login"), bloqueado, chainCon(200));
        assertEquals(429, bloqueado.getStatus());
        assertTrue(bloqueado.getContentAsString().contains("Demasiados intentos"));
        assertEquals("60", bloqueado.getHeader("Retry-After"));
    }

    @Test
    void unExitoLimpiaElContador() throws Exception {
        RateLimitFilter filtro = new RateLimitFilter();
        for (int i = 0; i < 4; i++) {
            filtro.doFilter(req("/api/admin/login"), new MockHttpServletResponse(), chainCon(401));
        }
        filtro.doFilter(req("/api/admin/login"), new MockHttpServletResponse(), chainCon(200));
        MockHttpServletResponse despues = new MockHttpServletResponse();
        filtro.doFilter(req("/api/admin/login"), despues, chainCon(200));
        assertEquals(200, despues.getStatus());
    }

    @Test
    void rutasNoSensiblesNoSeTocan() throws Exception {
        RateLimitFilter filtro = new RateLimitFilter();
        MockHttpServletResponse res = new MockHttpServletResponse();
        filtro.doFilter(req("/api/catalogo/modulos"), res, chainCon(200));
        assertEquals(200, res.getStatus());
    }

    @Test
    void xForwardedForSeparaClientesDetrasDelMismoProxy() throws Exception {
        RateLimitFilter filtro = new RateLimitFilter();
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest a = req("/api/admin/login");
            a.addHeader("X-Forwarded-For", "1.1.1.1, 10.0.0.1");
            filtro.doFilter(a, new MockHttpServletResponse(), chainCon(401));
        }
        MockHttpServletRequest b = req("/api/admin/login");
        b.addHeader("X-Forwarded-For", "2.2.2.2, 10.0.0.1");
        MockHttpServletResponse resB = new MockHttpServletResponse();
        filtro.doFilter(b, resB, chainCon(200));
        assertEquals(200, resB.getStatus());
    }
}
