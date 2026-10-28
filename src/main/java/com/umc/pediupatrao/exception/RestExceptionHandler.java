package com.umc.pediupatrao.exception;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Respostas de erro da API REST como {@link ProblemDetail} (RFC 9457). */
@RestControllerAdvice(annotations = RestController.class)
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    /** Entrega 401/403 ao Spring Security em vez de deixar o catch-all responder 500. */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void handleSecurityException(Exception exception) throws Exception {
        throw exception;
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail handleRecursoNaoEncontrado(RecursoNaoEncontradoException exception) {
        return problema(HttpStatus.NOT_FOUND, exception);
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ProblemDetail handleRegraDeNegocio(RegraDeNegocioException exception) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, exception);
    }

    @ExceptionHandler(TransicaoInvalidaException.class)
    public ProblemDetail handleTransicaoInvalida(TransicaoInvalidaException exception) {
        return problema(HttpStatus.CONFLICT, exception);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleConflitoDeEscrita(OptimisticLockingFailureException exception) {
        log.debug("Conflito de escrita concorrente", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "O registro foi alterado por outro usuário; recarregue e tente novamente.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleException(Exception exception) {
        log.error("Falha nao tratada", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "The operation could not be completed");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        List<Map<String, String>> erros = exception.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", e.isBindingFailure()
                        // falha de conversao (data, enum): a mensagem do Spring expoe tipos Java internos
                        ? "%s has an invalid value: %s".formatted(e.getField(), e.getRejectedValue())
                        : String.valueOf(e.getDefaultMessage())))
                .toList();
        problema.setProperty("errors", erros);
        return ResponseEntity.badRequest().body(problema);
    }

    /** Restricao em parametro avulso (ex.: {@code @Max} em {@code limit}): mesmo formato, com {@code errors}. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        // Um objeto validado (ex.: @Valid DashboardFilter) chega como ParameterErrors: o campo e o do objeto,
        // nao o nome Java do parametro. Um parametro avulso (ex.: limit) usa o proprio nome.
        List<Map<String, String>> erros = exception.getParameterValidationResults().stream()
                .flatMap(resultado -> resultado instanceof ParameterErrors errosDoObjeto
                        ? errosDoObjeto.getFieldErrors().stream().map(erro -> Map.of(
                                "field", erro.getField(), "message", String.valueOf(erro.getDefaultMessage())))
                        : resultado.getResolvableErrors().stream().map(erro -> Map.of(
                                "field", String.valueOf(resultado.getMethodParameter().getParameterName()),
                                "message", String.valueOf(erro.getDefaultMessage()))))
                .toList();
        problema.setProperty("errors", erros);
        return ResponseEntity.badRequest().body(problema);
    }

    private ProblemDetail problema(HttpStatus status, RuntimeException exception) {
        log.debug("{} -> {}", exception.getClass().getSimpleName(), status);
        return ProblemDetail.forStatusAndDetail(status, exception.getMessage());
    }
}
