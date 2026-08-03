package com.meshsuite.user;

import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PermissionService {

    private final UserRepository userRepository;

    public PermissionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean hasPermission(UUID userId, Module module, Action action) {
        return userRepository.hasPermission(userId, module, action);
    }
}
