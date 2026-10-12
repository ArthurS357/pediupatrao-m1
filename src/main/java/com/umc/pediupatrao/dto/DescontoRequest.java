package com.umc.pediupatrao.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Desconto percentual; o limite de 20% e validado de novo no service. */
public record DescontoRequest(@NotNull(message = "percentual must not be null") BigDecimal percentual) {
}
