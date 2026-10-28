package com.umc.pediupatrao.dashboard;

import com.umc.pediupatrao.entity.FormaPagamento;
import com.umc.pediupatrao.entity.StatusPedido;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Filtros transversais do dashboard (RF05), recebidos como query params em todos os endpoints.
 * Periodo ausente vira os ultimos 30 dias; a consistencia do periodo e regra do {@link DashboardService}.
 */
public record DashboardFilter(
        @Schema(description = "First day of the period, inclusive (defaults to 29 days before to)",
                example = "2026-10-01", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate from,

        @Schema(description = "Last day of the period, inclusive (defaults to today)",
                example = "2026-10-31", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate to,

        @Schema(description = "Filter by order status (sales metrics ignore CANCELADO when absent)",
                example = "FINALIZADO", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        StatusPedido status,

        @Schema(description = "Filter by product category, as registered in the product type",
                example = "Pizza", maxLength = 50, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 50, message = "category must be at most {max} characters")
        @Pattern(regexp = "^[\\p{L}\\p{N} _-]+$", message = "category must contain only letters, digits, spaces, _ or -")
        String category,

        @Schema(description = "Filter by payment method", example = "PIX",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        FormaPagamento paymentMethod) {
}
