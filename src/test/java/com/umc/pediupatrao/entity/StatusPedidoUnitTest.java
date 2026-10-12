package com.umc.pediupatrao.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Holds down the mandatory order flow: RECEBIDO to FINALIZADO, with CANCELADO outside it. */
class StatusPedidoUnitTest {

    @ParameterizedTest(name = "{0} / {1} -> {2}")
    @MethodSource("validTransitions")
    @DisplayName("UT proximos() when order is on the mandatory flow should allow only the next step")
    void proximos_whenOrderIsOnMandatoryFlow_shouldAllowOnlyNextStep(
            StatusPedido from, TipoEntrega type, StatusPedido expected) {
        // when / then
        assertThat(from.proximos(type)).containsExactly(expected);
    }

    @Test
    @DisplayName("UT proximos() when order is FINALIZADO or CANCELADO should allow nothing")
    void proximos_whenOrderIsTerminal_shouldAllowNothing() {
        // when / then
        assertThat(StatusPedido.FINALIZADO.proximos(TipoEntrega.ENTREGA)).isEmpty();
        assertThat(StatusPedido.CANCELADO.proximos(TipoEntrega.RETIRADA)).isEmpty();
    }

    @Test
    @DisplayName("UT proximos() when any status should never offer CANCELADO as a plain transition")
    void proximos_whenAnyStatus_shouldNeverOfferCancelado() {
        // when / then
        for (StatusPedido status : StatusPedido.values()) {
            assertThat(status.proximos(TipoEntrega.ENTREGA)).doesNotContain(StatusPedido.CANCELADO);
            assertThat(status.proximos(TipoEntrega.RETIRADA)).doesNotContain(StatusPedido.CANCELADO);
        }
    }

    @Test
    @DisplayName("UT antesDaSaida() should be true only for RECEBIDO, EM_PREPARACAO and PRONTO")
    void antesDaSaida_shouldBeTrueOnlyBeforeExit() {
        // when / then
        assertThat(StatusPedido.values())
                .filteredOn(StatusPedido::antesDaSaida)
                .containsExactly(StatusPedido.RECEBIDO, StatusPedido.EM_PREPARACAO, StatusPedido.PRONTO);
    }

    @Test
    @DisplayName("UT registraSaida() should be true only for SAIU_PARA_ENTREGA and RETIRADO")
    void registraSaida_shouldBeTrueOnlyForExitStatuses() {
        // when / then
        assertThat(StatusPedido.values())
                .filteredOn(StatusPedido::registraSaida)
                .containsExactly(StatusPedido.SAIU_PARA_ENTREGA, StatusPedido.RETIRADO);
    }

    private static Stream<Arguments> validTransitions() {
        return Stream.of(
                Arguments.of(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA, StatusPedido.EM_PREPARACAO),
                Arguments.of(StatusPedido.EM_PREPARACAO, TipoEntrega.RETIRADA, StatusPedido.PRONTO),
                Arguments.of(StatusPedido.PRONTO, TipoEntrega.ENTREGA, StatusPedido.SAIU_PARA_ENTREGA),
                Arguments.of(StatusPedido.PRONTO, TipoEntrega.RETIRADA, StatusPedido.RETIRADO),
                Arguments.of(StatusPedido.SAIU_PARA_ENTREGA, TipoEntrega.ENTREGA, StatusPedido.FINALIZADO),
                Arguments.of(StatusPedido.RETIRADO, TipoEntrega.RETIRADA, StatusPedido.FINALIZADO));
    }
}
