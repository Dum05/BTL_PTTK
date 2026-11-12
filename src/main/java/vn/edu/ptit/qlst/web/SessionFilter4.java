package vn.edu.ptit.qlst.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.ptit.qlst.model.Models4.Role4;
import vn.edu.ptit.qlst.service.AuthService4.Principal4;

import java.io.IOException;
import java.util.UUID;

@Component
public class SessionFilter4 extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        if (path.startsWith("/assets/")) {
            chain.doFilter(request, response);
            return;
        }
        HttpSession session = request.getSession();
        String csrf = (String) session.getAttribute("csrf");
        if (csrf == null) {
            csrf = UUID.randomUUID().toString();
            session.setAttribute("csrf", csrf);
        }
        Principal4 principal = (Principal4) session.getAttribute("user");
        Role4 required = path.startsWith("/member") ? Role4.CUSTOMER
                : path.startsWith("/warehouse") ? Role4.WAREHOUSE : null;
        response.setHeader("Cache-Control", "no-store");
        if (required != null && principal == null) {
            response.sendRedirect("/");
            return;
        }
        if (required != null && principal.role() != required) {
            response.sendError(403);
            return;
        }
        if ("POST".equals(request.getMethod()) && !csrf.equals(request.getParameter("csrf"))) {
            response.sendError(403);
            return;
        }
        chain.doFilter(request, response);
    }
}
