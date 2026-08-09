package com.meshsuite.venda.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.fiscal.dto.FiscalCalculationResult;
import com.meshsuite.fiscal.service.FiscalCalculationService;
import com.meshsuite.pedido.domain.ItemPedido;
import com.meshsuite.pedido.domain.Pedido;
import com.meshsuite.pedido.domain.enums.StatusPedido;
import com.meshsuite.pedido.exception.PedidoNaoEncontradoException;
import com.meshsuite.pedido.repository.PedidoRepository;
import com.meshsuite.produto.domain.Produto;
import com.meshsuite.venda.domain.ItemVenda;
import com.meshsuite.venda.domain.Venda;
import com.meshsuite.venda.dto.ItemVendaResponse;
import com.meshsuite.venda.dto.VendaResponse;
import com.meshsuite.venda.dto.VendaSummaryResponse;
import com.meshsuite.venda.exception.VendaNaoEncontradaException;
import com.meshsuite.venda.exception.VendaValidacaoException;
import com.meshsuite.venda.repository.VendaRepository;
import com.meshsuite.venda.repository.specification.VendaSpecifications;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VendaService {

    private final VendaRepository vendaRepository;
    private final PedidoRepository pedidoRepository;
    private final FiscalCalculationService fiscalCalculationService;
    private final EntityManager entityManager;

    public VendaService(VendaRepository vendaRepository, PedidoRepository pedidoRepository,
                         FiscalCalculationService fiscalCalculationService, EntityManager entityManager) {
        this.vendaRepository = vendaRepository;
        this.pedidoRepository = pedidoRepository;
        this.fiscalCalculationService = fiscalCalculationService;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.SALE, action = Action.VIEW)
    public Page<VendaSummaryResponse> listar(String busca, Pageable pageable) {
        Specification<Venda> spec = Specification.where(VendaSpecifications.comBusca(busca));
        return vendaRepository.findAll(spec, remapClienteNomeSort(pageable)).map(this::toSummary);
    }

    // VendaSummaryResponse.clienteNome is a projection, not a direct Venda property --
    // the actual JPA path is the cliente association's nomeFantasia. Remap here so
    // sorting by "clienteNome" (as sent by the frontend) doesn't blow up with a
    // PropertyReferenceException.
    private Pageable remapClienteNomeSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }
        Sort remapped = Sort.by(pageable.getSort().stream()
                .map(order -> "clienteNome".equals(order.getProperty())
                        ? order.withProperty("cliente.nomeFantasia")
                        : order)
                .toList());
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), remapped);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.SALE, action = Action.VIEW)
    public VendaResponse buscarPorId(UUID id) {
        return toResponse(vendaRepository.findById(id).orElseThrow(VendaNaoEncontradaException::new));
    }

    @Transactional
    @RequiresPermission(module = Module.SALE, action = Action.CREATE)
    public VendaResponse faturar(UUID pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow(PedidoNaoEncontradoException::new);
        if (pedido.getStatus() != StatusPedido.EM_PREPARO) {
            throw new VendaValidacaoException(
                    "Só é possível faturar um pedido em preparo. Status atual: " + pedido.getStatus());
        }

        Venda venda = new Venda();
        venda.setTenantId(pedido.getTenantId());
        venda.setNumero(proximoNumero(pedido.getTenantId()));
        venda.setPedido(pedido);
        venda.setCliente(pedido.getCliente());
        venda.setVendedor(pedido.getVendedor());
        venda.setDesconto(pedido.getDesconto());
        venda.setSubtotal(pedido.getSubtotal());
        venda.setTotal(pedido.getTotal());

        BigDecimal totalIcms = BigDecimal.ZERO;
        BigDecimal totalIpi = BigDecimal.ZERO;
        BigDecimal totalPis = BigDecimal.ZERO;
        BigDecimal totalCofins = BigDecimal.ZERO;

        for (ItemPedido itemPedido : pedido.getItens()) {
            Produto produto = itemPedido.getProduto();
            if (produto.getFiscalRegistration() == null) {
                throw new VendaValidacaoException(
                        "O produto " + produto.getNome() + " não possui cadastro fiscal aplicado");
            }
            FiscalCalculationResult calculo = fiscalCalculationService.calculate(
                    produto.getFiscalRegistration(), itemPedido.getQuantidade(), itemPedido.getValorUnitario());

            ItemVenda itemVenda = new ItemVenda();
            itemVenda.setVenda(venda);
            itemVenda.setProduto(produto);
            itemVenda.setQuantidade(itemPedido.getQuantidade());
            itemVenda.setValorUnitario(itemPedido.getValorUnitario());
            itemVenda.setValorTotal(itemPedido.getValorTotal());
            itemVenda.setValorIcms(calculo.icmsValue());
            itemVenda.setValorIpi(calculo.ipiValue());
            itemVenda.setValorPis(calculo.pisValue());
            itemVenda.setValorCofins(calculo.cofinsValue());
            venda.getItens().add(itemVenda);

            totalIcms = totalIcms.add(calculo.icmsValue());
            totalIpi = totalIpi.add(calculo.ipiValue());
            totalPis = totalPis.add(calculo.pisValue());
            totalCofins = totalCofins.add(calculo.cofinsValue());
        }

        venda.setValorIcms(totalIcms);
        venda.setValorIpi(totalIpi);
        venda.setValorPis(totalPis);
        venda.setValorCofins(totalCofins);

        Venda salva = vendaRepository.saveAndFlush(venda);

        pedido.setStatus(StatusPedido.FATURADO);
        pedidoRepository.saveAndFlush(pedido);

        return toResponse(salva);
    }

    // Atomic UPDATE ... RETURNING against the tenant's single venda_contador row --
    // never COUNT(*)/MAX(numero)+1, both of which race under concurrent inserts.
    // Runs inside this method's own @Transactional, so TenantContextAspect has
    // already issued SET LOCAL app.tenant_id before either native query below runs.
    private int proximoNumero(UUID tenantId) {
        entityManager.createNativeQuery(
                        "INSERT INTO venda_contador (tenant_id, proximo_numero) VALUES (:tenantId, 1) " +
                                "ON CONFLICT (tenant_id) DO NOTHING")
                .setParameter("tenantId", tenantId)
                .executeUpdate();

        Object resultado = entityManager.createNativeQuery(
                        "UPDATE venda_contador SET proximo_numero = proximo_numero + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING proximo_numero - 1")
                .setParameter("tenantId", tenantId)
                .getSingleResult();
        return ((Number) resultado).intValue();
    }

    private VendaSummaryResponse toSummary(Venda v) {
        return new VendaSummaryResponse(v.getId(), v.getNumero(), v.getCliente().getNomeFantasia(),
                v.getDataEmissao(), v.getTotal());
    }

    private VendaResponse toResponse(Venda v) {
        List<ItemVendaResponse> itens = v.getItens().stream()
                .map(i -> new ItemVendaResponse(i.getProduto().getId(), i.getProduto().getNome(),
                        i.getQuantidade(), i.getValorUnitario(), i.getValorTotal(),
                        i.getValorIcms(), i.getValorIpi(), i.getValorPis(), i.getValorCofins()))
                .toList();
        return new VendaResponse(v.getId(), v.getNumero(), v.getPedido().getId(), v.getPedido().getNumero(),
                v.getCliente().getId(), v.getCliente().getNomeFantasia(),
                v.getVendedor().getId(), v.getVendedor().getName(),
                v.getDataEmissao(), v.getDesconto(), v.getSubtotal(), v.getTotal(),
                v.getValorIcms(), v.getValorIpi(), v.getValorPis(), v.getValorCofins(), itens);
    }
}
