package com.umc.pediupatrao.entity;

/** Tipos de operacao registrados na auditoria. */
public enum Operacao {
    PEDIDO_CRIADO,
    PEDIDO_STATUS_ALTERADO,
    PEDIDO_DESCONTO_APLICADO,
    PEDIDO_CANCELADO,
    CLIENTE_CRIADO,
    CLIENTE_ALTERADO,
    CLIENTE_EXCLUIDO
}
