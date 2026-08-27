package com.meshsuite.company.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.company.domain.Company;
import com.meshsuite.company.dto.CompanyCountsResponse;
import com.meshsuite.company.dto.CompanyRequest;
import com.meshsuite.company.dto.CompanyResponse;
import com.meshsuite.company.exception.CompanyIsLastForTenantException;
import com.meshsuite.company.exception.CompanyNotFoundException;
import com.meshsuite.company.exception.DuplicateCnpjException;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.company.repository.specification.CompanySpecifications;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;

    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.USER, action = Action.VIEW)
    public Page<CompanyResponse> list(String search, Boolean active, String state, String city, Pageable pageable) {
        Specification<Company> spec = Specification.allOf(
                CompanySpecifications.withSearch(search),
                CompanySpecifications.withActive(active),
                CompanySpecifications.withState(state),
                CompanySpecifications.withCity(city));
        return companyRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.USER, action = Action.VIEW)
    public CompanyCountsResponse counts() {
        long active = companyRepository.countByActive(true);
        long inactive = companyRepository.countByActive(false);
        return new CompanyCountsResponse(active + inactive, active, inactive);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.USER, action = Action.VIEW)
    public CompanyResponse findById(UUID id) {
        return toResponse(findEntityById(id));
    }

    @Transactional
    @RequiresPermission(module = Module.USER, action = Action.CREATE)
    public CompanyResponse create(UUID tenantId, CompanyRequest request) {
        validateCnpj(request.cnpj(), null);

        Company company = new Company();
        company.setTenantId(tenantId);
        apply(company, request);
        return toResponse(companyRepository.saveAndFlush(company));
    }

    @Transactional
    @RequiresPermission(module = Module.USER, action = Action.EDIT)
    public CompanyResponse update(UUID id, CompanyRequest request) {
        validateCnpj(request.cnpj(), id);

        Company company = findEntityById(id);
        apply(company, request);
        return toResponse(companyRepository.saveAndFlush(company));
    }

    @Transactional
    @RequiresPermission(module = Module.USER, action = Action.EDIT)
    public CompanyResponse updateStatus(UUID id, boolean active) {
        Company company = findEntityById(id);
        company.setActive(active);
        return toResponse(companyRepository.saveAndFlush(company));
    }

    @Transactional
    @RequiresPermission(module = Module.USER, action = Action.DELETE)
    public void delete(UUID id) {
        Company company = findEntityById(id);
        if (companyRepository.countByTenantId(company.getTenantId()) <= 1) {
            throw new CompanyIsLastForTenantException();
        }
        companyRepository.delete(company);
    }

    private Company findEntityById(UUID id) {
        return companyRepository.findById(id).orElseThrow(CompanyNotFoundException::new);
    }

    private void validateCnpj(String cnpj, UUID currentId) {
        boolean duplicate = currentId == null
                ? companyRepository.existsByCnpj(cnpj)
                : companyRepository.existsByCnpjAndIdNot(cnpj, currentId);
        if (duplicate) {
            throw new DuplicateCnpjException();
        }
    }

    private void apply(Company company, CompanyRequest request) {
        company.setLegalName(request.legalName());
        company.setCnpj(request.cnpj());
        company.setTradeName(request.tradeName());
        company.setStateRegistration(request.stateRegistration());
        company.setMunicipalRegistration(request.municipalRegistration());
        company.setPhone(request.phone());
        company.setEmail(request.email());
        company.setWebsite(request.website());
        company.setZipCode(request.zipCode());
        company.setStreet(request.street());
        company.setNumber(request.number());
        company.setComplement(request.complement());
        company.setNeighborhood(request.neighborhood());
        company.setCity(request.city());
        company.setState(request.state());
    }

    private CompanyResponse toResponse(Company company) {
        return new CompanyResponse(
                company.getId(),
                company.getLegalName(),
                company.getCnpj(),
                company.getTradeName(),
                company.getStateRegistration(),
                company.getMunicipalRegistration(),
                company.getPhone(),
                company.getEmail(),
                company.getWebsite(),
                company.getZipCode(),
                company.getStreet(),
                company.getNumber(),
                company.getComplement(),
                company.getNeighborhood(),
                company.getCity(),
                company.getState(),
                company.isActive());
    }
}
