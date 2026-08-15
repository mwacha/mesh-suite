package com.meshsuite.sale.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.fiscal.dto.FiscalCalculationResult;
import com.meshsuite.fiscal.service.FiscalCalculationService;
import com.meshsuite.salesorder.domain.SalesOrderItem;
import com.meshsuite.salesorder.domain.SalesOrder;
import com.meshsuite.salesorder.domain.enums.SalesOrderStatus;
import com.meshsuite.salesorder.exception.SalesOrderNotFoundException;
import com.meshsuite.salesorder.repository.SalesOrderRepository;
import com.meshsuite.product.domain.Product;
import com.meshsuite.sale.domain.Sale;
import com.meshsuite.sale.domain.SaleItem;
import com.meshsuite.sale.dto.SaleItemResponse;
import com.meshsuite.sale.dto.SaleResponse;
import com.meshsuite.sale.dto.SaleSummaryResponse;
import com.meshsuite.sale.exception.SaleNotFoundException;
import com.meshsuite.sale.exception.SaleValidationException;
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
public class SaleService {

    private final SaleRepository saleRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final FiscalCalculationService fiscalCalculationService;
    private final EntityManager entityManager;

    public SaleService(SaleRepository saleRepository, SalesOrderRepository salesOrderRepository,
                        FiscalCalculationService fiscalCalculationService, EntityManager entityManager) {
        this.saleRepository = saleRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.fiscalCalculationService = fiscalCalculationService;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.SALE, action = Action.VIEW)
    public Page<SaleSummaryResponse> list(String search, Pageable pageable) {
        Specification<Sale> spec = Specification.where(SaleSpecifications.withSearch(search));
        return saleRepository.findAll(spec, remapCustomerNameSort(pageable)).map(this::toSummary);
    }

    // SaleSummaryResponse.customerName is a projection, not a direct Sale property --
    // the actual JPA path is the customer association's tradeName. Remap here so
    // sorting by "customerName" (as sent by the frontend) doesn't blow up with a
    // PropertyReferenceException.
    private Pageable remapCustomerNameSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }
        Sort remapped = Sort.by(pageable.getSort().stream()
                .map(order -> "customerName".equals(order.getProperty())
                        ? order.withProperty("customer.tradeName")
                        : order)
                .toList());
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), remapped);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.SALE, action = Action.VIEW)
    public SaleResponse findById(UUID id) {
        return toResponse(saleRepository.findById(id).orElseThrow(SaleNotFoundException::new));
    }

    @Transactional
    @RequiresPermission(module = Module.SALE, action = Action.CREATE)
    public SaleResponse issue(UUID orderId) {
        SalesOrder order = salesOrderRepository.findById(orderId).orElseThrow(SalesOrderNotFoundException::new);
        if (order.getStatus() != SalesOrderStatus.IN_PREPARATION) {
            throw new SaleValidationException(
                    "Só é possível faturar um pedido em preparo. Status atual: " + order.getStatus());
        }

        Sale sale = new Sale();
        sale.setTenantId(order.getTenantId());
        sale.setNumber(nextNumber(order.getTenantId()));
        sale.setOrder(order);
        sale.setCustomer(order.getCustomer());
        sale.setSalesperson(order.getSalesperson());
        sale.setDiscount(order.getDiscount());
        sale.setSubtotal(order.getSubtotal());
        sale.setTotal(order.getTotal());

        BigDecimal totalIcms = BigDecimal.ZERO;
        BigDecimal totalIpi = BigDecimal.ZERO;
        BigDecimal totalPis = BigDecimal.ZERO;
        BigDecimal totalCofins = BigDecimal.ZERO;

        for (SalesOrderItem orderItem : order.getItems()) {
            Product product = orderItem.getProduct();
            if (product.getFiscalRegistration() == null) {
                throw new SaleValidationException(
                        "O produto " + product.getName() + " não possui cadastro fiscal aplicado");
            }
            FiscalCalculationResult calculation = fiscalCalculationService.calculate(
                    product.getFiscalRegistration(), orderItem.getQuantity(), orderItem.getUnitPrice());

            SaleItem saleItem = new SaleItem();
            saleItem.setSale(sale);
            saleItem.setProduct(product);
            saleItem.setQuantity(orderItem.getQuantity());
            saleItem.setUnitPrice(orderItem.getUnitPrice());
            saleItem.setTotalAmount(orderItem.getTotalAmount());
            saleItem.setIcmsAmount(calculation.icmsValue());
            saleItem.setIpiAmount(calculation.ipiValue());
            saleItem.setPisAmount(calculation.pisValue());
            saleItem.setCofinsAmount(calculation.cofinsValue());
            sale.getItems().add(saleItem);

            totalIcms = totalIcms.add(calculation.icmsValue());
            totalIpi = totalIpi.add(calculation.ipiValue());
            totalPis = totalPis.add(calculation.pisValue());
            totalCofins = totalCofins.add(calculation.cofinsValue());
        }

        sale.setIcmsAmount(totalIcms);
        sale.setIpiAmount(totalIpi);
        sale.setPisAmount(totalPis);
        sale.setCofinsAmount(totalCofins);

        Sale saved = saleRepository.saveAndFlush(sale);

        order.setStatus(SalesOrderStatus.INVOICED);
        salesOrderRepository.saveAndFlush(order);

        return toResponse(saved);
    }

    // Atomic UPDATE ... RETURNING against the tenant's single sale_counter row --
    // never COUNT(*)/MAX(number)+1, both of which race under concurrent inserts.
    // Runs inside this method's own @Transactional, so TenantContextAspect has
    // already issued SET LOCAL app.tenant_id before either native query below runs.
    private int nextNumber(UUID tenantId) {
        entityManager.createNativeQuery(
                        "INSERT INTO sale_counter (tenant_id, next_number) VALUES (:tenantId, 1) " +
                                "ON CONFLICT (tenant_id) DO NOTHING")
                .setParameter("tenantId", tenantId)
                .executeUpdate();

        Object result = entityManager.createNativeQuery(
                        "UPDATE sale_counter SET next_number = next_number + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING next_number - 1")
                .setParameter("tenantId", tenantId)
                .getSingleResult();
        return ((Number) result).intValue();
    }

    private SaleSummaryResponse toSummary(Sale s) {
        return new SaleSummaryResponse(s.getId(), s.getNumber(), s.getCustomer().getTradeName(),
                s.getIssueDate(), s.getTotal());
    }

    private SaleResponse toResponse(Sale s) {
        List<SaleItemResponse> items = s.getItems().stream()
                .map(i -> new SaleItemResponse(i.getProduct().getId(), i.getProduct().getName(),
                        i.getQuantity(), i.getUnitPrice(), i.getTotalAmount(),
                        i.getIcmsAmount(), i.getIpiAmount(), i.getPisAmount(), i.getCofinsAmount()))
                .toList();
        return new SaleResponse(s.getId(), s.getNumber(), s.getOrder().getId(), s.getOrder().getNumber(),
                s.getCustomer().getId(), s.getCustomer().getTradeName(),
                s.getSalesperson().getId(), s.getSalesperson().getName(),
                s.getIssueDate(), s.getDiscount(), s.getSubtotal(), s.getTotal(),
                s.getIcmsAmount(), s.getIpiAmount(), s.getPisAmount(), s.getCofinsAmount(), items);
    }
}
