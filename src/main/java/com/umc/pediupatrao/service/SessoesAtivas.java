package com.umc.pediupatrao.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/** Encerra as sessoes abertas de um usuario, para que mudanca de perfil ou exclusao valham de imediato. */
@Component
@RequiredArgsConstructor
public class SessoesAtivas {

    private final SessionRegistry sessionRegistry;

    public void encerrar(String username) {
        sessionRegistry.getAllPrincipals().stream()
                .filter(principal -> principal instanceof UserDetails detalhes
                        && detalhes.getUsername().equals(username))
                .forEach(principal -> sessionRegistry.getAllSessions(principal, false)
                        .forEach(SessionInformation::expireNow));
    }
}
