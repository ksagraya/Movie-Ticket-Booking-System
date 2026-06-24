package com.booking.service;

import com.booking.entity.*;
import com.booking.exception.ApiException;
import com.booking.repository.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthHelper {

    private final UserRepository userRepository;

    public AuthHelper(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ApiException("Unauthorized", 401);
        }
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException("User not found", 401));
    }
}
