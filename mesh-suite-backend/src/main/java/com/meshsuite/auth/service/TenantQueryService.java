package com.meshsuite.auth.service;

import com.meshsuite.company.domain.Company;
import com.meshsuite.company.repository.CompanyRepository;
import com.meshsuite.user.domain.enums.Role;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TenantQueryService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    public TenantQueryService(CompanyRepository companyRepository, UserRepository userRepository) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void saveCompany(UUID tenantId, String legalName, String cnpj) {
        Company company = new Company();
        company.setTenantId(tenantId);
        company.setLegalName(legalName);
        company.setCnpj(cnpj);
        companyRepository.saveAndFlush(company);
    }

    @Transactional
    public void saveUser(UUID tenantId, String name, String email, Role role) {
        User user = new User();
        user.setTenantId(tenantId);
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setRole(role);
        userRepository.saveAndFlush(user);
    }

    @Transactional(readOnly = true)
    public List<Company> listCompanies() {
        return companyRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<User> listUsers() {
        return userRepository.findAll();
    }
}
