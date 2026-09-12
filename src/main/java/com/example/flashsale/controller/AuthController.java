package com.example.flashsale.controller;

import com.example.flashsale.model.User;
import com.example.flashsale.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AuthController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public record Registration(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{3,64}") String username,
            @NotBlank @Size(min = 12, max = 72) String password,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String fullName) {}

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public String register(@Valid @RequestBody Registration request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password exceeds 72 UTF-8 bytes");
        }
        if (users.findByUsername(request.username()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username unavailable");
        }
        users.saveAndFlush(new User(request.username(), encoder.encode(request.password()),
                request.email(), request.fullName(), "USER"));
        return "User registered";
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) { return token; }

    @GetMapping("/me")
    public Map<String, String> me(Authentication auth) {
        return auth == null ? Map.of("authenticated", "false")
                : Map.of("authenticated", "true", "username", auth.getName());
    }
}
