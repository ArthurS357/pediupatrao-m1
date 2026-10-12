package com.umc.pediupatrao.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Item solicitado: produto e quantidade; o preco vem do cadastro do produto. */
public record ItemRequest(
        @NotBlank(message = "produtoId must not be blank") String produtoId,
        @Min(value = 1, message = "quantidade must be at least 1")
        @Max(value = 100, message = "quantidade must be at most 100") int quantidade) {
}
