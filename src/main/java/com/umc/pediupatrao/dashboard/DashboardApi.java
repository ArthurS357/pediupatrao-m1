package com.umc.pediupatrao.dashboard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;

/**
 * Contrato e documentacao OpenAPI da API do dashboard. Os filtros de {@link DashboardFilter} valem para
 * todos os endpoints; sem periodo, sao os ultimos 30 dias.
 */
@Tag(name = "Dashboard", description = "Read-only sales indicators for the store manager (ADMIN and GERENTE)")
public interface DashboardApi {

    String VALIDATION_FAILED = "Request validation failed";
    String INVALID_PERIOD = "Period is inverted or longer than 366 days";
    String NOT_AUTHENTICATED = "Caller is not authenticated";
    String NOT_ALLOWED = "Caller is neither ADMIN nor GERENTE";

    @Operation(summary = "Get revenue, average ticket and number of orders by period and filters")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sales indicators successfully retrieved"),
            @ApiResponse(responseCode = "400", description = VALIDATION_FAILED),
            @ApiResponse(responseCode = "401", description = NOT_AUTHENTICATED),
            @ApiResponse(responseCode = "403", description = NOT_ALLOWED),
            @ApiResponse(responseCode = "422", description = INVALID_PERIOD)
    })
    Kpis kpis(@ParameterObject @Valid DashboardFilter filter);

    @Operation(summary = "Get sales time series by period, granularity and filters")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sales point list successfully retrieved",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = SalesPoint.class)))),
            @ApiResponse(responseCode = "400", description = VALIDATION_FAILED + " or unknown granularity"),
            @ApiResponse(responseCode = "401", description = NOT_AUTHENTICATED),
            @ApiResponse(responseCode = "403", description = NOT_ALLOWED),
            @ApiResponse(responseCode = "422", description = INVALID_PERIOD)
    })
    List<SalesPoint> salesTrend(@ParameterObject @Valid DashboardFilter filter,
                                @Parameter(description = "Size of each bucket of the series (defaults to DAY)")
                                Granularity granularity);

    @Operation(summary = "Get sales grouped by product category by period and filters")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category sales list successfully retrieved",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CategorySales.class)))),
            @ApiResponse(responseCode = "400", description = VALIDATION_FAILED),
            @ApiResponse(responseCode = "401", description = NOT_AUTHENTICATED),
            @ApiResponse(responseCode = "403", description = NOT_ALLOWED),
            @ApiResponse(responseCode = "422", description = INVALID_PERIOD)
    })
    List<CategorySales> salesByCategory(@ParameterObject @Valid DashboardFilter filter);

    @Operation(summary = "Count orders by status by period and filters (every status listed, zero included)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status count list successfully retrieved",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = StatusCount.class)))),
            @ApiResponse(responseCode = "400", description = VALIDATION_FAILED),
            @ApiResponse(responseCode = "401", description = NOT_AUTHENTICATED),
            @ApiResponse(responseCode = "403", description = NOT_ALLOWED),
            @ApiResponse(responseCode = "422", description = INVALID_PERIOD)
    })
    List<StatusCount> ordersStatus(@ParameterObject @Valid DashboardFilter filter);

    @Operation(summary = "Get best-selling products ranked by quantity or revenue by period and filters")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product sales list successfully retrieved",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProductSales.class)))),
            @ApiResponse(responseCode = "400", description = VALIDATION_FAILED + ", unknown sort or limit out of 1..50"),
            @ApiResponse(responseCode = "401", description = NOT_AUTHENTICATED),
            @ApiResponse(responseCode = "403", description = NOT_ALLOWED),
            @ApiResponse(responseCode = "422", description = INVALID_PERIOD)
    })
    List<ProductSales> topProducts(@ParameterObject @Valid DashboardFilter filter,
                                   @Parameter(description = "Ranking criterion (defaults to QUANTITY)")
                                   ProductRanking sort,
                                   @Parameter(description = "Maximum number of products in the ranking",
                                           schema = @Schema(type = "integer", format = "int32", minimum = "1",
                                                   maximum = "50", defaultValue = "10"))
                                   @Min(value = 1, message = "limit must be at least {value}")
                                   @Max(value = 50, message = "limit must be at most {value}")
                                   int limit);
}
