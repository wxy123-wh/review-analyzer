package com.wh.reputation.review;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wh.reputation.analysis.ReviewAnalysisService;
import com.wh.reputation.common.BadRequestException;
import com.wh.reputation.persistence.PlatformEntity;
import com.wh.reputation.persistence.PlatformRepository;
import com.wh.reputation.persistence.ProductEntity;
import com.wh.reputation.persistence.ProductRepository;
import com.wh.reputation.persistence.ReviewEntity;
import com.wh.reputation.persistence.ReviewRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.ZoneId;
import java.util.*;

@Service
public class ReviewImportService {
    private static final List<String> BASE_HEADER = List.of(
            "platform_name",
            "product_name",
            "brand",
            "model",
            "rating",
            "review_time",
            "content",
            "like_count",
            "review_id_raw");
    private static final List<String> HEADER_WITH_COMPETITOR = List.of(
            "platform_name",
            "product_name",
            "brand",
            "model",
            "rating",
            "review_time",
            "content",
            "like_count",
            "review_id_raw",
            "is_competitor");
    private static final DateTimeFormatter CSV_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PlatformRepository platformRepository;
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewAnalysisService reviewAnalysisService;
    private final com.wh.reputation.search.ReviewIndexingService reviewIndexingService;
    private final ObjectMapper objectMapper;

    public ReviewImportService(
            PlatformRepository platformRepository,
            ProductRepository productRepository,
            ReviewRepository reviewRepository,
            ReviewAnalysisService reviewAnalysisService,
            com.wh.reputation.search.ReviewIndexingService reviewIndexingService,
            ObjectMapper objectMapper) {
        this.platformRepository = platformRepository;
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.reviewAnalysisService = reviewAnalysisService;
        this.reviewIndexingService = reviewIndexingService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReviewImportResult importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("请上传文件");
        }

        String filename = file.getOriginalFilename();
        String lower = filename == null ? "" : filename.trim().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return importCsv(file);
        }
        if (lower.endsWith(".xlsx")) {
            return importXlsx(file);
        }
        if (lower.endsWith(".json")) {
            return importJsonFile(file);
        }

        String contentType = file.getContentType();
        String contentTypeLower = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (contentTypeLower.contains("spreadsheet") || contentTypeLower.contains("excel")) {
            return importXlsx(file);
        }
        if (contentTypeLower.contains("json")) {
            return importJsonFile(file);
        }
        if (contentTypeLower.contains("csv") || contentTypeLower.contains("text/plain")) {
            return importCsv(file);
        }

        throw new BadRequestException("不支持的文件类型");
    }

    @Transactional
    public ReviewImportResult importJson(List<ReviewImportItem> items) {
        if (items == null) {
            throw new BadRequestException("请求体不能为空");
        }
        return importItems(items);
    }

    @Transactional
    public ReviewImportResult importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("请上传文件");
        }

        int errors = 0;
        List<ValidRow> validRows = new ArrayList<>();

        try (var reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new BadRequestException("导入文件为空");
            }
            List<String> header = CsvUtils.parseLine(headerLine).stream()
                    .map(String::trim)
                    .toList();
            List<String> normalizedHeader = stripBom(header);
            boolean hasCompetitorCol = normalizedHeader.equals(HEADER_WITH_COMPETITOR);
            boolean headerOk = normalizedHeader.equals(BASE_HEADER) || hasCompetitorCol;
            if (!headerOk) {
                throw new BadRequestException("导入文件表头不符合要求");
            }
            int expectedCols = hasCompetitorCol ? HEADER_WITH_COMPETITOR.size() : BASE_HEADER.size();

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                List<String> cols;
                try {
                    cols = CsvUtils.parseLine(line);
                } catch (IllegalArgumentException e) {
                    errors++;
                    continue;
                }
                if (cols.size() != expectedCols) {
                    errors++;
                    continue;
                }

                Optional<ValidRow> parsed;
                try {
                    parsed = parseRow(cols);
                } catch (IllegalArgumentException e) {
                    errors++;
                    continue;
                }
                if (parsed.isEmpty()) {
                    errors++;
                    continue;
                }
                validRows.add(parsed.get());
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取导入文件失败", e);
        }

        return saveValidRows(validRows, errors);
    }

    private ReviewImportResult importJsonFile(MultipartFile file) {
        List<ReviewImportItem> items;
        try (var input = file.getInputStream()) {
            items = objectMapper.readValue(input, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new BadRequestException("导入文件格式不正确");
        }
        return importItems(items);
    }

    private ReviewImportResult importXlsx(MultipartFile file) {
        int errors = 0;
        List<ValidRow> validRows = new ArrayList<>();

        try (var workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new BadRequestException("导入文件为空");
            }

            int headerRowNum = sheet.getFirstRowNum();
            Row headerRow = sheet.getRow(headerRowNum);
            if (headerRow == null) {
                throw new BadRequestException("导入文件为空");
            }

            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            List<String> header = new ArrayList<>(HEADER_WITH_COMPETITOR.size());
            for (int i = 0; i < HEADER_WITH_COMPETITOR.size(); i++) {
                header.add(readCellString(headerRow.getCell(i), formatter, evaluator));
            }
            while (!header.isEmpty() && header.get(header.size() - 1).isBlank()) {
                header.remove(header.size() - 1);
            }
            List<String> normalizedHeader = stripBom(header);
            boolean hasCompetitorCol = normalizedHeader.equals(HEADER_WITH_COMPETITOR);
            boolean headerOk = normalizedHeader.equals(BASE_HEADER) || hasCompetitorCol;
            if (!headerOk) {
                throw new BadRequestException("导入文件表头不符合要求");
            }
            int expectedCols = hasCompetitorCol ? HEADER_WITH_COMPETITOR.size() : BASE_HEADER.size();

            int lastRowNum = sheet.getLastRowNum();
            for (int r = headerRowNum + 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowBlank(row, expectedCols, formatter, evaluator)) {
                    continue;
                }

                List<String> cols = new ArrayList<>(expectedCols);
                for (int c = 0; c < expectedCols; c++) {
                    cols.add(readCellByColumnIndex(row.getCell(c), c, formatter, evaluator));
                }

                Optional<ValidRow> parsed;
                try {
                    parsed = parseRow(cols);
                } catch (IllegalArgumentException e) {
                    errors++;
                    continue;
                }
                if (parsed.isEmpty()) {
                    errors++;
                    continue;
                }
                validRows.add(parsed.get());
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalStateException("读取导入文件失败", e);
        }

        return saveValidRows(validRows, errors);
    }

    private ReviewImportResult importItems(List<ReviewImportItem> items) {
        int errors = 0;
        List<ValidRow> validRows = new ArrayList<>();
        for (ReviewImportItem item : items) {
            if (item == null) {
                errors++;
                continue;
            }

            List<String> cols = List.of(
                    emptyIfNull(item.platformName()),
                    emptyIfNull(item.productName()),
                    emptyIfNull(item.brand()),
                    emptyIfNull(item.model()),
                    item.rating() == null ? "" : String.valueOf(item.rating()),
                    emptyIfNull(item.reviewTime()),
                    emptyIfNull(item.content()),
                    item.likeCount() == null ? "" : String.valueOf(item.likeCount()),
                    emptyIfNull(item.reviewIdRaw()),
                    item.isCompetitor() == null ? "" : String.valueOf(item.isCompetitor()));

            Optional<ValidRow> parsed;
            try {
                parsed = parseRow(cols);
            } catch (IllegalArgumentException e) {
                errors++;
                continue;
            }
            if (parsed.isEmpty()) {
                errors++;
                continue;
            }
            validRows.add(parsed.get());
        }

        return saveValidRows(validRows, errors);
    }

    private ReviewImportResult saveValidRows(List<ValidRow> validRows, int errors) {
        Set<String> existingHashes = loadExistingHashes(validRows);
        Map<String, PlatformEntity> platformCache = new HashMap<>();
        Map<ProductKey, ProductEntity> productCache = new HashMap<>();

        int inserted = 0;
        int skipped = 0;
        List<Long> insertedReviewIds = new ArrayList<>();
        for (ValidRow row : validRows) {
            if (existingHashes.contains(row.hash())) {
                skipped++;
                continue;
            }

            PlatformEntity platform = platformCache.computeIfAbsent(row.platformName(),
                    name -> platformRepository.findByName(name)
                            .orElseGet(() -> platformRepository.save(new PlatformEntity(name, now()))));

            ProductKey productKey = new ProductKey(row.productName(), row.brand(), row.model());
            boolean competitorFlag = Boolean.TRUE.equals(row.isCompetitor());
            ProductEntity product = productCache.computeIfAbsent(productKey, key -> {
                ProductEntity found = productRepository.findExisting(key.name(), key.brand(), key.model())
                        .or(() -> key.brand() == null && key.model() == null
                                ? productRepository.findFirstByNameOrderByIdAsc(key.name())
                                : Optional.empty())
                        .orElseGet(() -> productRepository.save(new ProductEntity(
                                key.name(),
                                key.brand(),
                                key.model(),
                                "headphones",
                                competitorFlag,
                                now())));
                if (competitorFlag && !found.isCompetitor()) {
                    found.setCompetitor(true);
                    productRepository.save(found);
                }
                return found;
            });

            ReviewEntity entity = new ReviewEntity(
                    platform,
                    product,
                    row.reviewIdRaw(),
                    row.rating(),
                    row.contentRaw(),
                    row.contentClean(),
                    row.reviewTime(),
                    row.likeCount(),
                    row.hash(),
                    "NEU",
                    0.0,
                    now());
            ReviewEntity saved = reviewRepository.save(entity);

            existingHashes.add(row.hash());
            inserted++;
            insertedReviewIds.add(saved.getId());
        }

        reviewAnalysisService.analyzeReviewIds(insertedReviewIds);
        reviewIndexingService.indexReviews(insertedReviewIds);

        return new ReviewImportResult(inserted, skipped, errors);
    }

    private static List<String> stripBom(List<String> header) {
        if (header.isEmpty()) {
            return header;
        }
        String first = header.get(0);
        if (first.startsWith("\uFEFF")) {
            List<String> fixed = new ArrayList<>(header);
            fixed.set(0, first.substring(1));
            return fixed;
        }
        return header;
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private static boolean isRowBlank(
            Row row,
            int columnCount,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        for (int c = 0; c < columnCount; c++) {
            String value = readCellString(row.getCell(c), formatter, evaluator);
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String readCellByColumnIndex(
            Cell cell,
            int columnIndex,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        if (columnIndex == 5) {
            LocalDateTime dateTime = readExcelDateTime(cell);
            if (dateTime != null) {
                return dateTime.format(CSV_TIME_FORMATTER);
            }
        }
        if (columnIndex == 4 || columnIndex == 7) {
            String asInt = readExcelInteger(cell);
            if (asInt != null) {
                return asInt;
            }
        }
        return readCellString(cell, formatter, evaluator);
    }

    private static LocalDateTime readExcelDateTime(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            var date = cell.getDateCellValue();
            if (date == null) {
                return null;
            }
            return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        }
        return null;
    }

    private static String readExcelInteger(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double v = cell.getNumericCellValue();
            long asLong = Math.round(v);
            if (Math.abs(v - asLong) > 1e-9) {
                return null;
            }
            return String.valueOf(asLong);
        }
        return null;
    }

    private static String readCellString(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        String value = evaluator == null
                ? formatter.formatCellValue(cell)
                : formatter.formatCellValue(cell, evaluator);
        return value == null ? "" : value.trim();
    }

    private Set<String> loadExistingHashes(List<ValidRow> rows) {
        if (rows.isEmpty()) {
            return new HashSet<>();
        }
        List<String> hashes = rows.stream().map(ValidRow::hash).toList();
        Set<String> existing = new HashSet<>();
        int chunkSize = 900;
        for (int i = 0; i < hashes.size(); i += chunkSize) {
            List<String> chunk = hashes.subList(i, Math.min(i + chunkSize, hashes.size()));
            existing.addAll(reviewRepository.findExistingHashes(chunk));
        }
        return existing;
    }

    private Optional<ValidRow> parseRow(List<String> cols) {
        String platformName = cols.get(0).trim();
        String productName = cols.get(1).trim();
        String brand = toNullIfBlank(cols.get(2));
        String model = toNullIfBlank(cols.get(3));
        Integer rating = toIntOrNull(cols.get(4));
        String reviewTimeRaw = cols.get(5).trim();
        String contentRaw = cols.get(6);
        Integer likeCount = toIntOrNull(cols.get(7));
        String reviewIdRaw = toNullIfBlank(cols.get(8));
        Boolean isCompetitor = cols.size() >= 10 ? toBooleanOrNull(cols.get(9)) : null;

        if (platformName.isBlank() || productName.isBlank() || contentRaw == null || contentRaw.isBlank()) {
            return Optional.empty();
        }

        String contentClean = TextCleaner.clean(contentRaw);
        if (contentClean.isBlank()) {
            return Optional.empty();
        }

        LocalDateTime reviewTime = null;
        if (!reviewTimeRaw.isBlank()) {
            try {
                reviewTime = LocalDateTime.parse(reviewTimeRaw, CSV_TIME_FORMATTER);
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        }
        String reviewTimeHashPart = reviewTime == null ? "" : reviewTime.format(CSV_TIME_FORMATTER);

        String hashInput = platformName + "|" + productName + "|" + contentClean + "|" + reviewTimeHashPart;
        String hash = HashUtils.sha256Hex(hashInput);

        return Optional.of(new ValidRow(
                platformName,
                productName,
                brand,
                model,
                rating,
                reviewTime,
                contentRaw,
                contentClean,
                likeCount,
                reviewIdRaw,
                hash,
                isCompetitor));
    }

    private static LocalDateTime now() {
        return LocalDateTime.now();
    }

    private static String toNullIfBlank(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static Integer toIntOrNull(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        return Integer.parseInt(trimmed);
    }

    private static Boolean toBooleanOrNull(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.equals("1") || normalized.equals("true") || normalized.equals("yes") || normalized.equals("y")) {
            return true;
        }
        if (normalized.equals("0") || normalized.equals("false") || normalized.equals("no") || normalized.equals("n")) {
            return false;
        }
        return null;
    }

    private record ProductKey(String name, String brand, String model) {
    }

    private record ValidRow(
            String platformName,
            String productName,
            String brand,
            String model,
            Integer rating,
            LocalDateTime reviewTime,
            String contentRaw,
            String contentClean,
            Integer likeCount,
            String reviewIdRaw,
            String hash,
            Boolean isCompetitor) {
    }
}
