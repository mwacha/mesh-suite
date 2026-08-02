package com.meshsuite.auth;

import com.meshsuite.empresa.Empresa;
import com.meshsuite.empresa.EmpresaRepository;
import com.meshsuite.user.Role;
import com.meshsuite.user.User;
import com.meshsuite.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TenantQueryService {

    private final EmpresaRepository empresaRepository;
    private final UserRepository userRepository;

    public TenantQueryService(EmpresaRepository empresaRepository, UserRepository userRepository) {
        this.empresaRepository = empresaRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void saveEmpresa(UUID tenantId, String razaoSocial, String cnpj) {
        Empresa empresa = new Empresa();
        empresa.setTenantId(tenantId);
        empresa.setRazaoSocial(razaoSocial);
        empresa.setCnpj(cnpj);
        empresaRepository.saveAndFlush(empresa);
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
    public List<Empresa> listEmpresas() {
        return empresaRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<User> listUsers() {
        return userRepository.findAll();
    }
}
