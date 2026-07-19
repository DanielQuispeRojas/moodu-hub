package com.idastasoft.licencias.service;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tokens de sesion de admin en memoria (simple, para uso local). El login admin
 * devuelve un token que el dashboard debe enviar en el header X-Admin-Token.
 */
@Service
public class AdminTokenService {

    private final Map<String, String> tokens = new ConcurrentHashMap<>(); // token -> usuario

    public String crear(String usuario) {
        String token = UUID.randomUUID().toString();
        tokens.put(token, usuario);
        return token;
    }

    public boolean valido(String token) {
        return token != null && tokens.containsKey(token);
    }

    public String usuario(String token) {
        return tokens.get(token);
    }

    public void revocar(String token) {
        tokens.remove(token);
    }
}
