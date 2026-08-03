package com.meshsuite.pedido;

import com.meshsuite.parceiro.PapelParceiro;
import com.meshsuite.parceiro.Parceiro;
import com.meshsuite.parceiro.ParceiroRepository;
import com.meshsuite.pedido.dto.*;
import com.meshsuite.produto.Produto;
import com.meshsuite.produto.ProdutoRepository;
import com.meshsuite.user.Role;
import com.meshsuite.user.User;
import com.meshsuite.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ParceiroRepository parceiroRepository;
    private final UserRepository userRepository;
    private final ProdutoRepository produtoRepository;
    private final EntityManager entityManager;

    public PedidoService(PedidoRepository pedidoRepository, ParceiroRepository parceiroRepository,
                          UserRepository userRepository, ProdutoRepository produtoRepository,
                          EntityManager entityManager) {
        this.pedidoRepository = pedidoRepository;
        this.parceiroRepository = parceiroRepository;
        this.userRepository = userRepository;
        this.produtoRepository = produtoRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public Page<PedidoSummaryResponse> listar(String busca, StatusPedido status, Pageable pageable) {
        Specification<Pedido> spec = Specification.allOf(
                PedidoSpecifications.comBusca(busca),
                PedidoSpecifications.comStatus(status));
        return pedidoRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public PedidoResumoResponse resumo() {
        long digitados = pedidoRepository.countByStatus(StatusPedido.DIGITADO);
        long emPreparo = pedidoRepository.countByStatus(StatusPedido.EM_PREPARO);
        long faturados = pedidoRepository.countByStatus(StatusPedido.FATURADO);
        return new PedidoResumoResponse(digitados + emPreparo + faturados, digitados, emPreparo, faturados);
    }

    @Transactional(readOnly = true)
    public PedidoResponse buscarPorId(UUID id) {
        return toResponse(buscarEntidadePorId(id));
    }

    @Transactional
    public PedidoResponse criar(UUID tenantId, PedidoRequest request) {
        Parceiro cliente = buscarClienteValido(request.clienteId());
        User vendedor = buscarVendedorValido(request.vendedorId());

        Pedido pedido = new Pedido();
        pedido.setTenantId(tenantId);
        pedido.setNumero(proximoNumero(tenantId));
        aplicar(pedido, cliente, vendedor, request);
        return toResponse(pedidoRepository.saveAndFlush(pedido));
    }

    @Transactional
    public PedidoResponse atualizar(UUID id, PedidoRequest request) {
        Parceiro cliente = buscarClienteValido(request.clienteId());
        User vendedor = buscarVendedorValido(request.vendedorId());

        Pedido pedido = buscarEntidadePorId(id);
        aplicar(pedido, cliente, vendedor, request);
        return toResponse(pedidoRepository.saveAndFlush(pedido));
    }

    @Transactional
    public PedidoResponse avancarStatus(UUID id, StatusPedido novoStatus) {
        Pedido pedido = buscarEntidadePorId(id);
        int atual = pedido.getStatus().ordinal();
        int alvo = novoStatus.ordinal();
        if (alvo != atual + 1) {
            throw new PedidoValidacaoException(
                    "Não é possível avançar de " + pedido.getStatus() + " para " + novoStatus);
        }
        pedido.setStatus(novoStatus);
        return toResponse(pedidoRepository.saveAndFlush(pedido));
    }

    @Transactional
    public void excluir(UUID id) {
        pedidoRepository.delete(buscarEntidadePorId(id));
    }

    private Pedido buscarEntidadePorId(UUID id) {
        return pedidoRepository.findById(id).orElseThrow(PedidoNaoEncontradoException::new);
    }

    private Parceiro buscarClienteValido(UUID clienteId) {
        Parceiro parceiro = parceiroRepository.findById(clienteId)
                .orElseThrow(() -> new PedidoValidacaoException("Cliente não encontrado"));
        if (!parceiro.getPapeis().contains(PapelParceiro.CLIENTE)) {
            throw new PedidoValidacaoException("O parceiro selecionado não tem o papel Cliente");
        }
        return parceiro;
    }

    private User buscarVendedorValido(UUID vendedorId) {
        User user = userRepository.findById(vendedorId)
                .orElseThrow(() -> new PedidoValidacaoException("Vendedor não encontrado"));
        if (user.getRole() != Role.SALES_REP) {
            throw new PedidoValidacaoException("O usuário selecionado não tem o papel Representante");
        }
        return user;
    }

    // Atomic UPDATE ... RETURNING against the tenant's single pedido_contador row --
    // never COUNT(*)/MAX(numero)+1, both of which race under concurrent inserts.
    // Runs inside this method's own @Transactional, so TenantContextAspect has
    // already issued SET LOCAL app.tenant_id before either native query below runs.
    private int proximoNumero(UUID tenantId) {
        entityManager.createNativeQuery(
                        "INSERT INTO pedido_contador (tenant_id, proximo_numero) VALUES (:tenantId, 1) " +
                                "ON CONFLICT (tenant_id) DO NOTHING")
                .setParameter("tenantId", tenantId)
                .executeUpdate();

        Object resultado = entityManager.createNativeQuery(
                        "UPDATE pedido_contador SET proximo_numero = proximo_numero + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING proximo_numero - 1")
                .setParameter("tenantId", tenantId)
                .getSingleResult();
        return ((Number) resultado).intValue();
    }

    private void aplicar(Pedido pedido, Parceiro cliente, User vendedor, PedidoRequest request) {
        pedido.setCliente(cliente);
        pedido.setVendedor(vendedor);
        pedido.setDataPedido(request.dataPedido() != null ? request.dataPedido() : LocalDate.now());
        pedido.setDataEntrega(request.dataEntrega());
        pedido.setDesconto(request.desconto() != null ? request.desconto() : BigDecimal.ZERO);

        pedido.getItens().clear();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemPedidoDto dto : request.itens()) {
            Produto produto = produtoRepository.findById(dto.produtoId())
                    .orElseThrow(() -> new PedidoValidacaoException("Produto não encontrado"));
            ItemPedido item = new ItemPedido();
            item.setPedido(pedido);
            item.setProduto(produto);
            item.setQuantidade(dto.quantidade());
            item.setValorUnitario(dto.valorUnitario());
            BigDecimal valorTotalItem = dto.quantidade().multiply(dto.valorUnitario());
            item.setValorTotal(valorTotalItem);
            pedido.getItens().add(item);
            subtotal = subtotal.add(valorTotalItem);
        }
        pedido.setSubtotal(subtotal);
        pedido.setTotal(subtotal.subtract(pedido.getDesconto()));
    }

    private PedidoSummaryResponse toSummary(Pedido p) {
        return new PedidoSummaryResponse(p.getId(), p.getNumero(), p.getCliente().getNomeFantasia(),
                p.getVendedor().getName(), p.getDataPedido(), p.getTotal(), p.getStatus());
    }

    private PedidoResponse toResponse(Pedido p) {
        List<ItemPedidoResponse> itens = p.getItens().stream()
                .map(i -> new ItemPedidoResponse(i.getProduto().getId(), i.getProduto().getNome(),
                        i.getQuantidade(), i.getValorUnitario(), i.getValorTotal()))
                .toList();
        return new PedidoResponse(p.getId(), p.getNumero(), p.getCliente().getId(), p.getCliente().getNomeFantasia(),
                p.getVendedor().getId(), p.getVendedor().getName(), p.getDataPedido(), p.getDataEntrega(),
                p.getStatus(), p.getDesconto(), p.getSubtotal(), p.getTotal(), itens);
    }
}
