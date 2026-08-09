package com.meshsuite.user.domain;

import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;

@Embeddable
public class UserPermissionGrant {

    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false, length = 20)
    private Module module;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 10)
    private Action action;

    public UserPermissionGrant() {
    }

    public UserPermissionGrant(Module module, Action action) {
        this.module = module;
        this.action = action;
    }

    public Module getModule() {
        return module;
    }

    public void setModule(Module module) {
        this.module = module;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    // equals/hashCode required: this is the element type of a Set<UserPermissionGrant>
    // (User.permissions) -- without them, Set membership/dedup falls back to identity,
    // and Hibernate's dirty-checking for @ElementCollection compares elements by value.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserPermissionGrant that)) return false;
        return module == that.module && action == that.action;
    }

    @Override
    public int hashCode() {
        return Objects.hash(module, action);
    }
}
