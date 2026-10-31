package com.marudham.web.controller;

import com.marudham.web.model.AppUser;
import com.marudham.web.repository.AppUserRepository;
import com.marudham.web.service.PasswordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final int MAX_FAILS = 5;
    private static final long LOCK_MS = 5 * 60 * 1000L;

    private final AppUserRepository users;
    private final PasswordService passwords;

    @Value("${app.user.port:8081}")
    private int userPort;

    private final Map<String, long[]> fails = new ConcurrentHashMap<>();

    public AuthController(AppUserRepository u, PasswordService p) {
        this.users = u;
        this.passwords = p;
    }

    private boolean isUserPort(HttpServletRequest r) {
        return r.getLocalPort() == userPort;
    }

    @GetMapping("/info")
    public Map<String, Object> info(HttpServletRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("site", isUserPort(r) ? "user" : "admin");
        m.put("registration", true);
        m.put("needsCode", false);
        return m;
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest r) {
        HttpSession s = r.getSession(false);
        if (s == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in.");
        Object usernameObj = s.getAttribute("auth.username");
        if (usernameObj == null) {
            // Check legacy keys
            usernameObj = s.getAttribute("auth.admin");
            if (usernameObj == null) usernameObj = s.getAttribute("auth.user");
            if (usernameObj == null) usernameObj = s.getAttribute("auth.driver");
        }
        if (usernameObj == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in.");
        Optional<AppUser> u = users.findByUsername(String.valueOf(usernameObj));
        if (u.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in.");
        return view(u.get());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> b, HttpServletRequest r) {
        String name = norm(b.get("username")), pw = b.getOrDefault("password", "");
        long[] f = fails.get(name);
        if (f != null && f[1] > System.currentTimeMillis())
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. Try again in a few minutes.");
        Optional<AppUser> u = users.findByUsername(name);
        if (u.isEmpty() || !passwords.matches(pw, u.get().getPasswordHash())) {
            long[] x = fails.computeIfAbsent(name, k -> new long[2]);
            if (++x[0] >= MAX_FAILS) { x[0] = 0; x[1] = System.currentTimeMillis() + LOCK_MS; }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong username or password.");
        }

        AppUser user = u.get();
        String role = user.getRole();

        // If accessed strictly via userPort (8081), only citizens can log in
        if (isUserPort(r) && !role.equals("USER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This is the citizen portal. Please sign in via the admin/main console on port 8080.");
        }

        fails.remove(name);
        HttpSession s = r.getSession(true);
        s.setAttribute("auth.username", user.getUsername());
        s.setAttribute("auth.role", role);
        if ("ADMIN".equals(role)) {
            s.setAttribute("auth.admin", user.getUsername());
        } else if ("DRIVER".equals(role)) {
            s.setAttribute("auth.driver", user.getUsername());
        } else {
            s.setAttribute("auth.user", user.getUsername());
        }

        Map<String, Object> out = view(user);
        String redirect = "/admin.html";
        if ("DRIVER".equals(role)) {
            redirect = "/driver.html";
        } else if ("USER".equals(role)) {
            redirect = "/user.html";
        }
        out.put("redirect", redirect);
        return out;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> b, HttpServletRequest r) {
        boolean onUserPort = isUserPort(r);
        String name = norm(b.get("username")), pw = b.getOrDefault("password", ""), full = b.getOrDefault("fullName", "").trim();
        String requestedRole = b.getOrDefault("role", onUserPort ? "USER" : "USER").toUpperCase();

        if (!name.matches("[a-z0-9_.-]{3,30}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must be 3-30 letters, digits, dot, dash or underscore.");
        if (pw.length() < 8)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters.");
        if (full.length() > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is too long.");
        if (users.existsByUsername(name))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already taken.");

        String assignedRole = "USER";
        if (onUserPort) {
            assignedRole = "USER";
        } else if ("DRIVER".equals(requestedRole)) {
            assignedRole = "DRIVER";
        } else if ("ADMIN".equals(requestedRole)) {
            assignedRole = "ADMIN";
        }

        AppUser u = users.save(new AppUser(name, passwords.hash(pw), assignedRole, full.isEmpty() ? null : full));
        return view(u);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest r) {
        HttpSession s = r.getSession(false);
        if (s != null) {
            s.invalidate();
        }
        return Map.of("ok", true);
    }

    private static String norm(String v) { return v == null ? "" : v.trim().toLowerCase(); }

    private Map<String, Object> view(AppUser u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", u.getUsername());
        m.put("role", u.getRole());
        m.put("fullName", u.getFullName());
        return m;
    }
}
