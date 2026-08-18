package com.meshsuite.purchaseinvoice.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.fiscal.dto.FiscalCalculationResult;
import com.meshsuite.fiscal.service.FiscalCalculationService;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.payable.dto.AccountsPayableInstallmentInput;
import com.meshsuite.payable.service.AccountsPayableService;
import com.meshsuite.product.domain.Product;
import com.meshsuite.purchaseinvoice.domain.PurchaseInvoice;
import com.meshsuite.purchaseinvoice.domain.PurchaseInvoiceItem;
import com.meshsuite.purchaseinvoice.dto.InstallmentInput;
import com.meshsuite.purchaseinvoice.dto.PurchaseInvoiceItemResponse;
import com.meshsuite.purchaseinvoice.dto.PurchaseInvoiceRequest;
import com.meshsuite.purchaseinvoice.dto.PurchaseInvoiceResponse;
import com.meshsuite.purchaseinvoice.dto.PurchaseInvoiceSummaryResponse;
import com.meshsuite.purchaseinvoice.exception.PurchaseInvoiceNotFoundException;
import com.meshsuite.purchaseinvoice.exception.PurchaseInvoiceValidationException;
import com.meshsuite.purchaseinvoice.repository.PurchaseInvoiceRepository;
import com.meshsuite.purchaseinvoice.repository.specification.PurchaseInvoiceSpecifications;
import com.meshsuite.purchaseorder.domain.PurchaseOrder;
import com.meshsuite.purchaseorder.domain.PurchaseOrderItem;
import com.meshsuite.purchaseorder.domain.enums.PurchaseOrderStatus;
import com.meshsuite.purchaseorder.exception.PurchaseOrderNotFoundException;
import com.meshsuite.purchaseorder.repository.PurchaseOrderRepository;
import com.meshsuite.stock.domain.enums.StockMovementOrigin;
import com.meshsuite.stock.domain.enums.StockMovementType;
import com.meshsuite.stock.service.StockService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseInvoiceService {

    private final PurchaseInvoiceRepository purchaseInvoiceRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final FiscalCalculationService fiscalCalculationService;
    private final StockService stockService;
    private final AccountsPayableService accountsPayableService;
    private final EntityManager entityManager;

    public PurchaseInvoiceService(PurchaseInvoiceRepository purchaseInvoiceRepository,
                                   PurchaseOrderRepository purchaseOrderRepository,
                                   FiscalCalculationService fiscalCalculationService,
                                   StockService stockService,
                                   AccountsPayableService accountsPayableService,
                                   EntityManager entityManager) {
        this.purchaseInvoiceRepository = purchaseInvoiceRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.fiscalCalculationService = fiscalCalculationService;
        this.stockService = stockService;
        this.accountsPayableService = accountsPayableService;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PURCHASE_INVOICE, action = Action.VIEW)
    public Page<PurchaseInvoiceSummaryResponse> list(String search, Pageable pageable) {
        Specification<PurchaseInvoice> spec = Specification.where(PurchaseInvoiceSpecifications.withSearch(search));
        return purchaseInvoiceRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PURCHASE_INVOICE, action = Action.VIEW)
    public PurchaseInvoiceResponse findById(UUID id) {
        return toResponse(purchaseInvoiceRepository.findById(id).orElseThrow(PurchaseInvoiceNotFoundException::new));
    }

    @Transactional
    @RequiresPermission(module = Module.PURCHASE_INVOICE, action = Action.CREATE)
    public PurchaseInvoiceResponse issue(UUID purchaseOrderId, PurchaseInvoiceRequest request, UUID userId) {
        PurchaseOrder order = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(PurchaseOrderNotFoundException::new);
        if (order.getStatus() != PurchaseOrderStatus.OPEN) {
            throw new PurchaseInvoiceValidationException(
                    "Só é possível lançar uma compra a partir de uma ordem em aberto. Status atual: " + order.getStatus());
        }

        Partner supplier = order.getSupplier();
        if (purchaseInvoiceRepository.findBySupplierIdAndInvoiceNumber(supplier.getId(), request.invoiceNumber()).isPresent()) {
            throw new PurchaseInvoiceValidationException(
                    "Já existe uma nota " + request.invoiceNumber() + " cadastrada para este fornecedor");
        }
        if (request.entryDate().isBefore(request.issueDate())) {
            throw new PurchaseInvoiceValidationException("A data de entrada não pode ser anterior à data de emissão");
        }

        PurchaseInvoice invoice = new PurchaseInvoice();
        invoice.setTenantId(order.getTenantId());
        invoice.setNumber(nextNumber(order.getTenantId()));
        invoice.setInvoiceNumber(request.invoiceNumber());
        invoice.setSeries(request.series());
        invoice.setModel(request.model());
        invoice.setPurchaseOrder(order);
        invoice.setSupplier(supplier);
        invoice.setIssueDate(request.issueDate());
        invoice.setEntryDate(request.entryDate());
        invoice.setDiscount(order.getDiscount());
        invoice.setSubtotal(order.getSubtotal());
        invoice.setTotal(order.getTotal());

        BigDecimal totalIcms = BigDecimal.ZERO;
        BigDecimal totalIpi = BigDecimal.ZERO;
        BigDecimal totalPis = BigDecimal.ZERO;
        BigDecimal totalCofins = BigDecimal.ZERO;

        for (PurchaseOrderItem orderItem : order.getItems()) {
            Product product = orderItem.getProduct();
            if (product.getFiscalRegistration() == null) {
                throw new PurchaseInvoiceValidationException(
                        "O produto " + product.getName() + " não possui cadastro fiscal aplicado");
            }
            FiscalCalculationResult calculation = fiscalCalculationService.calculate(
                    product.getFiscalRegistration(), orderItem.getQuantity(), orderItem.getUnitPrice());

            PurchaseInvoiceItem invoiceItem = new PurchaseInvoiceItem();
            invoiceItem.setPurchaseInvoice(invoice);
            invoiceItem.setProduct(product);
            invoiceItem.setQuantity(orderItem.getQuantity());
            invoiceItem.setUnitPrice(orderItem.getUnitPrice());
            invoiceItem.setTotalValue(orderItem.getTotalValue());
            invoiceItem.setIcmsAmount(calculation.icmsValue());
            invoiceItem.setIpiAmount(calculation.ipiValue());
            invoiceItem.setPisAmount(calculation.pisValue());
            invoiceItem.setCofinsAmount(calculation.cofinsValue());
            invoice.getItems().add(invoiceItem);

            totalIcms = totalIcms.add(calculation.icmsValue());
            totalIpi = totalIpi.add(calculation.ipiValue());
            totalPis = totalPis.add(calculation.pisValue());
            totalCofins = totalCofins.add(calculation.cofinsValue());
        }

        invoice.setIcmsAmount(totalIcms);
        invoice.setIpiAmount(totalIpi);
        invoice.setPisAmount(totalPis);
        invoice.setCofinsAmount(totalCofins);

        BigDecimal installmentsTotal = request.installments().stream()
                .map(InstallmentInput::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (installmentsTotal.compareTo(invoice.getTotal()) != 0) {
            throw new PurchaseInvoiceValidationException(
                    "A soma das parcelas (" + installmentsTotal + ") não bate com o total da nota (" + invoice.getTotal() + ")");
        }

        PurchaseInvoice saved = purchaseInvoiceRepository.saveAndFlush(invoice);

        for (PurchaseInvoiceItem item : saved.getItems()) {
            stockService.adjustBalance(order.getTenantId(), item.getProduct().getId(), StockMovementType.INBOUND,
                    item.getQuantity(), StockMovementOrigin.PURCHASE, saved.getId(), userId, null);
        }

        List<AccountsPayableInstallmentInput> payableInstallments = request.installments().stream()
                .map(i -> new AccountsPayableInstallmentInput(i.amount(), i.dueDate()))
                .toList();
        accountsPayableService.createInstallments(order.getTenantId(), supplier.getId(), saved.getId(), payableInstallments);

        order.setStatus(PurchaseOrderStatus.RECEIVED);
        purchaseOrderRepository.saveAndFlush(order);

        return toResponse(saved);
    }

    // Atomic UPDATE ... RETURNING against the tenant's single
    // purchase_invoice_counter row -- never COUNT(*)/MAX(number)+1, both of which
    // race under concurrent inserts. Same pattern as every other counter in this
    // codebase (PurchaseOrder, Sale, AccountsPayable).
    private int nextNumber(UUID tenantId) {
        entityManager.createNativeQuery(
                        "INSERT INTO purchase_invoice_counter (tenant_id, next_number) VALUES (:tenantId, 1) " +
                                "ON CONFLICT (tenant_id) DO NOTHING")
                .setParameter("tenantId", tenantId)
                .executeUpdate();

        Object result = entityManager.createNativeQuery(
                        "UPDATE purchase_invoice_counter SET next_number = next_number + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING next_number - 1")
                .setParameter("tenantId", tenantId)
                .getSingleResult();
        return ((Number) result).intValue();
    }

    private PurchaseInvoiceSummaryResponse toSummary(PurchaseInvoice i) {
        return new PurchaseInvoiceSummaryResponse(i.getId(), i.getNumber(), i.getInvoiceNumber(),
                i.getSupplier().getTradeName(), i.getIssueDate(), i.getTotal());
    }

    private PurchaseInvoiceResponse toResponse(PurchaseInvoice i) {
        List<PurchaseInvoiceItemResponse> items = i.getItems().stream()
                .map(it -> new PurchaseInvoiceItemResponse(it.getProduct().getId(), it.getProduct().getName(),
                        it.getQuantity(), it.getUnitPrice(), it.getTotalValue(),
                        it.getIcmsAmount(), it.getIpiAmount(), it.getPisAmount(), it.getCofinsAmount()))
                .toList();
        return new PurchaseInvoiceResponse(i.getId(), i.getNumber(), i.getInvoiceNumber(), i.getSeries(), i.getModel(),
                i.getPurchaseOrder().getId(), i.getPurchaseOrder().getNumber(),
                i.getSupplier().getId(), i.getSupplier().getTradeName(),
                i.getIssueDate(), i.getEntryDate(),
                i.getDiscount(), i.getSubtotal(), i.getTotal(),
                i.getIcmsAmount(), i.getIpiAmount(), i.getPisAmount(), i.getCofinsAmount(), items);
    }
}
