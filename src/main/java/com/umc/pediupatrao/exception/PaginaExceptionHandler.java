package com.umc.pediupatrao.exception;

import com.umc.pediupatrao.controller.HomeController;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Erros de negocio nas paginas Thymeleaf: volta para a pagina de origem com a mensagem em flash. */
@ControllerAdvice(assignableTypes = HomeController.class)
public class PaginaExceptionHandler {

    @ExceptionHandler({RecursoNaoEncontradoException.class, RegraDeNegocioException.class,
            TransicaoInvalidaException.class})
    public String handleErroDeNegocio(RuntimeException exception, HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("erro", exception.getMessage());
        return "redirect:" + caminhoDeOrigem(request.getHeader("Referer"));
    }

    /** Apenas o path do Referer, para nao redirecionar para fora do sistema. */
    private String caminhoDeOrigem(String referer) {
        if (referer == null) {
            return "/";
        }
        try {
            String path = URI.create(referer).getPath();
            return path != null && path.startsWith("/") && !path.startsWith("//") ? path : "/";
        } catch (IllegalArgumentException invalido) {
            return "/";
        }
    }
}
