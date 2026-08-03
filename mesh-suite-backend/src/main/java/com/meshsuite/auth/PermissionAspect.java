package com.meshsuite.auth;

import com.meshsuite.user.PermissionService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

// Runs after TenantContextAspect (@Order(1)): the permission check below queries
// user_permission, which has RLS via EXISTS against app_user.tenant_id. If this
// ran before SET LOCAL app.tenant_id, the query would find zero rows and deny
// every request unconditionally, regardless of what's actually granted -- see the
// Global Constraints note on @RequiresPermission ordering.
@Aspect
@Component
@Order(2)
public class PermissionAspect {

    private final PermissionService permissionService;

    public PermissionAspect(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Around("@annotation(requiresPermission)")
    public Object checkPermission(ProceedingJoinPoint pjp, RequiresPermission requiresPermission) throws Throwable {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        AuthContextService.Context principal = (AuthContextService.Context) auth.getPrincipal();
        if (!permissionService.hasPermission(principal.usuarioId(), requiresPermission.module(), requiresPermission.action())) {
            throw new PermissionDeniedException();
        }
        return pjp.proceed();
    }
}
