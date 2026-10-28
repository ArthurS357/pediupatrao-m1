package com.umc.pediupatrao.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.Set;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documento OpenAPI (/v3/api-docs) e Swagger UI (/swagger-ui.html) da API do dashboard. */
@Configuration
public class OpenApiConfig {

    /** 401 (sem corpo) e 403 (JSON de erro padrao do Boot) vem do Spring Security, nao sao ProblemDetail. */
    private static final Set<String> SECURITY_CODES = Set.of("401", "403");

    /** Corpo de erro do RestExceptionHandler (RFC 9457), com as propriedades no nivel de cima. */
    private static final Schema<?> PROBLEM = new ObjectSchema()
            .addProperty("type", new StringSchema().example("about:blank"))
            .addProperty("title", new StringSchema().example("Unprocessable Entity"))
            .addProperty("status", new IntegerSchema().example(422))
            .addProperty("detail", new StringSchema().example("O período consultado deve ter no máximo 366 dias."))
            .addProperty("instance", new StringSchema().example("/api/dashboard/kpis"))
            .addProperty("errors", new ArraySchema().description("Only on validation failures")
                    .items(new ObjectSchema()
                            .addProperty("field", new StringSchema().example("limit"))
                            .addProperty("message", new StringSchema().example("limit must be at most 50"))));

    @Bean
    OpenAPI dashboardOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Pediu Patrão - Dashboard API")
                .version("1.0")
                .description("Sales indicators of the Pediu Patrão ordering system. Session login required."));
    }

    /** Todo 4xx documentado e um ProblemDetail, nao o corpo de sucesso que o springdoc inferiria. */
    @Bean
    OperationCustomizer problemDetailOnClientErrors() {
        return (operation, handlerMethod) -> {
            operation.getResponses().forEach((code, response) -> {
                if (code.startsWith("4") && !SECURITY_CODES.contains(code)) {
                    response.setContent(new Content()
                            .addMediaType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    new MediaType().schema(PROBLEM)));
                } else if (SECURITY_CODES.contains(code)) {
                    response.setContent(null);
                }
            });
            return operation;
        };
    }
}
