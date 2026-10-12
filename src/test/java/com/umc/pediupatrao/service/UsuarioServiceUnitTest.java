package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.UsuarioRequest;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RegraDeNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceUnitTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SessoesAtivas sessoes;

    @InjectMocks
    private UsuarioService tested;

    @Test
    @DisplayName("UT criar() when data is valid should store the encoded password and the chosen profile")
    void criar_whenDataIsValid_shouldStoreEncodedPasswordAndProfile() {
        // given
        when(usuarioRepository.findByUsername("maria")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("senha-forte-1")).thenReturn("ENCODED");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(returnsFirstArg());

        // when
        Usuario result = tested.criar(new UsuarioRequest("maria", "senha-forte-1", "GERENTE"));

        // then
        assertThat(result.getPassword()).isEqualTo("ENCODED");
        assertThat(result.getRole()).isEqualTo("GERENTE");
    }

    @Test
    @DisplayName("UT criar() when role is unknown should throw RegraDeNegocioException without saving")
    void criar_whenRoleIsUnknown_shouldThrowWithoutSaving() {
        // when / then
        assertThatThrownBy(() -> tested.criar(new UsuarioRequest("maria", "senha-forte-1", "SUPERADMIN")))
                .isInstanceOf(RegraDeNegocioException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT criar() when username is already taken should throw RegraDeNegocioException")
    void criar_whenUsernameIsAlreadyTaken_shouldThrow() {
        // given
        Usuario existing = usuario("u1", "maria", "ATENDENTE");
        when(usuarioRepository.findByUsername("maria")).thenReturn(Optional.of(existing));

        // when / then
        assertThatThrownBy(() -> tested.criar(new UsuarioRequest("maria", "senha-forte-1", "ATENDENTE")))
                .isInstanceOf(RegraDeNegocioException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT criar() when password is too short should throw RegraDeNegocioException")
    void criar_whenPasswordIsTooShort_shouldThrow() {
        // given
        when(usuarioRepository.findByUsername("maria")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> tested.criar(new UsuarioRequest("maria", "curta", "ATENDENTE")))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    @DisplayName("UT atualizar() when admin changes own profile should throw RegraDeNegocioException")
    void atualizar_whenAdminChangesOwnProfile_shouldThrow() {
        // given
        when(usuarioRepository.findById("u1")).thenReturn(Optional.of(usuario("u1", "admin", "ADMIN")));
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario("u1", "admin", "ADMIN")));

        // when / then
        assertThatThrownBy(() -> tested.atualizar("u1", new UsuarioRequest("admin", null, "ATENDENTE"), "admin"))
                .isInstanceOf(RegraDeNegocioException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT atualizar() when admin changes another user's profile should persist it")
    void atualizar_whenAdminChangesAnotherUsersProfile_shouldPersist() {
        // given
        when(usuarioRepository.findById("u2")).thenReturn(Optional.of(usuario("u2", "joao", "ATENDENTE")));
        when(usuarioRepository.findByUsername("joao")).thenReturn(Optional.of(usuario("u2", "joao", "ATENDENTE")));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(returnsFirstArg());

        // when
        Usuario result = tested.atualizar("u2", new UsuarioRequest("joao", "", "GERENTE"), "admin");

        // then
        assertThat(result.getRole()).isEqualTo("GERENTE");
        ArgumentCaptor<Usuario> saved = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(saved.capture());
        assertThat(saved.getValue().getPassword()).isEqualTo("hash-antigo");
        verify(sessoes).encerrar("joao");
    }

    @Test
    @DisplayName("UT deletar() when admin deletes another user should delete and end that user sessions")
    void deletar_whenAdminDeletesAnotherUser_shouldDeleteAndEndSessions() {
        // given
        Usuario target = usuario("u2", "joao", "GERENTE");
        when(usuarioRepository.findById("u2")).thenReturn(Optional.of(target));

        // when
        tested.deletar("u2", "admin");

        // then
        verify(usuarioRepository).delete(target);
        verify(sessoes).encerrar("joao");
    }

    @Test
    @DisplayName("UT deletar() when admin deletes self should throw RegraDeNegocioException")
    void deletar_whenAdminDeletesSelf_shouldThrow() {
        // given
        when(usuarioRepository.findById("u1")).thenReturn(Optional.of(usuario("u1", "admin", "ADMIN")));

        // when / then
        assertThatThrownBy(() -> tested.deletar("u1", "admin")).isInstanceOf(RegraDeNegocioException.class);
        verify(usuarioRepository, never()).delete(any());
    }

    private Usuario usuario(String id, String username, String role) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setUsername(username);
        usuario.setPassword("hash-antigo");
        usuario.setRole(role);
        return usuario;
    }
}
