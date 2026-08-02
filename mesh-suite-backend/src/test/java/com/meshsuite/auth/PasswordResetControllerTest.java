package com.meshsuite.auth;

import com.meshsuite.AbstractIntegrationTest;
import com.meshsuite.tenant.Tenant;
import com.meshsuite.tenant.TenantRepository;
import com.meshsuite.user.Role;
import com.meshsuite.user.User;
import com.meshsuite.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// See AuthControllerTest for why this is @Transactional: it lets the fixture insert in
// the second test set app.tenant_id (RLS gates INSERT) and RESET it before the actual
// request, all sharing one connection with the in-process MockMvc call.
@Transactional
class PasswordResetControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;
    @MockBean com.meshsuite.mail.MailService mailService;

    @Test
    void forgotPasswordReturnsOkRegardlessOfWhetherEmailExists() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ninguem@aurora.com.br"}"""))
                .andExpect(status().isOk());
    }

    @Test
    void forgotPasswordSendsEmailWhenUsuarioExistsAndActive() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setCodigo("aurora");
        tenant.setNome("Aurora");
        tenantRepository.saveAndFlush(tenant);

        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenant.getId() + "'").executeUpdate();

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName("Marina");
        user.setEmail("marina@aurora.com.br");
        user.setPasswordHash(passwordEncoder.encode("senha123"));
        user.setRole(Role.ADMIN);
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"marina@aurora.com.br"}"""))
                .andExpect(status().isOk());

        verify(mailService).sendPasswordResetEmail(org.mockito.ArgumentMatchers.eq("marina@aurora.com.br"), any());
    }
}
