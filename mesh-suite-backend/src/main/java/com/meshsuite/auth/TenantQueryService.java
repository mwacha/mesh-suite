package com.meshsuite.auth;

import com.meshsuite.empresa.Empresa;
import com.meshsuite.empresa.EmpresaRepository;
import com.meshsuite.usuario.Papel;
import com.meshsuite.usuario.Usuario;
import com.meshsuite.usuario.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TenantQueryService {

    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;

    public TenantQueryService(EmpresaRepository empresaRepository, UsuarioRepository usuarioRepository) {
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
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
    public void saveUsuario(UUID tenantId, String nome, String email, Papel papel) {
        Usuario usuario = new Usuario();
        usuario.setTenantId(tenantId);
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setSenhaHash("hash");
        usuario.setPapel(papel);
        usuarioRepository.saveAndFlush(usuario);
    }

    @Transactional(readOnly = true)
    public List<Empresa> listEmpresas() {
        return empresaRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Usuario> listUsuarios() {
        return usuarioRepository.findAll();
    }
}
