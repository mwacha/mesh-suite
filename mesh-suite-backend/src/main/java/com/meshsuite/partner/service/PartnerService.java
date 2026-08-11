package com.meshsuite.partner.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.PartnerContact;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PartnerStatus;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.dto.*;
import com.meshsuite.partner.exception.DuplicateDocumentException;
import com.meshsuite.partner.exception.PartnerNotFoundException;
import com.meshsuite.partner.exception.PartnerValidationException;
import com.meshsuite.partner.repository.PartnerRepository;
import com.meshsuite.partner.repository.specification.PartnerSpecifications;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartnerService {

    private final PartnerRepository partnerRepository;

    public PartnerService(PartnerRepository partnerRepository) {
        this.partnerRepository = partnerRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.CUSTOMER, action = Action.VIEW)
    public Page<PartnerListItemResponse> list(String search, List<PartnerStatus> status, List<PersonType> personType,
                                               String document, List<String> state, List<String> city,
                                               PartnerRole role, Pageable pageable) {
        Specification<Partner> spec = Specification.allOf(
                PartnerSpecifications.withSearch(search),
                PartnerSpecifications.withStatus(status),
                PartnerSpecifications.withPersonType(personType),
                PartnerSpecifications.withDocument(document),
                PartnerSpecifications.withState(state),
                PartnerSpecifications.withCity(city),
                PartnerSpecifications.withRole(role));
        return partnerRepository.findAll(spec, pageable).map(this::toListItem);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.CUSTOMER, action = Action.VIEW)
    public PartnerSummaryResponse summary(PartnerRole role) {
        long active = countByStatus(PartnerStatus.ACTIVE, role);
        long atRisk = countByStatus(PartnerStatus.AT_RISK, role);
        long blocked = countByStatus(PartnerStatus.BLOCKED, role);
        return new PartnerSummaryResponse(active + atRisk + blocked, active, atRisk, blocked);
    }

    private long countByStatus(PartnerStatus status, PartnerRole role) {
        return role == null
                ? partnerRepository.countByStatus(status)
                : partnerRepository.countByStatusAndRolesContaining(status, role);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.CUSTOMER, action = Action.VIEW)
    public PartnerResponse findById(UUID id) {
        return toResponse(findEntityById(id));
    }

    @Transactional
    @RequiresPermission(module = Module.CUSTOMER, action = Action.CREATE)
    public PartnerResponse create(UUID tenantId, PartnerRequest request) {
        validate(request, null);

        Partner partner = new Partner();
        partner.setTenantId(tenantId);
        apply(partner, request);
        return toResponse(partnerRepository.saveAndFlush(partner));
    }

    @Transactional
    @RequiresPermission(module = Module.CUSTOMER, action = Action.EDIT)
    public PartnerResponse update(UUID id, PartnerRequest request) {
        validate(request, id);

        Partner partner = findEntityById(id);
        apply(partner, request);
        return toResponse(partnerRepository.saveAndFlush(partner));
    }

    @Transactional
    @RequiresPermission(module = Module.CUSTOMER, action = Action.EDIT)
    public PartnerResponse updateStatus(UUID id, PartnerStatus newStatus) {
        if (newStatus != PartnerStatus.ACTIVE && newStatus != PartnerStatus.BLOCKED) {
            throw new PartnerValidationException("Só é possível definir o status como ATIVO ou BLOQUEADO manualmente");
        }
        Partner partner = findEntityById(id);
        partner.setStatus(newStatus);
        return toResponse(partnerRepository.saveAndFlush(partner));
    }

    @Transactional
    @RequiresPermission(module = Module.CUSTOMER, action = Action.DELETE)
    public void delete(UUID id) {
        partnerRepository.delete(findEntityById(id));
    }

    private Partner findEntityById(UUID id) {
        return partnerRepository.findById(id).orElseThrow(PartnerNotFoundException::new);
    }

    private void validate(PartnerRequest request, UUID currentId) {
        boolean noActiveRole = request.roles().stream()
                .noneMatch(r -> r == PartnerRole.CUSTOMER || r == PartnerRole.SUPPLIER);
        if (noActiveRole) {
            throw new PartnerValidationException("Selecione ao menos o papel Cliente ou Fornecedor");
        }

        String document = normalizeDocument(request.document());
        int expectedLength = request.personType() == PersonType.INDIVIDUAL ? 11 : 14;
        if (document.length() != expectedLength) {
            throw new PartnerValidationException(
                    request.personType() == PersonType.INDIVIDUAL
                            ? "CPF deve ter 11 dígitos"
                            : "CNPJ deve ter 14 dígitos");
        }

        boolean duplicate = currentId == null
                ? partnerRepository.existsByDocument(document)
                : partnerRepository.existsByDocumentAndIdNot(document, currentId);
        if (duplicate) {
            throw new DuplicateDocumentException();
        }
    }

    // Aceita CNPJ/CPF digitados ou colados com a máscara usual (pontos, barra,
    // hífen) -- só os dígitos são validados e persistidos.
    private static String normalizeDocument(String document) {
        return document.replaceAll("\\D", "");
    }

    private void apply(Partner partner, PartnerRequest request) {
        partner.setPersonType(request.personType());
        partner.setDocument(normalizeDocument(request.document()));
        partner.setTradeName(request.tradeName());
        partner.setLegalName(request.legalName());
        partner.setRoles(new HashSet<>(request.roles()));
        partner.setBillingEmails(request.billingEmails());
        partner.setWhatsapp(request.whatsapp());
        partner.setTaxIndicator(request.taxIndicator());
        partner.setStateRegistration(request.stateRegistration());
        partner.setMunicipalRegistration(request.municipalRegistration());
        partner.setSuframaRegistration(request.suframaRegistration());
        partner.setZipCode(request.zipCode());
        partner.setStreet(request.street());
        partner.setNumber(request.number());
        partner.setNeighborhood(request.neighborhood());
        partner.setComplement(request.complement());
        partner.setState(request.state());
        partner.setCity(request.city());
        partner.setNotes(request.notes());

        partner.getContacts().clear();
        List<PartnerContactDto> contacts = request.contacts() == null ? List.of() : request.contacts();
        for (PartnerContactDto dto : contacts) {
            PartnerContact contact = new PartnerContact();
            contact.setPartner(partner);
            contact.setName(dto.name());
            contact.setEmail(dto.email());
            contact.setBusinessPhone(dto.businessPhone());
            contact.setMobilePhone(dto.mobilePhone());
            contact.setJobTitle(dto.jobTitle());
            partner.getContacts().add(contact);
        }
    }

    private PartnerListItemResponse toListItem(Partner p) {
        return new PartnerListItemResponse(
                p.getId(), p.getTradeName(), p.getLegalName(), p.getDocument(), p.getPersonType(),
                p.getCity(), p.getState(), p.getWhatsapp(), p.getStatus());
    }

    private PartnerResponse toResponse(Partner p) {
        List<PartnerContactDto> contacts = p.getContacts().stream()
                .map(c -> new PartnerContactDto(c.getName(), c.getEmail(), c.getBusinessPhone(),
                        c.getMobilePhone(), c.getJobTitle()))
                .toList();
        return new PartnerResponse(
                p.getId(), p.getPersonType(), p.getDocument(), p.getTradeName(), p.getLegalName(),
                p.getStatus(), p.getRoles(), p.getBillingEmails(), p.getWhatsapp(), p.getTaxIndicator(),
                p.getStateRegistration(), p.getMunicipalRegistration(), p.getSuframaRegistration(), p.getZipCode(),
                p.getStreet(), p.getNumber(), p.getNeighborhood(), p.getComplement(), p.getState(), p.getCity(),
                p.getNotes(), contacts);
    }
}
