package com.atlasglobal.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Retains the HTTP response protections when the API is served without Nginx. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ResponseHeadersFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain chain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        String styles = request.getRequestURI().startsWith("/api/swagger-ui/")
            ? "'self' 'unsafe-inline'" : "'self'";
        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src "
            + styles + "; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'");
        chain.doFilter(request, response);
    }
}
