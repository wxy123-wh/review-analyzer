package com.wh.reputation.analysis;

import com.wh.reputation.persistence.PlatformEntity;
import com.wh.reputation.persistence.PlatformRepository;
import com.wh.reputation.persistence.ProductEntity;
import com.wh.reputation.persistence.ProductRepository;
import com.wh.reputation.persistence.ReviewEntity;
import com.wh.reputation.persistence.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

@Component
@Order(2)
public class SampleReviewsSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SampleReviewsSeeder.class);
    private static final DateTimeFormatter REVIEW_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final HexFormat HEX = HexFormat.of();

    private final PlatformRepository platformRepository;
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;

    public SampleReviewsSeeder(
            PlatformRepository platformRepository,
            ProductRepository productRepository,
            ReviewRepository reviewRepository) {
        this.platformRepository = platformRepository;
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (productRepository.count() > 0 || reviewRepository.count() > 0) {
            return;
        }

        Path path = resolveSampleReviewsPathOrNull();
        if (path == null) {
            return;
        }

        List<SampleReviewRow> rows;
        try {
            rows = readCsv(path);
        } catch (Exception e) {
            log.warn("读取示例评论失败，跳过初始化：{}", path, e);
            return;
        }
        if (rows.isEmpty()) {
            return;
        }

        Map<String, PlatformEntity> platformMap = new HashMap<>();
        Map<ProductKey, ProductEntity> productMap = new HashMap<>();

        for (SampleReviewRow row : rows) {
            PlatformEntity platform = platformMap.computeIfAbsent(row.platformName(), this::findOrCreatePlatform);
            ProductKey productKey = new ProductKey(row.productName(), row.brand(), row.model());
            ProductEntity product = productMap.get(productKey);
            if (product == null) {
                product = findOrCreateProduct(productKey.name(), productKey.brand(), productKey.model(),
                        row.isCompetitor());
                productMap.put(productKey, product);
            } else if (row.isCompetitor() && !product.isCompetitor()) {
                product.setCompetitor(true);
                productRepository.save(product);
            }
            row.attach(platform, product);
        }

        Set<String> hashes = new HashSet<>(rows.stream().map(SampleReviewRow::hash).toList());
        Set<String> existing = new HashSet<>(reviewRepository.findExistingHashes(hashes));

        int inserted = 0;
        LocalDateTime now = LocalDateTime.now();
        for (SampleReviewRow row : rows) {
            if (existing.contains(row.hash())) {
                continue;
            }
            String sentimentLabel = sentimentLabelOf(row.rating());
            ReviewEntity entity = new ReviewEntity(
                    row.platform(),
                    row.product(),
                    row.reviewIdRaw(),
                    row.rating(),
                    row.content(),
                    row.content(),
                    row.reviewTime(),
                    row.likeCount(),
                    row.hash(),
                    sentimentLabel,
                    0.0,
                    now);
            reviewRepository.save(entity);
            inserted++;
        }

        log.info("已初始化示例评论：新增={}，文件={}", inserted, path.toAbsolutePath());
    }

    private Path resolveSampleReviewsPathOrNull() {
        try {
            return DataFileLocator.resolveRequired("sample_reviews.csv");
        } catch (Exception e) {
            log.warn("未找到示例评论文件，跳过初始化：{}", e.getMessage());
            return null;
        }
    }

    private PlatformEntity findOrCreatePlatform(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("缺少平台名称");
        }
        return platformRepository.findByName(name)
                .orElseGet(() -> platformRepository.save(new PlatformEntity(name, LocalDateTime.now())));
    }

    private ProductEntity findOrCreateProduct(String name, String brand, String model, boolean isCompetitor) {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("缺少产品名称");
        }
        return productRepository.findExisting(name, emptyToNull(brand), emptyToNull(model))
                .map(existing -> {
                    if (isCompetitor && !existing.isCompetitor()) {
                        existing.setCompetitor(true);
                        productRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> productRepository.save(new ProductEntity(
                        name,
                        emptyToNull(brand),
                        emptyToNull(model),
                        "headphones",
                        isCompetitor,
                        LocalDateTime.now())));
    }

    private static String emptyToNull(String v) {
        if (v == null) {
            return null;
        }
        String trimmed = v.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static String sentimentLabelOf(Integer rating) {
        if (rating == null) {
            return "NEU";
        }
        if (rating <= 2) {
            return "NEG";
        }
        if (rating >= 4) {
            return "POS";
        }
        return "NEU";
    }

    private static List<SampleReviewRow> readCsv(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) {
                return List.of();
            }

            List<SampleReviewRow> out = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> fields = parseCsvLine(line);
                if (fields.size() < 9) {
                    continue;
                }
                out.add(SampleReviewRow.from(fields));
            }
            return out;
        }
    }

    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        buf.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                    continue;
                }
                buf.append(c);
                continue;
            }

            if (c == '"') {
                inQuotes = true;
                continue;
            }
            if (c == ',') {
                out.add(buf.toString());
                buf.setLength(0);
                continue;
            }
            buf.append(c);
        }
        out.add(buf.toString());
        return out;
    }

    private record ProductKey(String name, String brand, String model) {
        private ProductKey {
            name = normalizeKeyPart(name);
            brand = normalizeKeyPart(brand);
            model = normalizeKeyPart(model);
        }
    }

    private static final class SampleReviewRow {
        private final String platformName;
        private final String productName;
        private final String brand;
        private final String model;
        private final Integer rating;
        private final LocalDateTime reviewTime;
        private final String content;
        private final Integer likeCount;
        private final String reviewIdRaw;
        private final String hash;
        private final boolean isCompetitor;

        private PlatformEntity platform;
        private ProductEntity product;

        private SampleReviewRow(
                String platformName,
                String productName,
                String brand,
                String model,
                Integer rating,
                LocalDateTime reviewTime,
                String content,
                Integer likeCount,
                String reviewIdRaw,
                String hash,
                boolean isCompetitor) {
            this.platformName = platformName;
            this.productName = productName;
            this.brand = brand;
            this.model = model;
            this.rating = rating;
            this.reviewTime = reviewTime;
            this.content = content;
            this.likeCount = likeCount;
            this.reviewIdRaw = reviewIdRaw;
            this.hash = hash;
            this.isCompetitor = isCompetitor;
        }

        static SampleReviewRow from(List<String> fields) {
            String platformName = fields.get(0);
            String productName = fields.get(1);
            String brand = fields.get(2);
            String model = fields.get(3);
            Integer rating = parseIntOrNull(fields.get(4));
            LocalDateTime reviewTime = parseReviewTimeOrNull(fields.get(5));
            String content = fields.get(6);
            Integer likeCount = parseIntOrNull(fields.get(7));
            String reviewIdRaw = fields.get(8);
            String hash = hashOf(reviewIdRaw == null ? content : reviewIdRaw);
            boolean isCompetitor = fields.size() >= 10 && parseBooleanOrFalse(fields.get(9));
            return new SampleReviewRow(
                    platformName,
                    productName,
                    brand,
                    model,
                    rating,
                    reviewTime,
                    content,
                    likeCount,
                    reviewIdRaw,
                    hash,
                    isCompetitor);
        }

        void attach(PlatformEntity platform, ProductEntity product) {
            this.platform = platform;
            this.product = product;
        }

        String platformName() {
            return platformName;
        }

        String productName() {
            return productName;
        }

        String brand() {
            return brand;
        }

        String model() {
            return model;
        }

        Integer rating() {
            return rating;
        }

        LocalDateTime reviewTime() {
            return reviewTime;
        }

        String content() {
            return content == null ? "" : content;
        }

        Integer likeCount() {
            return likeCount;
        }

        String reviewIdRaw() {
            return reviewIdRaw;
        }

        String hash() {
            return hash;
        }

        PlatformEntity platform() {
            return platform;
        }

        ProductEntity product() {
            return product;
        }

        boolean isCompetitor() {
            return isCompetitor;
        }

        private static Integer parseIntOrNull(String input) {
            if (input == null || input.isBlank()) {
                return null;
            }
            try {
                return Integer.parseInt(input.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private static LocalDateTime parseReviewTimeOrNull(String input) {
            if (input == null || input.isBlank()) {
                return null;
            }
            try {
                return LocalDateTime.parse(input.trim(), REVIEW_TIME_FORMAT);
            } catch (Exception e) {
                return null;
            }
        }

        private static String hashOf(String input) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] bytes = digest.digest((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
                return HEX.formatHex(bytes);
            } catch (Exception e) {
                String raw = input == null ? "" : input;
                return Integer.toHexString(raw.hashCode());
            }
        }

        private static boolean parseBooleanOrFalse(String input) {
            if (input == null) {
                return false;
            }
            String normalized = input.trim().toLowerCase(Locale.ROOT);
            return normalized.equals("1") || normalized.equals("true") || normalized.equals("yes")
                    || normalized.equals("y");
        }
    }

    private static String normalizeKeyPart(String v) {
        if (v == null) {
            return "";
        }
        return v.trim();
    }
}
