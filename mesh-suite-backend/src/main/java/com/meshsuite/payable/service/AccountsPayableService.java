package com.meshsuite.payable.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.repository.PartnerRepository;
import com.meshsuite.payable.domain.AccountsPayable;
import com.meshsuite.payable.domain.enums.AccountsPayableStatus;
import com.meshsuite.payable.dto.*;
import com.meshsuite.payable.exception.AccountsPayableNotFoundException;
import com.meshsuite.payable.exception.AccountsPayableValidationException;
import com.meshsuite.payable.repository.AccountsPayableRepository;
import com.meshsuite.payable.repository.specification.AccountsPayableSpecifications;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountsPayableService {

    private final AccountsPayableRepository accountsPayableRepository;
    private final PartnerRepository parceiroRepository;
    private final EntityManager entityManager;

    public AccountsPayableService(AccountsPayableRepository accountsPayableRepository,
                                   PartnerRepository parceiroRepository, EntityManager entityManager) {
        this.accountsPayableRepository = accountsPayableRepository;
        this.parceiroRepository = parceiroRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public List<AccountsPayableResponse> createInstallments(UUID tenantId, UUID supplierId, UUID referenceId,
                                                              List<AccountsPayableInstallmentInput> installments) {
        Partner supplier = findValidSupplier(supplierId);
        int total = installments.size();
        List<AccountsPayableResponse> result = new ArrayList<>();
        int installmentNumber = 1;
        for (AccountsPayableInstallmentInput input : installments) {
            AccountsPayable entry = new AccountsPayable();
            entry.setTenantId(tenantId);
            entry.setNumber(nextNumber(tenantId));
            entry.setInstallmentNumber(installmentNumber++);
            entry.setTotalInstallments(total);
            entry.setSupplier(supplier);
            entry.setAmount(input.amount());
            entry.setIssueDate(LocalDate.now());
            entry.setDueDate(input.dueDate());
            entry.setReferenceId(referenceId);
            result.add(toResponse(accountsPayableRepository.saveAndFlush(entry)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PAYABLE, action = Action.VIEW)
    public Page<AccountsPayableResponse> list(AccountsPayableStatus status, Pageable pageable) {
        Specification<AccountsPayable> spec = AccountsPayableSpecifications.withStatus(status);
        return accountsPayableRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional
    @RequiresPermission(module = Module.PAYABLE, action = Action.EDIT)
    public AccountsPayableResponse updateStatus(UUID id, AccountsPayableStatus newStatus) {
        AccountsPayable entry = findEntityById(id);
        if (newStatus == AccountsPayableStatus.PAID) {
            if (entry.getStatus() != AccountsPayableStatus.OPEN) {
                throw new AccountsPayableValidationException("Só é possível dar baixa em um título em aberto");
            }
            entry.setStatus(AccountsPayableStatus.PAID);
            entry.setPaymentDate(LocalDate.now());
        } else {
            if (entry.getStatus() != AccountsPayableStatus.PAID) {
                throw new AccountsPayableValidationException("Só é possível reverter a baixa de um título pago");
            }
            entry.setStatus(AccountsPayableStatus.OPEN);
            entry.setPaymentDate(null);
        }
        return toResponse(accountsPayableRepository.saveAndFlush(entry));
    }

    private AccountsPayable findEntityById(UUID id) {
        return accountsPayableRepository.findById(id).orElseThrow(AccountsPayableNotFoundException::new);
    }

    private Partner findValidSupplier(UUID supplierId) {
        Partner parceiro = parceiroRepository.findById(supplierId)
                .orElseThrow(() -> new AccountsPayableValidationException("Fornecedor não encontrado"));
        if (!parceiro.getRoles().contains(PartnerRole.SUPPLIER)) {
            throw new AccountsPayableValidationException("O parceiro selecionado não tem o papel Fornecedor");
        }
        return parceiro;
    }

    // Atomic UPDATE ... RETURNING against the tenant's single
    // accounts_payable_counter row -- never COUNT(*)/MAX(number)+1. Same
    // pattern as PurchaseOrderService.nextNumber/PedidoService.proximoNumero.
    private int nextNumber(UUID tenantId) {
        entityManager.createNativeQuery(
                        "INSERT INTO accounts_payable_counter (tenant_id, next_number) VALUES (:tenantId, 1) " +
                                "ON CONFLICT (tenant_id) DO NOTHING")
                .setParameter("tenantId", tenantId)
                .executeUpdate();

        Object result = entityManager.createNativeQuery(
                        "UPDATE accounts_payable_counter SET next_number = next_number + 1 " +
                                "WHERE tenant_id = :tenantId RETURNING next_number - 1")
                .setParameter("tenantId", tenantId)
                .getSingleResult();
        return ((Number) result).intValue();
    }

    private AccountsPayableResponse toResponse(AccountsPayable e) {
        return new AccountsPayableResponse(e.getId(), e.getNumber(), e.getInstallmentNumber(), e.getTotalInstallments(),
                e.getSupplier().getId(), e.getSupplier().getTradeName(), e.getAmount(), e.getIssueDate(),
                e.getDueDate(), e.getPaymentDate(), e.getStatus(), e.getReferenceId(), e.getCreatedAt());
    }
}
