package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.ItemRequest;
import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.EntidadeAuditada;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Operacao;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraDeNegocioException;
import com.umc.pediupatrao.exception.TransicaoInvalidaException;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Regras de pedido: total calculado no servidor, maquina de estados, desconto e cancelamento. */
@Service
@RequiredArgsConstructor
public class PedidoService {

    static final BigDecimal DESCONTO_MAXIMO = BigDecimal.valueOf(20);
    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final int TAMANHO_MAX_JUSTIFICATIVA = 500;

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ClienteService clienteService;
    private final AuditoriaService auditoria;
    private final Clock clock;

    @PreAuthorize("hasAnyRole('ATENDENTE','GERENTE')")
    @Transactional
    public Pedido criar(PedidoRequest dados, Ator ator) {
        clienteService.buscarPorId(dados.clienteId())
                .orElseThrow(() -> new RegraDeNegocioException("Cliente inexistente."));

        Pedido pedido = new Pedido();
        pedido.setClienteId(dados.clienteId());
        pedido.setTipoEntrega(dados.tipoEntrega());
        pedido.setItens(dados.itens().stream().map(this::montarItem).toList());
        pedido.setDescontoPercentual(BigDecimal.ZERO);
        pedido.setValorTotal(calcularTotal(pedido.getItens(), BigDecimal.ZERO));
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setCriadoPor(ator.username());
        pedido.setDataHoraEntrada(LocalDateTime.now(clock));

        Pedido salvo = pedidoRepository.save(pedido);
        auditoria.registrar(ator, Operacao.PEDIDO_CRIADO, EntidadeAuditada.PEDIDO, salvo.getId(),
                AuditoriaService.campos(), AuditoriaService.campos(
                        "clienteId", salvo.getClienteId(),
                        "tipoEntrega", salvo.getTipoEntrega(),
                        "itens", resumoDosItens(salvo.getItens()),
                        "valorTotal", salvo.getValorTotal(),
                        "status", salvo.getStatus()),
                null);
        return salvo;
    }

    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll();
    }

    public Pedido obter(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
    }

    /** Avanca o pedido pelo fluxo; a saida (entrega ou retirada) registra data/hora e responsavel. */
    @PreAuthorize("hasRole('GERENTE')")
    @Transactional
    public Pedido atualizarStatus(String id, StatusPedido destino, Ator ator) {
        Pedido pedido = obter(id);
        StatusPedido anterior = pedido.getStatus();

        if (anterior == StatusPedido.CANCELADO) {
            throw new TransicaoInvalidaException("Pedido cancelado não pode ser alterado.");
        }
        if (destino == StatusPedido.CANCELADO) {
            throw new TransicaoInvalidaException("Para cancelar use a operação de cancelamento (GERENTE, com justificativa).");
        }
        if (!anterior.proximos(pedido.getTipoEntrega()).contains(destino)) {
            throw new TransicaoInvalidaException(
                    "Transição inválida: %s -> %s.".formatted(anterior, destino));
        }

        pedido.setStatus(destino);
        LocalDateTime saidaAnterior = pedido.getSaidaEm();
        if (destino.registraSaida()) {
            pedido.setSaidaEm(LocalDateTime.now(clock));
            pedido.setSaidaPor(ator.username());
        }
        Pedido salvo = pedidoRepository.save(pedido);

        auditoria.registrar(ator, Operacao.PEDIDO_STATUS_ALTERADO, EntidadeAuditada.PEDIDO, id,
                AuditoriaService.campos("status", anterior, "saidaEm", saidaAnterior, "saidaPor", null),
                AuditoriaService.campos("status", destino, "saidaEm", salvo.getSaidaEm(), "saidaPor", salvo.getSaidaPor()),
                null);
        return salvo;
    }

    /** Desconto percentual (maximo 20%) antes da saida; substitui o desconto anterior. Exclusivo do GERENTE. */
    @PreAuthorize("hasRole('GERENTE')")
    @Transactional
    public Pedido aplicarDesconto(String id, BigDecimal percentual, Ator ator) {
        Pedido pedido = obter(id);
        exigirAntesDaSaida(pedido, "receber desconto");
        BigDecimal arredondado = percentual == null ? null : percentual.setScale(2, RoundingMode.HALF_UP);
        if (arredondado == null || arredondado.signum() <= 0 || arredondado.compareTo(DESCONTO_MAXIMO) > 0) {
            throw new RegraDeNegocioException("O desconto deve ser maior que 0 e no máximo 20%.");
        }

        BigDecimal descontoAnterior = pedido.getDescontoPercentual();
        BigDecimal totalAnterior = pedido.getValorTotal();
        pedido.setDescontoPercentual(arredondado);
        pedido.setValorTotal(calcularTotal(pedido.getItens(), pedido.getDescontoPercentual()));
        Pedido salvo = pedidoRepository.save(pedido);

        auditoria.registrar(ator, Operacao.PEDIDO_DESCONTO_APLICADO, EntidadeAuditada.PEDIDO, id,
                AuditoriaService.campos("descontoPercentual", descontoAnterior, "valorTotal", totalAnterior),
                AuditoriaService.campos("descontoPercentual", salvo.getDescontoPercentual(),
                        "valorTotal", salvo.getValorTotal()),
                null);
        return salvo;
    }

    /** Cancela antes da saida, com justificativa; preserva todos os dados e bloqueia alteracoes futuras. */
    @PreAuthorize("hasRole('GERENTE')")
    @Transactional
    public Pedido cancelar(String id, String justificativa, Ator ator) {
        Pedido pedido = obter(id);
        exigirAntesDaSaida(pedido, "ser cancelado");
        if (justificativa == null || justificativa.isBlank() || justificativa.length() > TAMANHO_MAX_JUSTIFICATIVA) {
            throw new RegraDeNegocioException(
                    "Informe a justificativa do cancelamento (até %d caracteres).".formatted(TAMANHO_MAX_JUSTIFICATIVA));
        }

        StatusPedido anterior = pedido.getStatus();
        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setCanceladoEm(LocalDateTime.now(clock));
        pedido.setCanceladoPor(ator.username());
        pedido.setJustificativaCancelamento(justificativa.strip());
        Pedido salvo = pedidoRepository.save(pedido);

        auditoria.registrar(ator, Operacao.PEDIDO_CANCELADO, EntidadeAuditada.PEDIDO, id,
                AuditoriaService.campos("status", anterior),
                AuditoriaService.campos("status", StatusPedido.CANCELADO),
                salvo.getJustificativaCancelamento());
        return salvo;
    }

    /** Total = soma(preco x quantidade) com o desconto percentual, em 2 casas (HALF_UP). */
    static BigDecimal calcularTotal(List<ItemPedido> itens, BigDecimal descontoPercentual) {
        BigDecimal bruto = itens.stream().map(ItemPedido::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fator = BigDecimal.ONE.subtract(descontoPercentual.divide(CEM));
        return bruto.multiply(fator).setScale(2, RoundingMode.HALF_UP);
    }

    private ItemPedido montarItem(ItemRequest item) {
        Produto produto = produtoRepository.findById(item.produtoId())
                .filter(p -> Boolean.TRUE.equals(p.getAtivo()) && p.getPreco() != null)
                .orElseThrow(() -> new RegraDeNegocioException(
                        "Produto inexistente ou inativo: " + item.produtoId()));
        BigDecimal preco = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);
        return new ItemPedido(produto.getId(), produto.getNome(), preco, item.quantidade());
    }

    private void exigirAntesDaSaida(Pedido pedido, String acao) {
        if (!pedido.getStatus().antesDaSaida()) {
            throw new TransicaoInvalidaException(
                    "Pedido %s não pode %s.".formatted(pedido.getStatus(), acao));
        }
    }

    private static String resumoDosItens(List<ItemPedido> itens) {
        return itens.stream()
                .map(i -> "%dx %s (%s)".formatted(i.quantidade(), i.nome(), i.precoUnitario()))
                .collect(Collectors.joining("; "));
    }
}
