package com.meshsuite.salesorder.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.repository.PartnerRepository;
import com.meshsuite.salesorder.domain.SalesOrderItem;
import com.meshsuite.salesorder.domain.SalesOrder;
import com.meshsuite.salesorder.domain.enums.PeriodRange;
import com.meshsuite.salesorder.domain.enums.SalesOrderStatus;
import com.meshsuite.salesorder.dto.*;
import com.meshsuite.salesorder.exception.SalesOrderNotFoundException;
import com.meshsuite.salesorder.exception.SalesOrderValidationException;
import com.meshsuite.salesorder.repository.SalesOrderRepository;
import com.meshsuite.salesorder.repository.specification.SalesOrderSpecifications;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.repository.ProductRepository;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesOrderService {

    private final SalesOrderRepository salesOrderRepository;
    private final PartnerRepository partnerRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final EntityManager entityManager;

    public SalesOrderService(SalesOrderRepository salesOrderRepository, PartnerRepository partnerRepository,
                              UserRepository userRepository, ProductRepository productRepository,
                              EntityManager entityManager) {
        this.salesOrderRepository = salesOrderRepository;
        this.partnerRepository = partnerRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.ORDER, action = Action.VIEW)
    public Page<SalesOrderSummaryResponse> list(String search, SalesOrderStatus status, UUID salespersonId, Pageable pageable) {
        Specification<SalesOrder> spec = Specification.allOf(
                SalesOrderSpecifications.withSearch(search),
                SalesOrderSpecifications.withStatus(status),
                SalesOrderSpecifications.withSalesperson(salespersonId));
        return salesOrderRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.ORDER, action = Action.VIEW)
    public SalesOrderCountsResponse counts() {
        long draft = salesOrderRepository.countByStatus(SalesOrderStatus.DRAFT);
        long inPreparation = salesOrderRepository.countByStatus(SalesOrderStatus.IN_PREPARATION);
        long invoiced = salesOrderRepository.countByStatus(SalesOrderStatus.INVOICED);
        return new SalesOrderCountsResponse(draft + inPreparation + invoiced, draft, inPreparation, invoiced);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.ORDER, action = Action.VIEW)
    public MonthlyRevenueResponse monthlyRevenue() {
        LocalDate today = LocalDate.now();
        BigDecimal total = salesOrderRepository.sumTotalByStatusAndOrderDateBetween(
                SalesOrderStatus.INVOICED, today.withDayOfMonth(1), today);
        return new MonthlyRevenueResponse(total);
    }

    private static final String[] MONTH_LABELS = {
        "jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez",
    };

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.ORDER, action = Action.VIEW)
    public List<OrderPeriodPointResponse> ordersByPeriod(PeriodRange range) {
        LocalDate today = LocalDate.now();
        if (range == PeriodRange.CURRENT_MONTH) {
            LocalDate start = today.withDayOfMonth(1);
            Map<Integer, Long> byDay = salesOrderRepository.findOrderDatesBetween(start, today).stream()
                    .collect(Collectors.groupingBy(LocalDate::getDayOfMonth, Collectors.counting()));
            return IntStream.rangeClosed(1, today.getDayOfMonth())
                    .mapToObj(day -> new OrderPeriodPointResponse(String.valueOf(day), byDay.getOrDefault(day, 0L)))
                    .toList();
        }

        LocalDate start = today.minusMonths(11).withDayOfMonth(1);
        Map<YearMonth, Long> byMonth = salesOrderRepository.findOrderDatesBetween(start, today).stream()
                .collect(Collectors.groupingBy(YearMonth::from, Collectors.counting()));
        List<OrderPeriodPointResponse> points = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth ym = YearMonth.from(today).minusMonths(i);
            String label = MONTH_LABELS[ym.getMonthValue() - 1] + "/" + String.format("%02d", ym.getYear() % 100);
            points.add(new OrderPeriodPointResponse(label, byMonth.getOrDefault(ym, 0L)));
        }
        return points;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.ORDER, action = Action.VIEW)
    public SalesOrderResponse findById(UUID id) {
        return toResponse(findEntityById(id));
    }

    @Transactional
    @RequiresPermission(module = Module.ORDER, action = Action.CREATE)
    public SalesOrderResponse create(UUID tenantId, SalesOrderRequest request) {
        Partner customer = findValidCustomer(request.customerId());
        User salesperson = findValidSalesperson(request.salespersonId());

        SalesOrder salesOrder = new SalesOrder();
        salesOrder.setTenantId(tenantId);
        salesOrder.setNumber(nextNumber(tenantId));
        apply(salesOrder, customer, salesperson, request);
        return toResponse(salesOrderRepository.saveAndFlush(salesOrder));
    }

    @Transactional
    @RequiresPermission(module = Module.ORDER, action = Action.EDIT)
    public SalesOrderResponse update(UUID id, SalesOrderRequest request) {
        Partner customer = findValidCustomer(request.customerId());
        User salesperson = findValidSalesperson(request.salespersonId());

        SalesOrder salesOrder = findEntityById(id);
        apply(salesOrder, customer, salesperson, request);
        return toResponse(salesOrderRepository.saveAndFlush(salesOrder));
    }

    @Transactional
    @RequiresPermission(module = Module.ORDER, action = Action.EDIT)
    public SalesOrderResponse advanceStatus(UUID id, SalesOrderStatus newStatus) {
        if (newStatus == SalesOrderStatus.INVOICED) {
            throw new SalesOrderValidationException(
                    "Faturamento deve ser feito através do fluxo de Venda (POST /api/sales/issue/{orderId})");
        }
        SalesOrder salesOrder = findEntityById(id);
        int current = salesOrder.getStatus().ordinal();
        int target = newStatus.ordinal();
        if (target != current + 1) {
            throw new SalesOrderValidationException(
                    "Não é possível avançar de " + salesOrder.getStatus() + " para " + newStatus);
        }
        salesOrder.setStatus(newStatus);
        return toResponse(salesOrderRepository.saveAndFlush(salesOrder));
    }

    @Transactional
    @RequiresPermission(module = Module.ORDER, action = Action.DELETE)
    public void delete(UUID id) {
        salesOrderRepository.delete(findEntityById(id));
    }

    private SalesOrder findEntityById(UUID id) {
        return salesOrderRepository.findById(id).orElseThrow(SalesOrderNotFoundException::new);
    }

    private Partner findValidCustomer(UUID customerId) {
        Partner partner = partnerRepository.findById(customerId)
                .orElseThrow(() -> new SalesOrderValidationException("Cliente não encontrado"));
        if (!partner.getRoles().contains(PartnerRole.CUSTOMER)) {
            throw new SalesOrderValidationException("O parceiro selecionado não tem o papel Cliente");
        }
        return partner;
    }

    private User findValidSalesperson(UUID salespersonId) {
        User user = userRepository.findById(salespersonId)
                .orElseThrow(() -> new SalesOrderValidationException("Vendedor não encontrado"));
        if (user.getRole() != Role.SALES_REP) {
            throw new SalesOrderValidationException("O usuário selecionado não tem o papel Representante");
        }
        return user;
    }

    // Atomic UPDATE ... RETURNING against the tenant's single sales_order_counter row --
    // never COUNT(*)/MAX(number)+1, both of which race under concurrent inserts.
    // Runs inside this method's own @Transactional, so TenantContextAspect has
    // already issued SET LOCAL app.tenant_id before either native query below runs.
    private int nextNumber(UUID tenantId) {
        entityManager.createNativeQuery(
                        "INSERT INTO sales_order_counter (tenant_id, next_number) VALUES (:tenantId, 1) " +
                                "ON CONFLICT (tenant_id) DO NOTHING")
                .setParameter("tenantId", tenantId)
                .executeUpdate();

        Object result = entityManager.createNativeQuery(
                        "UPDATE sales_order_counter SET next_number = next_number + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING next_number - 1")
                .setParameter("tenantId", tenantId)
                .getSingleResult();
        return ((Number) result).intValue();
    }

    private void apply(SalesOrder salesOrder, Partner customer, User salesperson, SalesOrderRequest request) {
        salesOrder.setCustomer(customer);
        salesOrder.setSalesperson(salesperson);
        salesOrder.setOrderDate(request.orderDate() != null ? request.orderDate() : LocalDate.now());
        salesOrder.setDeliveryDate(request.deliveryDate());
        salesOrder.setDiscount(request.discount() != null ? request.discount() : BigDecimal.ZERO);

        salesOrder.getItems().clear();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (SalesOrderItemRequest itemRequest : request.items()) {
            Product product = productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> new SalesOrderValidationException("Produto não encontrado"));
            SalesOrderItem item = new SalesOrderItem();
            item.setSalesOrder(salesOrder);
            item.setProduct(product);
            item.setQuantity(itemRequest.quantity());
            item.setUnitPrice(itemRequest.unitPrice());
            BigDecimal totalAmountItem = itemRequest.quantity().multiply(itemRequest.unitPrice());
            item.setTotalAmount(totalAmountItem);
            salesOrder.getItems().add(item);
            subtotal = subtotal.add(totalAmountItem);
        }
        salesOrder.setSubtotal(subtotal);
        salesOrder.setTotal(subtotal.subtract(salesOrder.getDiscount()));
    }

    private SalesOrderSummaryResponse toSummary(SalesOrder s) {
        return new SalesOrderSummaryResponse(s.getId(), s.getNumber(), s.getCustomer().getTradeName(),
                s.getSalesperson().getName(), s.getOrderDate(), s.getTotal(), s.getStatus());
    }

    private SalesOrderResponse toResponse(SalesOrder s) {
        List<SalesOrderItemResponse> items = s.getItems().stream()
                .map(i -> new SalesOrderItemResponse(i.getProduct().getId(), i.getProduct().getName(),
                        i.getQuantity(), i.getUnitPrice(), i.getTotalAmount()))
                .toList();
        return new SalesOrderResponse(s.getId(), s.getNumber(), s.getCustomer().getId(), s.getCustomer().getTradeName(),
                s.getSalesperson().getId(), s.getSalesperson().getName(), s.getOrderDate(), s.getDeliveryDate(),
                s.getStatus(), s.getDiscount(), s.getSubtotal(), s.getTotal(), items);
    }
}
