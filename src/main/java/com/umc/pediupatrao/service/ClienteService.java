package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.EntidadeAuditada;
import com.umc.pediupatrao.entity.Operacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraDeNegocioException;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro de clientes; toda inclusao, alteracao e exclusao gera evento de auditoria. */
@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final PedidoRepository pedidoRepository;
    private final AuditoriaService auditoria;

    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    /** Mapa id -> nome para exibicao nas telas (nome ausente vira travessao). */
    public Map<String, String> nomesPorId() {
        return clienteRepository.findAll().stream()
                .collect(Collectors.toMap(Cliente::getId, c -> Objects.toString(c.getNome(), "—")));
    }

    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    @PreAuthorize("hasRole('GERENTE')")
    @Transactional
    public void excluir(String id, Ator ator) {
        Cliente existente = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado."));
        if (pedidoRepository.existsByClienteId(id)) {
            throw new RegraDeNegocioException("Cliente possui pedidos e não pode ser excluído.");
        }
        clienteRepository.delete(existente);
        auditoria.registrar(ator, Operacao.CLIENTE_EXCLUIDO, EntidadeAuditada.CLIENTE, id,
                campos(existente), Map.of(), null);
    }

    /** Cria (sem id) ou atualiza (com id) o cliente, auditando os campos que mudaram. */
    @PreAuthorize("hasRole('ATENDENTE')")
    @Transactional
    public Cliente salvar(Cliente cliente, Ator ator) {
        if (cliente.getId() == null) {
            Cliente novo = clienteRepository.save(cliente);
            auditoria.registrar(ator, Operacao.CLIENTE_CRIADO, EntidadeAuditada.CLIENTE, novo.getId(),
                    Map.of(), campos(novo), null);
            return novo;
        }
        Cliente anterior = clienteRepository.findById(cliente.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado para atualização."));
        Map<String, String> camposAnteriores = campos(anterior);
        Cliente atualizado = clienteRepository.save(cliente);
        auditoria.registrar(ator, Operacao.CLIENTE_ALTERADO, EntidadeAuditada.CLIENTE, atualizado.getId(),
                camposAnteriores, campos(atualizado), null);
        return atualizado;
    }

    private static Map<String, String> campos(Cliente c) {
        return AuditoriaService.campos(
                "nome", c.getNome(),
                "telefone", c.getTelefone(),
                "cep", c.getCep(),
                "logradouro", c.getLogradouro(),
                "numero", c.getNumero(),
                "complemento", c.getComplemento(),
                "bairro", c.getBairro(),
                "cidade", c.getCidade(),
                "estado", c.getEstado(),
                "endereco", c.getEndereco());
    }
}
