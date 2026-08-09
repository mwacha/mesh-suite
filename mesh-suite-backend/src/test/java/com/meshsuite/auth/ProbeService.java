package com.meshsuite.auth;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.aspect.PermissionAspect;
import com.meshsuite.auth.aspect.TenantContextAspect;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Minimal throwaway service, annotated exactly like a real domain service will be
// from Task 9 onward -- exists only so PermissionAspectTest can exercise
// PermissionAspect's real AOP wiring (ordering with TenantContextAspect, RLS-scoped
// permission lookup) before any production service actually uses
// @RequiresPermission yet.
//
// Deliberately a TOP-LEVEL class rather than a static nested class inside
// PermissionAspectTest: Spring Boot's TestTypeExcludeFilter is registered as a
// ContextCustomizerFactory for every @SpringBootTest (not just test slices like
// @WebMvcTest), and it excludes a candidate class if ITS ENCLOSING CLASS qualifies
// as a "test class" (has @Test-annotated methods, since JUnit Jupiter's @Test is
// meta-annotated @Testable). A nested `static class ProbeService` inside
// PermissionAspectTest would therefore be silently dropped from component scanning
// -- confirmed via NoSuchBeanDefinitionException when first written that way. As a
// top-level class with no enclosing test class, it scans normally.
@Service
public class ProbeService {

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.CUSTOMER, action = Action.VIEW)
    public String probe() {
        return "ok";
    }
}
