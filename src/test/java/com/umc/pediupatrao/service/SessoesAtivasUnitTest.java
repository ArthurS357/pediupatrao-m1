package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

class SessoesAtivasUnitTest {

    private SessionRegistry registry;
    private SessoesAtivas tested;

    @BeforeEach
    void setUp() {
        registry = new SessionRegistryImpl();
        tested = new SessoesAtivas(registry);
    }

    @Test
    @DisplayName("UT encerrar() when user has open sessions should expire only that user's sessions")
    void encerrar_whenUserHasOpenSessions_shouldExpireOnlyThatUsersSessions() {
        // given
        UserDetails joao = user("joao");
        UserDetails maria = user("maria");
        registry.registerNewSession("s-joao", joao);
        registry.registerNewSession("s-maria", maria);

        // when
        tested.encerrar("joao");

        // then
        assertThat(registry.getSessionInformation("s-joao").isExpired()).isTrue();
        assertThat(registry.getSessionInformation("s-maria").isExpired()).isFalse();
    }

    @Test
    @DisplayName("UT encerrar() when user has no sessions should do nothing")
    void encerrar_whenUserHasNoSessions_shouldDoNothing() {
        // given
        registry.registerNewSession("s-maria", user("maria"));

        // when
        tested.encerrar("ninguem");

        // then
        assertThat(registry.getSessionInformation("s-maria").isExpired()).isFalse();
    }

    private UserDetails user(String username) {
        return User.withUsername(username).password("x").roles("ATENDENTE").build();
    }
}
