package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.CancelamentoRequest;
import com.umc.pediupatrao.dto.DescontoRequest;
import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.service.Ator;
import com.umc.pediupatrao.service.PedidoService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API de pedidos. Quem pode o que esta no SecurityConfig; as regras de negocio, no PedidoService. */
@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Pedido criarPedido(@RequestBody @Valid PedidoRequest pedido, @AuthenticationPrincipal UserDetails logado) {
        return pedidoService.criar(pedido, Ator.de(logado));
    }

    @GetMapping
    public List<Pedido> listarPedidos() {
        return pedidoService.listarPedidos();
    }

    @GetMapping("/{id}")
    public Pedido buscarPorId(@PathVariable("id") String id) {
        return pedidoService.obter(id);
    }

    @PutMapping("/{id}/status")
    public Pedido atualizarStatus(@PathVariable("id") String id, @RequestParam("status") StatusPedido status,
            @AuthenticationPrincipal UserDetails logado) {
        return pedidoService.atualizarStatus(id, status, Ator.de(logado));
    }

    @PostMapping("/{id}/desconto")
    public Pedido aplicarDesconto(@PathVariable("id") String id, @RequestBody @Valid DescontoRequest desconto,
            @AuthenticationPrincipal UserDetails logado) {
        return pedidoService.aplicarDesconto(id, desconto.percentual(), Ator.de(logado));
    }

    @PostMapping("/{id}/cancelamento")
    public Pedido cancelar(@PathVariable("id") String id, @RequestBody @Valid CancelamentoRequest cancelamento,
            @AuthenticationPrincipal UserDetails logado) {
        return pedidoService.cancelar(id, cancelamento.justificativa(), Ator.de(logado));
    }
}
