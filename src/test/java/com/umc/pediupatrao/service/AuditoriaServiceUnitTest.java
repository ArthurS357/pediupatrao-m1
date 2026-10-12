package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.Alteracao;
import com.umc.pediupatrao.entity.AuditoriaEvento;
import com.umc.pediupatrao.entity.EntidadeAuditada;
import com.umc.pediupatrao.entity.Operacao;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceUnitTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-23T17:35:00Z"), ZoneId.of("America/Sao_Paulo"));
    private static final Ator ATENDENTE = new Ator("atendente01", "ATENDENTE");

    @Mock
    private AuditoriaRepository repository;
    @Mock
    private MongoTemplate mongoTemplate;

    private AuditoriaService tested;

    @BeforeEach
    void setUp() {
        tested = new AuditoriaService(repository, mongoTemplate, CLOCK);
    }

    @Test
    @DisplayName("UT registrar() when a field changed should save event with actor, time and old and new values")
    void registrar_whenFieldChanged_shouldSaveEventWithActorTimeAndValues() {
        // given
        Map<String, String> before = AuditoriaService.campos("logradouro", "Rua das Flores", "cidade", "Sao Paulo");
        Map<String, String> after = AuditoriaService.campos("logradouro", "Av. Brasil", "cidade", "Sao Paulo");

        // when
        tested.registrar(ATENDENTE, Operacao.CLIENTE_ALTERADO, EntidadeAuditada.CLIENTE, "c1", before, after, null);

        // then
        ArgumentCaptor<AuditoriaEvento> captor = ArgumentCaptor.forClass(AuditoriaEvento.class);
        verify(repository).save(captor.capture());
        AuditoriaEvento event = captor.getValue();
        assertThat(event.usuario()).isEqualTo("atendente01");
        assertThat(event.perfil()).isEqualTo("ATENDENTE");
        assertThat(event.dataHora()).isEqualTo(LocalDateTime.of(2026, 9, 23, 14, 35));
        assertThat(event.operacao()).isEqualTo(Operacao.CLIENTE_ALTERADO);
        assertThat(event.entidade()).isEqualTo(EntidadeAuditada.CLIENTE);
        assertThat(event.entidadeId()).isEqualTo("c1");
        assertThat(event.alteracoes()).containsExactly(new Alteracao("logradouro", "Rua das Flores", "Av. Brasil"));
    }

    @Test
    @DisplayName("UT registrar() when nothing changed should not save anything")
    void registrar_whenNothingChanged_shouldNotSave() {
        // given
        Map<String, String> same = AuditoriaService.campos("nome", "Ana");

        // when
        tested.registrar(ATENDENTE, Operacao.CLIENTE_ALTERADO, EntidadeAuditada.CLIENTE, "c1", same, same, null);

        // then
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("UT registrar() when a field looks like a credential should never record it")
    void registrar_whenFieldLooksLikeCredential_shouldNeverRecordIt() {
        // given
        Map<String, String> after = AuditoriaService.campos("password", "segredo", "token", "abc", "nome", "Ana");

        // when
        tested.registrar(ATENDENTE, Operacao.CLIENTE_CRIADO, EntidadeAuditada.CLIENTE, "c1", Map.of(), after, null);

        // then
        ArgumentCaptor<AuditoriaEvento> captor = ArgumentCaptor.forClass(AuditoriaEvento.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().alteracoes()).extracting(Alteracao::campo).containsExactly("nome");
    }

    @Test
    @DisplayName("UT registrar() when justification is given should store it")
    void registrar_whenJustificationIsGiven_shouldStoreIt() {
        // when
        tested.registrar(ATENDENTE, Operacao.PEDIDO_CANCELADO, EntidadeAuditada.PEDIDO, "o1",
                AuditoriaService.campos("status", "PRONTO"), AuditoriaService.campos("status", "CANCELADO"), "Cliente desistiu");

        // then
        ArgumentCaptor<AuditoriaEvento> captor = ArgumentCaptor.forClass(AuditoriaEvento.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().justificativa()).isEqualTo("Cliente desistiu");
    }

    @Test
    @DisplayName("UT consultar() when event carries a phone should return it masked")
    void consultar_whenEventCarriesPhone_shouldReturnItMasked() {
        // given
        AuditoriaEvento stored = new AuditoriaEvento("e1", LocalDateTime.of(2026, 9, 23, 14, 35), "atendente01",
                "ATENDENTE", Operacao.CLIENTE_ALTERADO, EntidadeAuditada.CLIENTE, "c1",
                List.of(new Alteracao("telefone", "11988887777", "11977776666"),
                        new Alteracao("logradouro", "Rua das Flores", "Av. Brasil")),
                null);
        when(mongoTemplate.find(any(Query.class), eq(AuditoriaEvento.class))).thenReturn(List.of(stored));

        // when
        List<AuditoriaEvento> result = tested.consultar(EntidadeAuditada.CLIENTE, "c1");

        // then
        assertThat(result.getFirst().alteracoes()).containsExactly(
                new Alteracao("telefone", "*******7777", "*******6666"),
                new Alteracao("logradouro", "Rua das Flores", "Av. Brasil"));
    }

    @Test
    @DisplayName("UT mascarar() when value is null or short should return it unchanged")
    void mascarar_whenValueIsNullOrShort_shouldReturnUnchanged() {
        // when / then
        assertThat(AuditoriaService.mascarar(null)).isNull();
        assertThat(AuditoriaService.mascarar("1234")).isEqualTo("1234");
    }
}
