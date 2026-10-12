package com.umc.pediupatrao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cancelamento exige justificativa. */
public record CancelamentoRequest(
        @NotBlank(message = "justificativa must not be blank")
        @Size(max = 500, message = "justificativa must be at most 500 characters") String justificativa) {
}
