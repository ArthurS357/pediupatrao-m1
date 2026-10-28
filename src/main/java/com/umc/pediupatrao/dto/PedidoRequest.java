package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.FormaPagamento;
import com.umc.pediupatrao.entity.TipoEntrega;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Novo pedido: o cliente informa so o que quer; preco, total, status e responsavel sao do servidor.
 * {@code formaPagamento} e opcional para nao quebrar clientes da API anteriores ao dashboard.
 */
public record PedidoRequest(
        @NotBlank(message = "clienteId must not be blank") String clienteId,
        @NotNull(message = "tipoEntrega must not be null") TipoEntrega tipoEntrega,
        @NotEmpty(message = "itens must not be empty") List<@Valid ItemRequest> itens,
        FormaPagamento formaPagamento) {
}
