package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.UsuarioRequest;
import com.umc.pediupatrao.dto.UsuarioResponse;
import com.umc.pediupatrao.service.UsuarioService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Administracao de usuarios; acesso restrito a ADMIN pelo SecurityConfig. */
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    public List<UsuarioResponse> listarTodos() {
        return StreamSupport.stream(usuarioService.listarTodos().spliterator(), false)
                .map(UsuarioResponse::de)
                .toList();
    }

    @GetMapping("/{id}")
    public UsuarioResponse buscarPorId(@PathVariable("id") String id) {
        return UsuarioResponse.de(usuarioService.obter(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse criarUsuario(@RequestBody @Valid UsuarioRequest usuario) {
        return UsuarioResponse.de(usuarioService.criar(usuario));
    }

    @PutMapping("/{id}")
    public UsuarioResponse atualizarUsuario(@PathVariable("id") String id,
            @RequestBody @Valid UsuarioRequest usuario, @AuthenticationPrincipal UserDetails logado) {
        return UsuarioResponse.de(usuarioService.atualizar(id, usuario, logado.getUsername()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletarUsuario(@PathVariable("id") String id, @AuthenticationPrincipal UserDetails logado) {
        usuarioService.deletar(id, logado.getUsername());
    }
}
