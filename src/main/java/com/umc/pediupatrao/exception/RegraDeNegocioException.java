package com.umc.pediupatrao.exception;

/** Violacao de regra de negocio ou entrada invalida que o cliente pode corrigir; respondida como 422. */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
