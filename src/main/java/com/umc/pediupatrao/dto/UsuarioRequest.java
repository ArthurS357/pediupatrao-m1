package com.umc.pediupatrao.dto;

import jakarta.validation.constraints.NotBlank;

/** Corpo de criacao/alteracao de usuario; a senha e opcional na alteracao. */
public record UsuarioRequest(
        @NotBlank(message = "username must not be blank") String username,
        String password,
        @NotBlank(message = "role must not be blank") String role) {
}
