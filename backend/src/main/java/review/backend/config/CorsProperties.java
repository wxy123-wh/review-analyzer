package review.backend.config;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    private List<String> allowedOrigins = List.of("http://localhost:5175", "http://127.0.0.1:5175");
    private List<String> allowedOriginPatterns = List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://[::1]:*",
            "http://192.168.*.*:*",
            "http://10.*.*.*:*",
            "http://172.*.*.*:*"
    );

    public List<String> getAllowedOrigins() {
        return normalize(allowedOrigins);
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins;
    }

    public List<String> getAllowedOriginPatterns() {
        return normalize(allowedOriginPatterns);
    }

    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns == null ? List.of() : allowedOriginPatterns;
    }

    private List<String> normalize(List<String> origins) {
        return origins.stream()
                .map(origin -> origin == null ? "" : origin.trim())
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }
}
