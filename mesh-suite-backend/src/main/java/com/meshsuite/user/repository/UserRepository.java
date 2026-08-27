package com.meshsuite.user.repository;

import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.enums.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
    List<User> findAllByEmail(String email);
    List<User> findByRoleOrderByName(Role role);
    long countByActive(boolean active);
    long countByPermissionProfileId(UUID permissionProfileId);

    @Query("SELECT COUNT(u) > 0 FROM User u JOIN u.permissions p " +
            "WHERE u.id = :userId AND p.module = :module AND p.action = :action")
    boolean hasPermission(@Param("userId") UUID userId, @Param("module") Module module, @Param("action") Action action);

    @Query("SELECT DISTINCT u FROM User u JOIN u.permissions p " +
            "WHERE u.role IN :roles AND p.module = :module AND p.action = :action " +
            "ORDER BY u.name")
    List<User> findByRoleInAndPermission(@Param("roles") List<Role> roles,
                                          @Param("module") Module module,
                                          @Param("action") Action action);
}
