package com.umc.pediupatrao.entity;

import java.util.Set;

/**
 * Estados do pedido e a tabela de transicoes validas. CANCELADO nunca e destino de uma transicao
 * comum: so a operacao de cancelamento (GERENTE, com justificativa) leva a ele.
 */
public enum StatusPedido {
    RECEBIDO, EM_PREPARACAO, PRONTO, SAIU_PARA_ENTREGA, RETIRADO, FINALIZADO, CANCELADO;

    /** Destinos permitidos a partir deste estado, conforme o tipo de entrega do pedido. */
    public Set<StatusPedido> proximos(TipoEntrega tipo) {
        return switch (this) {
            case RECEBIDO -> Set.of(EM_PREPARACAO);
            case EM_PREPARACAO -> Set.of(PRONTO);
            case PRONTO -> Set.of(tipo == TipoEntrega.ENTREGA ? SAIU_PARA_ENTREGA : RETIRADO);
            case SAIU_PARA_ENTREGA, RETIRADO -> Set.of(FINALIZADO);
            case FINALIZADO, CANCELADO -> Set.of();
        };
    }

    /** Estados em que o pedido ainda nao saiu: unicos que aceitam desconto ou cancelamento. */
    public boolean antesDaSaida() {
        return this == RECEBIDO || this == EM_PREPARACAO || this == PRONTO;
    }

    /** Estados que registram data/hora e responsavel pela saida. */
    public boolean registraSaida() {
        return this == SAIU_PARA_ENTREGA || this == RETIRADO;
    }
}
