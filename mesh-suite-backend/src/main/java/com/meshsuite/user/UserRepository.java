package com.meshsuite.user;

import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
    List<User> findByRoleOrderByName(Role role);
    long countByActive(boolean active);

    @Query("SELECT COUNT(u) > 0 FROM User u JOIN u.permissions p " +
            "WHERE u.id = :userId AND p.module = :module AND p.action = :action")
    boolean hasPermission(@Param("userId") UUID userId, @Param("module") Module module, @Param("action") Action action);
}
