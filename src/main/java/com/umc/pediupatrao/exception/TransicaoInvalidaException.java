package com.umc.pediupatrao.exception;

/** Operacao incompativel com o estado atual do pedido; respondida como 409. */
public class TransicaoInvalidaException extends RuntimeException {

    public TransicaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
