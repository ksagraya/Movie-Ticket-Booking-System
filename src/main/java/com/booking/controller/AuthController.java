package com.booking.controller;

import com.booking.dto.Requests;
import com.booking.dto.Responses;
import com.booking.entity.Role;
import com.booking.entity.User;
import com.booking.exception.ApiException;
import com.booking.repository.UserRepository;
import com.booking.service.AuthHelper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthHelper authHelper;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthHelper authHelper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authHelper = authHelper;
    }

    @PostMapping("/register")
    public ResponseEntity<Responses.UserResponse> register(@Valid @RequestBody Requests.RegisterRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new ApiException("Username already taken", 409);
        }
        User u = User.builder()
                .username(req.username())
                .password(passwordEncoder.encode(req.password()))
                .email(req.email())
                .role(req.role() == null ? Role.CUSTOMER : req.role())
                .build();
        u = userRepository.save(u);
        return ResponseEntity.status(201).body(Responses.UserResponse.from(u));
    }

    @GetMapping("/me")
    public Responses.UserResponse me() {
        return Responses.UserResponse.from(authHelper.currentUser());
    }
}
