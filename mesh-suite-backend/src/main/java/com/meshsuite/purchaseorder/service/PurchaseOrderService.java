package com.meshsuite.purchaseorder.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.auth.exception.PermissionDeniedException;
import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.repository.PartnerRepository;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.repository.ProductRepository;
import com.meshsuite.purchaseorder.domain.PurchaseOrder;
import com.meshsuite.purchaseorder.domain.PurchaseOrderItem;
import com.meshsuite.purchaseorder.domain.enums.PurchaseOrderStatus;
import com.meshsuite.purchaseorder.dto.*;
import com.meshsuite.purchaseorder.exception.PurchaseOrderNotFoundException;
import com.meshsuite.purchaseorder.exception.PurchaseOrderValidationException;
import com.meshsuite.purchaseorder.repository.PurchaseOrderRepository;
import com.meshsuite.purchaseorder.repository.specification.PurchaseOrderSpecifications;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PartnerRepository parceiroRepository;
    private final UserRepository userRepository;
    private final ProductRepository produtoRepository;
    private final EntityManager entityManager;

    public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository, PartnerRepository parceiroRepository,
                                 UserRepository userRepository, ProductRepository produtoRepository,
                                 EntityManager entityManager) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.parceiroRepository = parceiroRepository;
        this.userRepository = userRepository;
        this.produtoRepository = produtoRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PURCHASE, action = Action.VIEW)
    public Page<PurchaseOrderSummaryResponse> list(String search, PurchaseOrderStatus status, Pageable pageable) {
        Specification<PurchaseOrder> spec = Specification.allOf(
                PurchaseOrderSpecifications.withSearch(search),
                PurchaseOrderSpecifications.withStatus(status));
        return purchaseOrderRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PURCHASE, action = Action.VIEW)
    public PurchaseOrderCountsResponse counts() {
        long open = purchaseOrderRepository.countByStatus(PurchaseOrderStatus.OPEN);
        long received = purchaseOrderRepository.countByStatus(PurchaseOrderStatus.RECEIVED);
        long cancelled = purchaseOrderRepository.countByStatus(PurchaseOrderStatus.CANCELLED);
        return new PurchaseOrderCountsResponse(open + received + cancelled, open, received, cancelled);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PURCHASE, action = Action.VIEW)
    public PurchaseOrderResponse findById(UUID id) {
        return toResponse(findEntityById(id));
    }

    @Transactional
    @RequiresPermission(module = Module.PURCHASE, action = Action.CREATE)
    public PurchaseOrderResponse create(UUID tenantId, PurchaseOrderRequest request) {
        garantirPapelAutorizado();
        Partner supplier = findValidSupplier(request.supplierId());
        User buyer = findValidBuyer(request.buyerId());

        PurchaseOrder order = new PurchaseOrder();
        order.setTenantId(tenantId);
        order.setNumber(nextNumber(tenantId));
        apply(order, supplier, buyer, request);
        return toResponse(purchaseOrderRepository.saveAndFlush(order));
    }

    @Transactional
    @RequiresPermission(module = Module.PURCHASE, action = Action.EDIT)
    public PurchaseOrderResponse update(UUID id, PurchaseOrderRequest request) {
        garantirPapelAutorizado();
        Partner supplier = findValidSupplier(request.supplierId());
        User buyer = findValidBuyer(request.buyerId());

        PurchaseOrder order = findEntityById(id);
        if (order.getStatus() != PurchaseOrderStatus.OPEN) {
            throw new PurchaseOrderValidationException(
                    "Não é possível editar uma ordem de compra " + order.getStatus());
        }
        apply(order, supplier, buyer, request);
        return toResponse(purchaseOrderRepository.saveAndFlush(order));
    }

    @Transactional
    @RequiresPermission(module = Module.PURCHASE, action = Action.EDIT)
    public PurchaseOrderResponse updateStatus(UUID id, PurchaseOrderStatus newStatus) {
        PurchaseOrder order = findEntityById(id);
        if (order.getStatus() != PurchaseOrderStatus.OPEN) {
            throw new PurchaseOrderValidationException(
                    "Não é possível alterar o status de uma ordem " + order.getStatus());
        }
        if (newStatus == PurchaseOrderStatus.OPEN) {
            throw new PurchaseOrderValidationException("Status inválido: " + newStatus);
        }
        order.setStatus(newStatus);
        return toResponse(purchaseOrderRepository.saveAndFlush(order));
    }

    @Transactional
    @RequiresPermission(module = Module.PURCHASE, action = Action.DELETE)
    public void delete(UUID id) {
        purchaseOrderRepository.delete(findEntityById(id));
    }

    private PurchaseOrder findEntityById(UUID id) {
        return purchaseOrderRepository.findById(id).orElseThrow(PurchaseOrderNotFoundException::new);
    }

    private Partner findValidSupplier(UUID supplierId) {
        Partner parceiro = parceiroRepository.findById(supplierId)
                .orElseThrow(() -> new PurchaseOrderValidationException("Fornecedor não encontrado"));
        if (!parceiro.getRoles().contains(PartnerRole.SUPPLIER)) {
            throw new PurchaseOrderValidationException("O parceiro selecionado não tem o papel Fornecedor");
        }
        return parceiro;
    }

    private User findValidBuyer(UUID buyerId) {
        User user = userRepository.findById(buyerId)
                .orElseThrow(() -> new PurchaseOrderValidationException("Comprador não encontrado"));
        if (user.getRole() != Role.ADMINISTRATIVE && user.getRole() != Role.ADMIN) {
            throw new PurchaseOrderValidationException("O usuário selecionado não tem o papel Administrador ou Administrativo");
        }
        return user;
    }

    // @RequiresPermission(PURCHASE, CREATE/EDIT) above only checks the grant in
    // user_permission -- it says nothing about the caller's role. A user could in
    // theory hold that grant without being ADMIN/ADMINISTRATIVE (e.g. a role change
    // that didn't clean up permissions), so this is an explicit second gate on top
    // of the annotation, not a replacement for it.
    private void garantirPapelAutorizado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        AuthContextService.Context principal = (AuthContextService.Context) auth.getPrincipal();
        if (!principal.papel().equals(Role.ADMIN.name()) && !principal.papel().equals(Role.ADMINISTRATIVE.name())) {
            throw new PermissionDeniedException();
        }
    }

    // Atomic UPDATE ... RETURNING against the tenant's single
    // purchase_order_counter row -- never COUNT(*)/MAX(number)+1, both of which
    // race under concurrent inserts. Runs inside this method's own
    // @Transactional, so TenantContextAspect has already issued SET LOCAL
    // app.tenant_id before either native query below runs.
    private int nextNumber(UUID tenantId) {
        entityManager.createNativeQuery(
                        "INSERT INTO purchase_order_counter (tenant_id, next_number) VALUES (:tenantId, 1) " +
                                "ON CONFLICT (tenant_id) DO NOTHING")
                .setParameter("tenantId", tenantId)
                .executeUpdate();

        Object result = entityManager.createNativeQuery(
                        "UPDATE purchase_order_counter SET next_number = next_number + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING next_number - 1")
                .setParameter("tenantId", tenantId)
                .getSingleResult();
        return ((Number) result).intValue();
    }

    private void apply(PurchaseOrder order, Partner supplier, User buyer, PurchaseOrderRequest request) {
        order.setSupplier(supplier);
        order.setBuyer(buyer);
        order.setOrderDate(request.orderDate() != null ? request.orderDate() : LocalDate.now());
        order.setExpectedDeliveryDate(request.expectedDeliveryDate());
        BigDecimal discount = request.discount() != null ? request.discount() : BigDecimal.ZERO;

        order.getItems().clear();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (PurchaseOrderItemRequest dto : request.items()) {
            Product product = produtoRepository.findById(dto.productId())
                    .orElseThrow(() -> new PurchaseOrderValidationException("Produto não encontrado"));
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPurchaseOrder(order);
            item.setProduct(product);
            item.setQuantity(dto.quantity());
            item.setUnitPrice(dto.unitPrice());
            BigDecimal itemTotal = dto.quantity().multiply(dto.unitPrice());
            item.setTotalValue(itemTotal);
            order.getItems().add(item);
            subtotal = subtotal.add(itemTotal);
        }
        if (discount.compareTo(subtotal) > 0) {
            throw new PurchaseOrderValidationException("O desconto não pode ser maior que o valor dos produtos");
        }
        order.setDiscount(discount);
        order.setSubtotal(subtotal);
        order.setTotal(subtotal.subtract(discount));
    }

    private PurchaseOrderSummaryResponse toSummary(PurchaseOrder o) {
        return new PurchaseOrderSummaryResponse(o.getId(), o.getNumber(), o.getSupplier().getTradeName(),
                o.getBuyer().getName(), o.getOrderDate(), o.getTotal(), o.getStatus());
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder o) {
        List<PurchaseOrderItemResponse> items = o.getItems().stream()
                .map(i -> new PurchaseOrderItemResponse(i.getProduct().getId(), i.getProduct().getName(),
                        i.getQuantity(), i.getUnitPrice(), i.getTotalValue()))
                .toList();
        return new PurchaseOrderResponse(o.getId(), o.getNumber(), o.getSupplier().getId(), o.getSupplier().getTradeName(),
                o.getBuyer().getId(), o.getBuyer().getName(), o.getOrderDate(), o.getExpectedDeliveryDate(),
                o.getStatus(), o.getDiscount(), o.getSubtotal(), o.getTotal(), items);
    }
}
