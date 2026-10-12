package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.UsuarioRequest;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraDeNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Administracao de usuarios. Somente ADMIN chega aqui (regra no SecurityConfig); o service ainda
 * impede que o administrador altere o proprio perfil ou se exclua.
 */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{3,32}$");
    private static final int SENHA_MIN = 8;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessoesAtivas sessoes;

    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    public Usuario obter(String id) {
        return buscarPorId(id).orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    public Iterable<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    /** Cria usuario novo; qualquer id vindo do cliente e ignorado. */
    public Usuario criar(UsuarioRequest dados) {
        Perfil perfil = Perfil.exigir(dados.role());
        validarUsername(dados.username(), null);
        exigirSenhaValida(dados.password());

        Usuario usuario = new Usuario();
        usuario.setUsername(dados.username());
        usuario.setPassword(passwordEncoder.encode(dados.password()));
        usuario.setRole(perfil.name());
        return usuarioRepository.save(usuario);
    }

    /** Atualiza username, perfil e, se informada, a senha; {@code usernameAtual} e quem executa a acao. */
    public Usuario atualizar(String id, UsuarioRequest dados, String usernameAtual) {
        Usuario usuario = obter(id);
        Perfil novoPerfil = Perfil.exigir(dados.role());
        validarUsername(dados.username(), id);

        boolean ehOProprio = usuario.getUsername().equals(usernameAtual);
        boolean mudouPerfilOuNome = !novoPerfil.name().equals(usuario.getRole())
                || !dados.username().equals(usuario.getUsername());
        if (ehOProprio && mudouPerfilOuNome) {
            throw new RegraDeNegocioException("Você não pode alterar o próprio perfil ou username.");
        }

        String usernameAnterior = usuario.getUsername();
        usuario.setUsername(dados.username());
        usuario.setRole(novoPerfil.name());
        if (dados.password() != null && !dados.password().isBlank()) {
            exigirSenhaValida(dados.password());
            usuario.setPassword(passwordEncoder.encode(dados.password()));
        }
        Usuario salvo = usuarioRepository.save(usuario);
        if (!ehOProprio) {
            sessoes.encerrar(usernameAnterior);
        }
        return salvo;
    }

    public void deletar(String id, String usernameAtual) {
        Usuario usuario = obter(id);
        if (usuario.getUsername().equals(usernameAtual)) {
            throw new RegraDeNegocioException("Você não pode excluir o próprio usuário.");
        }
        usuarioRepository.delete(usuario);
        sessoes.encerrar(usuario.getUsername());
    }

    private void validarUsername(String username, String idAtual) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new RegraDeNegocioException(
                    "Username deve ter de 3 a 32 caracteres (letras, números, ponto, hífen ou sublinhado).");
        }
        boolean emUso = usuarioRepository.findByUsername(username)
                .filter(existente -> !existente.getId().equals(idAtual))
                .isPresent();
        if (emUso) {
            throw new RegraDeNegocioException("Username já está em uso.");
        }
    }

    private void exigirSenhaValida(String senha) {
        if (senha == null || senha.length() < SENHA_MIN) {
            throw new RegraDeNegocioException("A senha deve ter pelo menos " + SENHA_MIN + " caracteres.");
        }
    }
}
