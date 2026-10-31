package com.marudham.web.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * PortAccessFilter protects routes and enforces role-based navigation.
 * Port 8080 = Full smart city platform (Admin console, Driver console, Citizen portal).
 * Port 8081 = Dedicated Citizen portal port.
 */
@Component
public class PortAccessFilter extends OncePerRequestFilter {

    @Value("${app.user.port:8081}")
    private int userPort;

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/login.html", "/signup.html",
            "/api/auth/login", "/api/auth/register", "/api/auth/info",
            "/favicon.ico"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        boolean userSite = req.getLocalPort() == userPort;
        HttpSession session = req.getSession(false);

        String username = session == null ? null : (String) session.getAttribute("auth.username");
        String role = session == null ? null : (String) session.getAttribute("auth.role");

        // Fallback to legacy attributes if any
        if (username == null && session != null) {
            if (session.getAttribute("auth.admin") != null) {
                username = (String) session.getAttribute("auth.admin");
                role = "ADMIN";
            } else if (session.getAttribute("auth.driver") != null) {
                username = (String) session.getAttribute("auth.driver");
                role = "DRIVER";
            } else if (session.getAttribute("auth.user") != null) {
                username = (String) session.getAttribute("auth.user");
                role = "USER";
            }
        }

        boolean signedIn = username != null;

        // Redirect root path to appropriate portal
        if (path.equals("/") || path.equals("/index.html")) {
            if (!signedIn) {
                res.sendRedirect("/login.html");
                return;
            }
            if ("DRIVER".equals(role)) {
                res.sendRedirect("/driver.html");
            } else if ("USER".equals(role) || userSite) {
                res.sendRedirect("/user.html");
            } else {
                res.sendRedirect("/admin.html");
            }
            return;
        }

        // If visiting login/signup while already signed in, redirect to home portal
        if ((path.equals("/login.html") || path.equals("/signup.html")) && signedIn) {
            if ("DRIVER".equals(role)) res.sendRedirect("/driver.html");
            else if ("USER".equals(role) || userSite) res.sendRedirect("/user.html");
            else res.sendRedirect("/admin.html");
            return;
        }

        // Allow public paths
        if (PUBLIC_PATHS.contains(path) || path.startsWith("/static/") || path.startsWith("/css/") || path.startsWith("/js/")) {
            chain.doFilter(req, res);
            return;
        }

        // Dedicated User Port 8081 restrictions
        if (userSite) {
            if (path.equals("/admin.html") || path.equals("/driver.html")) {
                res.sendRedirect("/user.html");
                return;
            }
            if (path.startsWith("/api/admin/") || path.startsWith("/api/trucks") || path.startsWith("/api/optimizer") || path.startsWith("/api/reports")) {
                json(res, HttpServletResponse.SC_FORBIDDEN, "This action is restricted to the administrator console.");
                return;
            }
        }

        // Check authentication for all non-public paths
        if (!signedIn) {
            if (path.startsWith("/api/")) {
                json(res, HttpServletResponse.SC_UNAUTHORIZED, "Please sign in to access this resource.");
            } else {
                res.sendRedirect("/login.html");
            }
            return;
        }

        // Role-based page access checks
        if (path.equals("/admin.html") && !"ADMIN".equals(role)) {
            res.sendRedirect("DRIVER".equals(role) ? "/driver.html" : "/user.html");
            return;
        }

        if (path.equals("/driver.html") && !"DRIVER".equals(role) && !"ADMIN".equals(role)) {
            res.sendRedirect("/user.html");
            return;
        }

        chain.doFilter(req, res);
    }

    private void json(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
