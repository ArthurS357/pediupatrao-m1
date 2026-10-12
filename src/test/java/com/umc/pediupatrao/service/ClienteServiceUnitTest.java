package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.EntidadeAuditada;
import com.umc.pediupatrao.entity.Operacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraDeNegocioException;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClienteServiceUnitTest {

    private static final Ator ATENDENTE = new Ator("atendente01", "ATENDENTE");

    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private AuditoriaService auditoria;

    @InjectMocks
    private ClienteService tested;

    @Test
    @DisplayName("UT salvar() when address changes should audit old and new values")
    void salvar_whenAddressChanges_shouldAuditOldAndNewValues() {
        // given
        Cliente stored = cliente("c1", "Rua das Flores");
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(stored));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(returnsFirstArg());

        // when
        tested.salvar(cliente("c1", "Av. Brasil"), ATENDENTE);

        // then
        ArgumentCaptor<Map<String, String>> before = mapCaptor();
        ArgumentCaptor<Map<String, String>> after = mapCaptor();
        verify(auditoria).registrar(eq(ATENDENTE), eq(Operacao.CLIENTE_ALTERADO), eq(EntidadeAuditada.CLIENTE),
                eq("c1"), before.capture(), after.capture(), eq(null));
        assertThat(before.getValue()).containsEntry("logradouro", "Rua das Flores");
        assertThat(after.getValue()).containsEntry("logradouro", "Av. Brasil");
    }

    @Test
    @DisplayName("UT salvar() when customer is new should audit creation with empty before")
    void salvar_whenCustomerIsNew_shouldAuditCreationWithEmptyBefore() {
        // given
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> {
            Cliente saved = invocation.getArgument(0);
            saved.setId("c9");
            return saved;
        });

        // when
        tested.salvar(cliente(null, "Rua A"), ATENDENTE);

        // then
        verify(auditoria).registrar(eq(ATENDENTE), eq(Operacao.CLIENTE_CRIADO), eq(EntidadeAuditada.CLIENTE),
                eq("c9"), eq(Map.of()), any(), eq(null));
    }

    @Test
    @DisplayName("UT salvar() when updating a missing customer should throw RecursoNaoEncontradoException")
    void salvar_whenUpdatingMissingCustomer_shouldThrowRecursoNaoEncontrado() {
        // given
        when(clienteRepository.findById("nope")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> tested.salvar(cliente("nope", "Rua A"), ATENDENTE))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(clienteRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT excluir() when customer exists should delete and audit exclusion")
    void excluir_whenCustomerExists_shouldDeleteAndAuditExclusion() {
        // given
        Cliente stored = cliente("c1", "Rua A");
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(stored));

        // when
        tested.excluir("c1", ATENDENTE);

        // then
        verify(clienteRepository).delete(stored);
        verify(auditoria).registrar(eq(ATENDENTE), eq(Operacao.CLIENTE_EXCLUIDO), eq(EntidadeAuditada.CLIENTE),
                eq("c1"), any(), eq(Map.of()), eq(null));
    }

    @Test
    @DisplayName("UT excluir() when customer has orders should throw RegraDeNegocioException without deleting")
    void excluir_whenCustomerHasOrders_shouldThrowWithoutDeleting() {
        // given
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente("c1", "Rua A")));
        when(pedidoRepository.existsByClienteId("c1")).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> tested.excluir("c1", ATENDENTE)).isInstanceOf(RegraDeNegocioException.class);
        verify(clienteRepository, never()).delete(any());
    }

    @Test
    @DisplayName("UT excluir() when customer is missing should throw RecursoNaoEncontradoException")
    void excluir_whenCustomerIsMissing_shouldThrowRecursoNaoEncontrado() {
        // given
        when(clienteRepository.findById("nope")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> tested.excluir("nope", ATENDENTE))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, String>> mapCaptor() {
        return ArgumentCaptor.forClass((Class<Map<String, String>>) (Class<?>) Map.class);
    }

    private Cliente cliente(String id, String street) {
        Cliente cliente = new Cliente();
        cliente.setId(id);
        cliente.setNome("Ana");
        cliente.setTelefone("11988887777");
        cliente.setLogradouro(street);
        return cliente;
    }
}
