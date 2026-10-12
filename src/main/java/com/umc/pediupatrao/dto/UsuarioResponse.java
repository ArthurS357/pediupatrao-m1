package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.Usuario;

/** Visao publica do usuario: nunca inclui a senha nem o hash. */
public record UsuarioResponse(String id, String username, String role) {

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getUsername(), usuario.getRole());
    }
}
