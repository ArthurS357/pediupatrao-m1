package com.umc.pediupatrao.exception;

/** Recurso inexistente; respondido como 404. */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
