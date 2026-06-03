package com.wh.reputation.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    // Serve index.html explicitly for frontend routes
    @GetMapping(value = {
            "/",
            "/login",
            "/dashboard/**",
            "/overview",
            "/analysis",
            "/reviews",
            "/clusters",
            "/suggestions",
            "/events",
            "/before-after",
            "/topics",
            "/compare",
            "/alerts"
    })
    public ResponseEntity<Resource> index() {
        Resource resource = new ClassPathResource("static/index.html");
        if (resource.exists()) {
            return ResponseEntity.ok().body(resource);
        }
        return ResponseEntity.notFound().build();
    }
}
