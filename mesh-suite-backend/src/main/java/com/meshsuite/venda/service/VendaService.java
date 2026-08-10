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
import com.meshsuite.sale.domain.SaleItem;
import com.meshsuite.sale.domain.Sale;
import com.meshsuite.venda.dto.ItemVendaResponse;
import com.meshsuite.venda.dto.VendaResponse;
import com.meshsuite.venda.dto.VendaSummaryResponse;
import com.meshsuite.venda.exception.VendaNaoEncontradaException;
import com.meshsuite.venda.exception.VendaValidacaoException;
import com.meshsuite.sale.repository.SaleRepository;
import com.meshsuite.sale.repository.specification.SaleSpecifications;
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

    private final SaleRepository saleRepository;
    private final PedidoRepository pedidoRepository;
    private final FiscalCalculationService fiscalCalculationService;
    private final EntityManager entityManager;

    public VendaService(SaleRepository saleRepository, PedidoRepository pedidoRepository,
                         FiscalCalculationService fiscalCalculationService, EntityManager entityManager) {
        this.saleRepository = saleRepository;
        this.pedidoRepository = pedidoRepository;
        this.fiscalCalculationService = fiscalCalculationService;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.SALE, action = Action.VIEW)
    public Page<VendaSummaryResponse> listar(String busca, Pageable pageable) {
        Specification<Sale> spec = Specification.where(SaleSpecifications.withSearch(busca));
        return saleRepository.findAll(spec, remapClienteNomeSort(pageable)).map(this::toSummary);
    }

    // VendaSummaryResponse.clienteNome is a projection, not a direct Sale property --
    // the actual JPA path is the customer association's nomeFantasia. Remap here so
    // sorting by "clienteNome" (as sent by the frontend) doesn't blow up with a
    // PropertyReferenceException.
    private Pageable remapClienteNomeSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }
        Sort remapped = Sort.by(pageable.getSort().stream()
                .map(order -> "clienteNome".equals(order.getProperty())
                        ? order.withProperty("customer.nomeFantasia")
                        : order)
                .toList());
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), remapped);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.SALE, action = Action.VIEW)
    public VendaResponse buscarPorId(UUID id) {
        return toResponse(saleRepository.findById(id).orElseThrow(VendaNaoEncontradaException::new));
    }

    @Transactional
    @RequiresPermission(module = Module.SALE, action = Action.CREATE)
    public VendaResponse faturar(UUID pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow(PedidoNaoEncontradoException::new);
        if (pedido.getStatus() != StatusPedido.EM_PREPARO) {
            throw new VendaValidacaoException(
                    "Só é possível faturar um pedido em preparo. Status atual: " + pedido.getStatus());
        }

        Sale sale = new Sale();
        sale.setTenantId(pedido.getTenantId());
        sale.setNumber(proximoNumero(pedido.getTenantId()));
        sale.setOrder(pedido);
        sale.setCustomer(pedido.getCliente());
        sale.setSalesperson(pedido.getVendedor());
        sale.setDiscount(pedido.getDesconto());
        sale.setSubtotal(pedido.getSubtotal());
        sale.setTotal(pedido.getTotal());

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

            SaleItem saleItem = new SaleItem();
            saleItem.setSale(sale);
            saleItem.setProduct(produto);
            saleItem.setQuantity(itemPedido.getQuantidade());
            saleItem.setUnitPrice(itemPedido.getValorUnitario());
            saleItem.setTotalAmount(itemPedido.getValorTotal());
            saleItem.setIcmsAmount(calculo.icmsValue());
            saleItem.setIpiAmount(calculo.ipiValue());
            saleItem.setPisAmount(calculo.pisValue());
            saleItem.setCofinsAmount(calculo.cofinsValue());
            sale.getItems().add(saleItem);

            totalIcms = totalIcms.add(calculo.icmsValue());
            totalIpi = totalIpi.add(calculo.ipiValue());
            totalPis = totalPis.add(calculo.pisValue());
            totalCofins = totalCofins.add(calculo.cofinsValue());
        }

        sale.setIcmsAmount(totalIcms);
        sale.setIpiAmount(totalIpi);
        sale.setPisAmount(totalPis);
        sale.setCofinsAmount(totalCofins);

        Sale salva = saleRepository.saveAndFlush(sale);

        pedido.setStatus(StatusPedido.FATURADO);
        pedidoRepository.saveAndFlush(pedido);

        return toResponse(salva);
    }

    // Atomic UPDATE ... RETURNING against the tenant's single sale_counter row --
    // never COUNT(*)/MAX(number)+1, both of which race under concurrent inserts.
    // Runs inside this method's own @Transactional, so TenantContextAspect has
    // already issued SET LOCAL app.tenant_id before either native query below runs.
    private int proximoNumero(UUID tenantId) {
        entityManager.createNativeQuery(
                        "INSERT INTO sale_counter (tenant_id, next_number) VALUES (:tenantId, 1) " +
                                "ON CONFLICT (tenant_id) DO NOTHING")
                .setParameter("tenantId", tenantId)
                .executeUpdate();

        Object resultado = entityManager.createNativeQuery(
                        "UPDATE sale_counter SET next_number = next_number + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING next_number - 1")
                .setParameter("tenantId", tenantId)
                .getSingleResult();
        return ((Number) resultado).intValue();
    }

    private VendaSummaryResponse toSummary(Sale s) {
        return new VendaSummaryResponse(s.getId(), s.getNumber(), s.getCustomer().getNomeFantasia(),
                s.getIssueDate(), s.getTotal());
    }

    private VendaResponse toResponse(Sale s) {
        List<ItemVendaResponse> itens = s.getItems().stream()
                .map(i -> new ItemVendaResponse(i.getProduct().getId(), i.getProduct().getNome(),
                        i.getQuantity(), i.getUnitPrice(), i.getTotalAmount(),
                        i.getIcmsAmount(), i.getIpiAmount(), i.getPisAmount(), i.getCofinsAmount()))
                .toList();
        return new VendaResponse(s.getId(), s.getNumber(), s.getOrder().getId(), s.getOrder().getNumero(),
                s.getCustomer().getId(), s.getCustomer().getNomeFantasia(),
                s.getSalesperson().getId(), s.getSalesperson().getName(),
                s.getIssueDate(), s.getDiscount(), s.getSubtotal(), s.getTotal(),
                s.getIcmsAmount(), s.getIpiAmount(), s.getPisAmount(), s.getCofinsAmount(), itens);
    }
}
