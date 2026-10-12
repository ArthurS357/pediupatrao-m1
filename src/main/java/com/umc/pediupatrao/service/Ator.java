package com.umc.pediupatrao.service;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Quem executa a operacao: sempre derivado da sessao autenticada, nunca do corpo da requisicao. */
public record Ator(String username, String perfil) {

    private static final String PREFIXO_ROLE = "ROLE_";

    public static Ator de(UserDetails usuario) {
        String perfil = usuario.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith(PREFIXO_ROLE) ? a.substring(PREFIXO_ROLE.length()) : a)
                .findFirst()
                .orElse("SEM_PERFIL");
        return new Ator(usuario.getUsername(), perfil);
    }
}
