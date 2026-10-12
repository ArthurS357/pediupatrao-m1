package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Holds down the service-layer guard: discount and cancellation are GERENTE-only even when the
 * controller and URL rules are bypassed, and not even ADMIN gets through.
 */
@SpringJUnitConfig(PedidoServiceSecurityIntegrationTest.Config.class)
class PedidoServiceSecurityIntegrationTest {

    private static final Ator ATOR = new Ator("u", "X");

    @Autowired
    private PedidoService tested;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("IT aplicarDesconto() when caller is ADMIN should be denied")
    void aplicarDesconto_whenCallerIsAdmin_shouldBeDenied() {
        // when / then
        assertThatThrownBy(() -> tested.aplicarDesconto("o1", BigDecimal.TEN, ATOR))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    @DisplayName("IT cancelar() when caller is ATENDENTE should be denied")
    void cancelar_whenCallerIsAtendente_shouldBeDenied() {
        // when / then
        assertThatThrownBy(() -> tested.cancelar("o1", "motivo", ATOR))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    @DisplayName("IT aplicarDesconto() when caller is GERENTE should pass the guard and reach the rules")
    void aplicarDesconto_whenCallerIsGerente_shouldPassGuardAndReachRules() {
        // when / then: the mocked repository finds nothing, which proves the guard let the call through
        assertThatThrownBy(() -> tested.aplicarDesconto("o1", BigDecimal.TEN, ATOR))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        PedidoService pedidoService() {
            return new PedidoService(mock(PedidoRepository.class), mock(ProdutoRepository.class),
                    mock(ClienteService.class), mock(AuditoriaService.class), Clock.systemUTC());
        }
    }
}
