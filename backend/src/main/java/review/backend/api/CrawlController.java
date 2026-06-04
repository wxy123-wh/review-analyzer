package review.backend.api;

import review.backend.api.dto.CrawlJobResponse;
import review.backend.api.dto.CrawlStartRequest;
import review.backend.application.CrawlJobService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/crawl")
public class CrawlController {

    private final CrawlJobService crawlJobService;

    public CrawlController(CrawlJobService crawlJobService) {
        this.crawlJobService = crawlJobService;
    }

    @PostMapping("/start")
    public ResponseEntity<CrawlJobResponse> start(@Valid @RequestBody CrawlStartRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(crawlJobService.start(request));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> job(@PathVariable("id") String id) {
        return crawlJobService.findJob(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "crawl job not found", "jobId", id)));
    }

    @PostMapping("/jobs/{id}/import")
    public ResponseEntity<?> importJsonl(@PathVariable("id") String id) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(crawlJobService.cleanAndImport(id));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", ex.getMessage(), "jobId", id));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", ex.getMessage(), "jobId", id));
        }
    }
}
