package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.ItemRequest;
import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.EntidadeAuditada;
import com.umc.pediupatrao.entity.FormaPagamento;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Operacao;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraDeNegocioException;
import com.umc.pediupatrao.exception.TransicaoInvalidaException;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PedidoServiceUnitTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-23T17:35:00Z"), ZoneId.of("America/Sao_Paulo"));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 23, 14, 35);
    private static final Ator ATENDENTE = new Ator("atendente01", "ATENDENTE");
    private static final Ator GERENTE = new Ator("gerente01", "GERENTE");

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private ClienteService clienteService;
    @Mock
    private AuditoriaService auditoria;

    private PedidoService tested;

    @BeforeEach
    void setUp() {
        tested = new PedidoService(pedidoRepository, produtoRepository, clienteService, auditoria, CLOCK);
    }

    // ---- criar ----

    @Test
    @DisplayName("UT criar() when items are valid should compute total on server from registered prices")
    void criar_whenItemsAreValid_shouldComputeTotalOnServerFromRegisteredPrices() {
        // given
        givenCliente("c1");
        when(produtoRepository.findById("p1")).thenReturn(Optional.of(produto("p1", "Calabresa", 45.50, true)));
        when(produtoRepository.findById("p2")).thenReturn(Optional.of(produto("p2", "Guarana", 8.00, true)));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(returnsFirstArg());
        PedidoRequest request = new PedidoRequest("c1", TipoEntrega.ENTREGA,
                List.of(new ItemRequest("p1", 2), new ItemRequest("p2", 1)), FormaPagamento.PIX);

        // when
        Pedido result = tested.criar(request, ATENDENTE);

        // then
        assertThat(result.getValorTotal()).isEqualByComparingTo("99.00");
        assertThat(result.getStatus()).isEqualTo(StatusPedido.RECEBIDO);
        assertThat(result.getCriadoPor()).isEqualTo("atendente01");
        assertThat(result.getDataHoraEntrada()).isEqualTo(NOW);
        assertThat(result.getDescontoPercentual()).isEqualByComparingTo("0");
        assertThat(result.getFormaPagamento()).isEqualTo(FormaPagamento.PIX);
        verify(auditoria).registrar(eq(ATENDENTE), eq(Operacao.PEDIDO_CRIADO), eq(EntidadeAuditada.PEDIDO),
                any(), any(), any(), eq(null));
    }

    @Test
    @DisplayName("UT criar() when product is inactive should throw RegraDeNegocioException without saving")
    void criar_whenProductIsInactive_shouldThrowWithoutSaving() {
        // given
        givenCliente("c1");
        when(produtoRepository.findById("p1")).thenReturn(Optional.of(produto("p1", "Calabresa", 45.50, false)));
        PedidoRequest request = new PedidoRequest("c1", TipoEntrega.RETIRADA, List.of(new ItemRequest("p1", 1)), null);

        // when / then
        assertThatThrownBy(() -> tested.criar(request, ATENDENTE)).isInstanceOf(RegraDeNegocioException.class);
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT criar() when customer does not exist should throw RegraDeNegocioException without saving")
    void criar_whenCustomerDoesNotExist_shouldThrowWithoutSaving() {
        // given
        when(clienteService.buscarPorId("x")).thenReturn(Optional.empty());
        PedidoRequest request = new PedidoRequest("x", TipoEntrega.RETIRADA, List.of(new ItemRequest("p1", 1)), null);

        // when / then
        assertThatThrownBy(() -> tested.criar(request, ATENDENTE)).isInstanceOf(RegraDeNegocioException.class);
        verify(pedidoRepository, never()).save(any());
    }

    // ---- atualizarStatus ----

    @Test
    @DisplayName("UT atualizarStatus() when transition is valid should advance and audit")
    void atualizarStatus_whenTransitionIsValid_shouldAdvanceAndAudit() {
        // given
        givenPedido("o1", StatusPedido.RECEBIDO, TipoEntrega.ENTREGA);

        // when
        Pedido result = tested.atualizarStatus("o1", StatusPedido.EM_PREPARACAO, GERENTE);

        // then
        assertThat(result.getStatus()).isEqualTo(StatusPedido.EM_PREPARACAO);
        assertThat(result.getSaidaEm()).isNull();
        verify(auditoria).registrar(eq(GERENTE), eq(Operacao.PEDIDO_STATUS_ALTERADO), eq(EntidadeAuditada.PEDIDO),
                eq("o1"), any(), any(), eq(null));
    }

    @Test
    @DisplayName("UT atualizarStatus() when step is skipped should throw TransicaoInvalidaException")
    void atualizarStatus_whenStepIsSkipped_shouldThrowTransicaoInvalida() {
        // given
        when(pedidoRepository.findById("o1")).thenReturn(Optional.of(pedido(StatusPedido.EM_PREPARACAO, TipoEntrega.ENTREGA)));

        // when / then
        assertThatThrownBy(() -> tested.atualizarStatus("o1", StatusPedido.FINALIZADO, GERENTE))
                .isInstanceOf(TransicaoInvalidaException.class);
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT atualizarStatus() when pickup order leaves should record exit time and responsible")
    void atualizarStatus_whenPickupOrderLeaves_shouldRecordExitTimeAndResponsible() {
        // given
        givenPedido("o1", StatusPedido.PRONTO, TipoEntrega.RETIRADA);

        // when
        Pedido result = tested.atualizarStatus("o1", StatusPedido.RETIRADO, GERENTE);

        // then
        assertThat(result.getStatus()).isEqualTo(StatusPedido.RETIRADO);
        assertThat(result.getSaidaEm()).isEqualTo(NOW);
        assertThat(result.getSaidaPor()).isEqualTo("gerente01");
    }

    @Test
    @DisplayName("UT atualizarStatus() when delivery order is marked RETIRADO should throw TransicaoInvalidaException")
    void atualizarStatus_whenDeliveryOrderIsMarkedRetirado_shouldThrowTransicaoInvalida() {
        // given
        when(pedidoRepository.findById("o1")).thenReturn(Optional.of(pedido(StatusPedido.PRONTO, TipoEntrega.ENTREGA)));

        // when / then
        assertThatThrownBy(() -> tested.atualizarStatus("o1", StatusPedido.RETIRADO, GERENTE))
                .isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    @DisplayName("UT atualizarStatus() when order is cancelled should throw TransicaoInvalidaException")
    void atualizarStatus_whenOrderIsCancelled_shouldThrowTransicaoInvalida() {
        // given
        when(pedidoRepository.findById("o1")).thenReturn(Optional.of(pedido(StatusPedido.CANCELADO, TipoEntrega.ENTREGA)));

        // when / then
        assertThatThrownBy(() -> tested.atualizarStatus("o1", StatusPedido.EM_PREPARACAO, GERENTE))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("cancelado");
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT atualizarStatus() when target is CANCELADO should require the cancellation operation")
    void atualizarStatus_whenTargetIsCancelado_shouldRequireCancellationOperation() {
        // given
        when(pedidoRepository.findById("o1")).thenReturn(Optional.of(pedido(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA)));

        // when / then
        assertThatThrownBy(() -> tested.atualizarStatus("o1", StatusPedido.CANCELADO, GERENTE))
                .isInstanceOf(TransicaoInvalidaException.class);
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT atualizarStatus() when order does not exist should throw RecursoNaoEncontradoException")
    void atualizarStatus_whenOrderDoesNotExist_shouldThrowRecursoNaoEncontrado() {
        // given
        when(pedidoRepository.findById("nope")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> tested.atualizarStatus("nope", StatusPedido.EM_PREPARACAO, GERENTE))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // ---- aplicarDesconto ----

    @Test
    @DisplayName("UT aplicarDesconto() when percentage is 10 should recompute total and audit")
    void aplicarDesconto_whenPercentageIsTen_shouldRecomputeTotalAndAudit() {
        // given
        givenPedido("o1", StatusPedido.EM_PREPARACAO, TipoEntrega.ENTREGA);

        // when
        Pedido result = tested.aplicarDesconto("o1", new BigDecimal("10"), GERENTE);

        // then
        assertThat(result.getDescontoPercentual()).isEqualByComparingTo("10");
        assertThat(result.getValorTotal()).isEqualByComparingTo("90.00");
        verify(auditoria).registrar(eq(GERENTE), eq(Operacao.PEDIDO_DESCONTO_APLICADO), eq(EntidadeAuditada.PEDIDO),
                eq("o1"), any(), any(), eq(null));
    }

    @Test
    @DisplayName("UT aplicarDesconto() when percentage is exactly 20 should be accepted")
    void aplicarDesconto_whenPercentageIsExactlyTwenty_shouldBeAccepted() {
        // given
        givenPedido("o1", StatusPedido.RECEBIDO, TipoEntrega.ENTREGA);

        // when
        Pedido result = tested.aplicarDesconto("o1", new BigDecimal("20"), GERENTE);

        // then
        assertThat(result.getValorTotal()).isEqualByComparingTo("80.00");
    }

    @ParameterizedTest(name = "percentage {0}")
    @ValueSource(strings = {"20.01", "50", "0", "-5"})
    @DisplayName("UT aplicarDesconto() when percentage is out of range should throw RegraDeNegocioException")
    void aplicarDesconto_whenPercentageIsOutOfRange_shouldThrowRegraDeNegocio(String percentage) {
        // given
        when(pedidoRepository.findById("o1")).thenReturn(Optional.of(pedido(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA)));

        // when / then
        assertThatThrownBy(() -> tested.aplicarDesconto("o1", new BigDecimal(percentage), GERENTE))
                .isInstanceOf(RegraDeNegocioException.class);
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT aplicarDesconto() when percentage rounds to zero should throw and keep previous discount")
    void aplicarDesconto_whenPercentageRoundsToZero_shouldThrowAndKeepPreviousDiscount() {
        // given
        when(pedidoRepository.findById("o1")).thenReturn(Optional.of(pedido(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA)));

        // when / then
        assertThatThrownBy(() -> tested.aplicarDesconto("o1", new BigDecimal("0.001"), GERENTE))
                .isInstanceOf(RegraDeNegocioException.class);
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT aplicarDesconto() when order already left should throw TransicaoInvalidaException")
    void aplicarDesconto_whenOrderAlreadyLeft_shouldThrowTransicaoInvalida() {
        // given
        when(pedidoRepository.findById("o1")).thenReturn(Optional.of(pedido(StatusPedido.SAIU_PARA_ENTREGA, TipoEntrega.ENTREGA)));

        // when / then
        assertThatThrownBy(() -> tested.aplicarDesconto("o1", new BigDecimal("10"), GERENTE))
                .isInstanceOf(TransicaoInvalidaException.class);
        verify(pedidoRepository, never()).save(any());
    }

    // ---- cancelar ----

    @Test
    @DisplayName("UT cancelar() when order has not left should cancel keeping history and audit with justification")
    void cancelar_whenOrderHasNotLeft_shouldCancelKeepingHistoryAndAudit() {
        // given
        givenPedido("o1", StatusPedido.PRONTO, TipoEntrega.ENTREGA);

        // when
        Pedido result = tested.cancelar("o1", "  Cliente desistiu  ", GERENTE);

        // then
        assertThat(result.getStatus()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(result.getJustificativaCancelamento()).isEqualTo("Cliente desistiu");
        assertThat(result.getCanceladoPor()).isEqualTo("gerente01");
        assertThat(result.getCanceladoEm()).isEqualTo(NOW);
        assertThat(result.getItens()).hasSize(1);
        assertThat(result.getValorTotal()).isEqualByComparingTo("100.00");
        verify(auditoria).registrar(eq(GERENTE), eq(Operacao.PEDIDO_CANCELADO), eq(EntidadeAuditada.PEDIDO),
                eq("o1"), any(), any(), eq("Cliente desistiu"));
    }

    @ParameterizedTest(name = "justification [{0}]")
    @ValueSource(strings = {"", "   "})
    @DisplayName("UT cancelar() when justification is blank should throw RegraDeNegocioException")
    void cancelar_whenJustificationIsBlank_shouldThrowRegraDeNegocio(String justification) {
        // given
        when(pedidoRepository.findById("o1")).thenReturn(Optional.of(pedido(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA)));

        // when / then
        assertThatThrownBy(() -> tested.cancelar("o1", justification, GERENTE))
                .isInstanceOf(RegraDeNegocioException.class);
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT cancelar() when order already left should throw TransicaoInvalidaException")
    void cancelar_whenOrderAlreadyLeft_shouldThrowTransicaoInvalida() {
        // given
        when(pedidoRepository.findById("o1")).thenReturn(Optional.of(pedido(StatusPedido.RETIRADO, TipoEntrega.RETIRADA)));

        // when / then
        assertThatThrownBy(() -> tested.cancelar("o1", "motivo", GERENTE))
                .isInstanceOf(TransicaoInvalidaException.class);
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT cancelar() when order is already cancelled should throw TransicaoInvalidaException")
    void cancelar_whenOrderIsAlreadyCancelled_shouldThrowTransicaoInvalida() {
        // given
        when(pedidoRepository.findById("o1")).thenReturn(Optional.of(pedido(StatusPedido.CANCELADO, TipoEntrega.ENTREGA)));

        // when / then
        assertThatThrownBy(() -> tested.cancelar("o1", "motivo", GERENTE))
                .isInstanceOf(TransicaoInvalidaException.class);
    }

    // ---- calcularTotal ----

    @Test
    @DisplayName("UT calcularTotal() when discount leaves fractions should round half up to two places")
    void calcularTotal_whenDiscountLeavesFractions_shouldRoundHalfUp() {
        // given
        List<ItemPedido> items = List.of(new ItemPedido("p1", "Pizza", new BigDecimal("33.33"), 3));

        // when
        BigDecimal total = PedidoService.calcularTotal(items, new BigDecimal("12.50"));

        // then
        assertThat(total).isEqualByComparingTo("87.49");
    }

    private void givenCliente(String id) {
        when(clienteService.buscarPorId(id)).thenReturn(Optional.of(new Cliente()));
    }

    private void givenPedido(String id, StatusPedido status, TipoEntrega type) {
        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido(status, type)));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(returnsFirstArg());
    }

    private Pedido pedido(StatusPedido status, TipoEntrega type) {
        Pedido pedido = new Pedido();
        pedido.setId("o1");
        pedido.setStatus(status);
        pedido.setTipoEntrega(type);
        pedido.setItens(List.of(new ItemPedido("p1", "Pizza", new BigDecimal("50.00"), 2)));
        pedido.setDescontoPercentual(BigDecimal.ZERO);
        pedido.setValorTotal(new BigDecimal("100.00"));
        return pedido;
    }

    private Produto produto(String id, String name, double price, boolean active) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome(name);
        produto.setPreco(price);
        produto.setAtivo(active);
        return produto;
    }
}
