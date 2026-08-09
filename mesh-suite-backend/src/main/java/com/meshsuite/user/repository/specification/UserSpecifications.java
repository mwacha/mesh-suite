package com.meshsuite.user.repository.specification;

import com.meshsuite.user.domain.User;
import com.meshsuite.user.domain.enums.Profile;
import org.springframework.data.jpa.domain.Specification;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> withSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), term),
                cb.like(cb.lower(root.get("email")), term));
    }

    public static Specification<User> withProfile(Profile profile) {
        if (profile == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("profile"), profile);
    }

    public static Specification<User> withActive(Boolean active) {
        if (active == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }
}
