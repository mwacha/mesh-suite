package com.meshsuite.paymentmethod.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.paymentmethod.domain.PaymentMethod;
import com.meshsuite.paymentmethod.domain.PaymentMethodInstallment;
import com.meshsuite.paymentmethod.domain.enums.PaymentMethodType;
import com.meshsuite.paymentmethod.dto.PaymentMethodCountsResponse;
import com.meshsuite.paymentmethod.dto.PaymentMethodInstallmentInput;
import com.meshsuite.paymentmethod.dto.PaymentMethodInstallmentResponse;
import com.meshsuite.paymentmethod.dto.PaymentMethodRequest;
import com.meshsuite.paymentmethod.dto.PaymentMethodResponse;
import com.meshsuite.paymentmethod.dto.PaymentMethodSummaryResponse;
import com.meshsuite.paymentmethod.exception.DuplicatePaymentMethodDescriptionException;
import com.meshsuite.paymentmethod.exception.PaymentMethodNotFoundException;
import com.meshsuite.paymentmethod.exception.PaymentMethodValidationException;
import com.meshsuite.paymentmethod.repository.PaymentMethodRepository;
import com.meshsuite.paymentmethod.repository.specification.PaymentMethodSpecifications;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentMethodService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    private final PaymentMethodRepository paymentMethodRepository;

    public PaymentMethodService(PaymentMethodRepository paymentMethodRepository) {
        this.paymentMethodRepository = paymentMethodRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PAYABLE, action = Action.VIEW)
    public Page<PaymentMethodSummaryResponse> list(String search, PaymentMethodType type, Boolean active,
            Pageable pageable) {
        Specification<PaymentMethod> spec = Specification.allOf(
                PaymentMethodSpecifications.withSearch(search),
                PaymentMethodSpecifications.withType(type),
                PaymentMethodSpecifications.withActive(active));
        return paymentMethodRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PAYABLE, action = Action.VIEW)
    public PaymentMethodCountsResponse counts() {
        long active = paymentMethodRepository.countByActive(true);
        long inactive = paymentMethodRepository.countByActive(false);
        return new PaymentMethodCountsResponse(active + inactive, active, inactive);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PAYABLE, action = Action.VIEW)
    public PaymentMethodResponse findById(UUID id) {
        return toResponse(findEntityById(id));
    }

    @Transactional
    @RequiresPermission(module = Module.PAYABLE, action = Action.CREATE)
    public PaymentMethodResponse create(UUID tenantId, PaymentMethodRequest request) {
        validate(request, null);

        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setTenantId(tenantId);
        apply(paymentMethod, request);
        return toResponse(paymentMethodRepository.saveAndFlush(paymentMethod));
    }

    @Transactional
    @RequiresPermission(module = Module.PAYABLE, action = Action.EDIT)
    public PaymentMethodResponse update(UUID id, PaymentMethodRequest request) {
        validate(request, id);

        PaymentMethod paymentMethod = findEntityById(id);
        apply(paymentMethod, request);
        return toResponse(paymentMethodRepository.saveAndFlush(paymentMethod));
    }

    @Transactional
    @RequiresPermission(module = Module.PAYABLE, action = Action.DELETE)
    public void delete(UUID id) {
        paymentMethodRepository.delete(findEntityById(id));
    }

    private PaymentMethod findEntityById(UUID id) {
        return paymentMethodRepository.findById(id).orElseThrow(PaymentMethodNotFoundException::new);
    }

    private void validate(PaymentMethodRequest request, UUID currentId) {
        List<PaymentMethodInstallmentInput> installments = request.installments();
        // installments == null significa "não mexa no parcelamento" (a tela de
        // cadastro não o edita). Só uma lista informada é validada.
        if (installments != null && !installments.isEmpty()) {
            BigDecimal total = installments.stream()
                    .map(PaymentMethodInstallmentInput::percentage)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(ONE_HUNDRED) != 0) {
                throw new PaymentMethodValidationException("A soma dos percentuais das parcelas deve ser igual a 100%");
            }
            if (installments.size() > request.maxInstallments()) {
                throw new PaymentMethodValidationException(
                        "O parcelamento tem mais parcelas do que o máximo informado");
            }
        }

        boolean duplicate = currentId == null
                ? paymentMethodRepository.existsByDescription(request.description())
                : paymentMethodRepository.existsByDescriptionAndIdNot(request.description(), currentId);
        if (duplicate) {
            throw new DuplicatePaymentMethodDescriptionException();
        }
    }

    private void apply(PaymentMethod paymentMethod, PaymentMethodRequest request) {
        paymentMethod.setDescription(request.description());
        paymentMethod.setType(request.type());
        paymentMethod.setNotes(request.notes());
        paymentMethod.setActive(request.active() != null ? request.active() : true);
        paymentMethod.setMaxInstallments(request.maxInstallments());
        paymentMethod.setInterestRate(request.interestRate());
        paymentMethod.setSettlementDays(request.settlementDays());

        if (request.installments() == null) {
            return;
        }

        paymentMethod.getInstallments().clear();
        int number = 1;
        for (PaymentMethodInstallmentInput input : request.installments()) {
            PaymentMethodInstallment installment = new PaymentMethodInstallment();
            installment.setPaymentMethod(paymentMethod);
            installment.setInstallmentNumber(number++);
            installment.setDaysDue(input.daysDue());
            installment.setPercentage(input.percentage());
            paymentMethod.getInstallments().add(installment);
        }
    }

    private PaymentMethodSummaryResponse toSummary(PaymentMethod p) {
        List<Integer> installmentDays = p.getInstallments().stream()
                .map(PaymentMethodInstallment::getDaysDue)
                .toList();
        return new PaymentMethodSummaryResponse(
                p.getId(),
                p.getDescription(),
                p.getType(),
                p.getActive(),
                p.getMaxInstallments(),
                installmentDays.size(),
                installmentDays);
    }

    private PaymentMethodResponse toResponse(PaymentMethod p) {
        List<PaymentMethodInstallmentResponse> installments = p.getInstallments().stream()
                .map(i -> new PaymentMethodInstallmentResponse(i.getInstallmentNumber(), i.getDaysDue(), i.getPercentage()))
                .toList();
        return new PaymentMethodResponse(
                p.getId(),
                p.getDescription(),
                p.getType(),
                p.getNotes(),
                p.getActive(),
                p.getMaxInstallments(),
                p.getInterestRate(),
                p.getSettlementDays(),
                p.getCreatedAt(),
                installments);
    }
}
