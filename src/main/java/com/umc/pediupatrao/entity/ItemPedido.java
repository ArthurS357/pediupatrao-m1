package com.umc.pediupatrao.entity;

import java.math.BigDecimal;

/** Item do pedido; nome e preco sao copiados do produto no momento do pedido (historico imutavel). */
public record ItemPedido(String produtoId, String nome, BigDecimal precoUnitario, int quantidade) {

    public BigDecimal subtotal() {
        return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
    }
}
