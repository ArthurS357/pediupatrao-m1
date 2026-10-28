package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Pedido da pizzaria. Preco, total, status, responsavel e datas sao definidos pelo servidor;
 * nada disso vem do corpo da requisicao (ver PedidoRequest).
 */
@Document(collection = "pedidos")
@Getter
@Setter
public class Pedido {

    @Id
    private String id;
    @Version
    private Long versao;

    private String clienteId;
    private TipoEntrega tipoEntrega;
    private FormaPagamento formaPagamento;
    private List<ItemPedido> itens;
    private BigDecimal descontoPercentual = BigDecimal.ZERO;
    private BigDecimal valorTotal;
    private StatusPedido status;

    private String criadoPor;
    private LocalDateTime dataHoraEntrada;

    private LocalDateTime saidaEm;
    private String saidaPor;

    private LocalDateTime canceladoEm;
    private String canceladoPor;
    private String justificativaCancelamento;

    /** Destinos validos agora; usado pela tela para mostrar so os botoes cabiveis. */
    public Set<StatusPedido> getProximosStatus() {
        return status == null ? Set.of() : status.proximos(tipoEntrega);
    }

    public boolean isAceitaDescontoOuCancelamento() {
        return status != null && status.antesDaSaida();
    }
}
