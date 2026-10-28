package com.umc.pediupatrao.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** Como o cliente paga o pedido; pedidos gravados antes deste campo ficam sem forma de pagamento. */
@Schema(description = "Payment method of the order", enumAsRef = true)
public enum FormaPagamento {
    DINHEIRO, PIX, CARTAO_CREDITO, CARTAO_DEBITO
}
