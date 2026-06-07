package review.backend.application;

import review.backend.api.dto.ProductTaxonomyBindingResponse;
import review.backend.api.dto.TaxonomyResponse;
import review.backend.api.dto.TaxonomyUpsertRequest;
import review.backend.api.dto.UxPrimaryLabelRequest;
import review.backend.api.dto.UxPrimaryLabelResponse;
import review.backend.api.dto.UxSecondaryLabelRequest;
import review.backend.api.dto.UxSecondaryLabelResponse;
import review.backend.data.TaxonomyRepository;
import review.backend.data.TaxonomyRepository.PrimaryLabelDraft;
import review.backend.data.TaxonomyRepository.SecondaryLabelDraft;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class TaxonomyService {

    public static final String FALLBACK_PRIMARY_LABEL = "无明显问题";
    public static final String FALLBACK_SECONDARY_LABEL = "无明显问题";
    private static final String DEFAULT_TAXONOMY_NAME = "默认通用 UX 标签";
    private static final String DEFAULT_PRODUCT_CATEGORY = "general-product";

    private final TaxonomyRepository taxonomyRepository;
    private final ReviewAggregationService reviewAggregationService;

    public TaxonomyService(TaxonomyRepository taxonomyRepository, ReviewAggregationService reviewAggregationService) {
        this.taxonomyRepository = taxonomyRepository;
        this.reviewAggregationService = reviewAggregationService;
    }

    public List<TaxonomyResponse> listTaxonomies() {
        ensureDefaultTaxonomy();
        return taxonomyRepository.findAll();
    }

    public TaxonomyResponse findById(long taxonomyId) {
        return taxonomyRepository.findById(taxonomyId)
                .orElseThrow(() -> new IllegalArgumentException("taxonomy not found: " + taxonomyId));
    }

    public TaxonomyResponse create(TaxonomyUpsertRequest request) {
        return taxonomyRepository.create(
                normalizeLabel(request.name(), "通用 UX 标签"),
                normalizeLabel(request.productCategory(), "general").toLowerCase(Locale.ROOT),
                1,
                toDrafts(request.primaryLabels())
        );
    }

    public TaxonomyResponse createNextVersion(long taxonomyId, TaxonomyUpsertRequest request) {
        TaxonomyResponse current = findById(taxonomyId);
        return taxonomyRepository.create(
                normalizeLabel(request.name(), current.name()),
                normalizeLabel(request.productCategory(), current.productCategory()).toLowerCase(Locale.ROOT),
                current.version() + 1,
                toDrafts(request.primaryLabels())
        );
    }

    public ProductTaxonomyBindingResponse bindProduct(String productCode, long taxonomyId) {
        String normalizedProductCode = reviewAggregationService.normalizeProductCode(productCode);
        TaxonomyResponse taxonomy = findById(taxonomyId);
        taxonomyRepository.bindProduct(normalizedProductCode, taxonomy.taxonomyId());
        return new ProductTaxonomyBindingResponse(normalizedProductCode, taxonomy);
    }

    public ProductTaxonomyBindingResponse findProductBinding(String productCode) {
        String normalizedProductCode = reviewAggregationService.normalizeProductCode(productCode);
        ensureDefaultTaxonomy();
        TaxonomyResponse taxonomy = taxonomyRepository.findBoundForProduct(normalizedProductCode)
                .or(() -> taxonomyRepository.findDefault())
                .orElseThrow(() -> new IllegalStateException("default taxonomy is missing"));
        return new ProductTaxonomyBindingResponse(normalizedProductCode, taxonomy);
    }

    public TaxonomyResponse taxonomyForProduct(String productCode, Long requestedTaxonomyId) {
        ensureDefaultTaxonomy();
        if (requestedTaxonomyId != null) {
            TaxonomyResponse taxonomy = findById(requestedTaxonomyId);
            taxonomyRepository.bindProduct(productCode, taxonomy.taxonomyId());
            return taxonomy;
        }
        Optional<TaxonomyResponse> bound = taxonomyRepository.findBoundForProduct(productCode);
        if (bound.isPresent()) {
            return bound.get();
        }
        TaxonomyResponse fallback = taxonomyRepository.findDefault()
                .orElseThrow(() -> new IllegalStateException("default taxonomy is missing"));
        taxonomyRepository.bindProduct(productCode, fallback.taxonomyId());
        return fallback;
    }

    public NlpTaxonomy toNlpTaxonomy(TaxonomyResponse taxonomy) {
        return new NlpTaxonomy(
                taxonomy.taxonomyId(),
                taxonomy.name(),
                taxonomy.productCategory(),
                taxonomy.version(),
                taxonomy.primaryLabels().stream()
                        .map(primary -> new NlpPrimaryLabel(
                                primary.labelName(),
                                primary.secondaryLabels().stream()
                                        .filter(UxSecondaryLabelResponse::enabled)
                                        .map(secondary -> new NlpSecondaryLabel(
                                                secondary.labelName(),
                                                secondary.synonyms(),
                                                secondary.description()
                                        ))
                                        .toList()
                        ))
                        .toList()
        );
    }

    public String primaryForSecondary(TaxonomyResponse taxonomy, String uxSecondaryLabel) {
        if (uxSecondaryLabel == null || uxSecondaryLabel.isBlank()) {
            return FALLBACK_PRIMARY_LABEL;
        }
        for (UxPrimaryLabelResponse primary : taxonomy.primaryLabels()) {
            for (UxSecondaryLabelResponse secondary : primary.secondaryLabels()) {
                if (uxSecondaryLabel.equals(secondary.labelName())) {
                    return primary.labelName();
                }
            }
        }
        return FALLBACK_PRIMARY_LABEL;
    }

    public String normalizeSecondaryLabel(TaxonomyResponse taxonomy, String value) {
        String normalized = normalizeLabel(value, FALLBACK_SECONDARY_LABEL);
        for (UxPrimaryLabelResponse primary : taxonomy.primaryLabels()) {
            for (UxSecondaryLabelResponse secondary : primary.secondaryLabels()) {
                if (secondary.enabled() && normalized.equals(secondary.labelName())) {
                    return normalized;
                }
            }
        }
        return FALLBACK_SECONDARY_LABEL;
    }

    public String legacyAspectFor(TaxonomyResponse taxonomy, String uxSecondaryLabel) {
        String normalized = normalizeSecondaryLabel(taxonomy, uxSecondaryLabel);
        return switch (normalized) {
            case "电池与续航" -> "battery";
            case "连接与稳定性" -> "bluetooth";
            case "降噪与通透" -> "noise-canceling";
            case "佩戴与人体工学" -> "comfort";
            case "麦克风与通话" -> "microphone";
            default -> ReviewAggregationService.ASPECT_UNKNOWN;
        };
    }

    private void ensureDefaultTaxonomy() {
        TaxonomyResponse defaultTaxonomy = taxonomyRepository.findDefault()
                .orElseGet(() -> taxonomyRepository.create(
                        DEFAULT_TAXONOMY_NAME,
                        DEFAULT_PRODUCT_CATEGORY,
                        1,
                        defaultPrimaryLabels()
                ));
        taxonomyRepository.deactivateDuplicateDefaults(defaultTaxonomy.taxonomyId());
    }

    private List<PrimaryLabelDraft> defaultPrimaryLabels() {
        return List.of(
                new PrimaryLabelDraft("产品硬件", List.of(
                        new SecondaryLabelDraft("电池与续航", List.of("续航", "电池", "充电", "耗电"), "电量、充电和续航体验", true),
                        new SecondaryLabelDraft("连接与稳定性", List.of("连接", "断连", "蓝牙", "网络", "稳定"), "连接、断开、卡顿和稳定性", true),
                        new SecondaryLabelDraft("材质与品控", List.of("做工", "材质", "质量", "瑕疵", "损坏"), "外观材质、质量和品控问题", true)
                )),
                new PrimaryLabelDraft("产品体验", List.of(
                        new SecondaryLabelDraft("佩戴与人体工学", List.of("佩戴", "舒适", "重量", "尺寸"), "佩戴、握持、安装或人体工学体验", true),
                        new SecondaryLabelDraft("交互控制", List.of("按键", "触控", "操作", "设置", "控制"), "操作、控制、设置和使用门槛", true),
                        new SecondaryLabelDraft("软件与生态", List.of("软件", "系统", "app", "兼容", "升级"), "软件、系统、兼容和生态体验", true)
                )),
                new PrimaryLabelDraft("声音表现", List.of(
                        new SecondaryLabelDraft("音质体验", List.of("音质", "声音", "低音", "高音", "清晰"), "声音、画质或核心表现质量", true),
                        new SecondaryLabelDraft("底噪与杂音", List.of("杂音", "底噪", "噪音", "电流声"), "噪声、杂音和异常声音", true),
                        new SecondaryLabelDraft("降噪与通透", List.of("降噪", "通透", "隔音"), "降噪、隔音和环境感知体验", true),
                        new SecondaryLabelDraft("麦克风与通话", List.of("通话", "麦克风", "收音", "语音"), "通话、收音和语音沟通", true)
                )),
                new PrimaryLabelDraft("服务与履约", List.of(
                        new SecondaryLabelDraft("物流与包装", List.of("物流", "快递", "包装", "发货"), "物流、包装和到货体验", true),
                        new SecondaryLabelDraft("售后响应", List.of("售后", "客服", "退换", "维修"), "客服、售后、退换和维修体验", true)
                )),
                new PrimaryLabelDraft("价格价值", List.of(
                        new SecondaryLabelDraft("价格变动", List.of("价格", "降价", "涨价", "保价"), "价格波动和保价体验", true),
                        new SecondaryLabelDraft("性价比预期", List.of("性价比", "值得", "划算", "贵"), "价格与用户预期的匹配度", true)
                )),
                new PrimaryLabelDraft(FALLBACK_PRIMARY_LABEL, List.of(
                        new SecondaryLabelDraft(FALLBACK_SECONDARY_LABEL, List.of("其他", "无明显问题"), "无法归入具体体验标签的评论", true)
                ))
        );
    }

    private List<PrimaryLabelDraft> toDrafts(List<UxPrimaryLabelRequest> primaryLabels) {
        List<PrimaryLabelDraft> drafts = new ArrayList<>();
        for (UxPrimaryLabelRequest primary : primaryLabels) {
            drafts.add(new PrimaryLabelDraft(
                    normalizeLabel(primary.labelName(), "产品体验"),
                    primary.secondaryLabels().stream()
                            .map(this::toSecondaryDraft)
                            .toList()
            ));
        }
        return drafts;
    }

    private SecondaryLabelDraft toSecondaryDraft(UxSecondaryLabelRequest request) {
        List<String> synonyms = request.synonyms() == null
                ? List.of()
                : request.synonyms().stream()
                        .map(item -> normalizeLabel(item, ""))
                        .filter(item -> !item.isBlank())
                        .distinct()
                        .toList();
        return new SecondaryLabelDraft(
                normalizeLabel(request.labelName(), FALLBACK_SECONDARY_LABEL),
                synonyms,
                normalizeLabel(request.description(), ""),
                request.enabled() == null || request.enabled()
        );
    }

    private String normalizeLabel(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    public record NlpTaxonomy(
            long taxonomyId,
            String name,
            String productCategory,
            int version,
            List<NlpPrimaryLabel> primaryLabels
    ) {
    }

    public record NlpPrimaryLabel(String labelName, List<NlpSecondaryLabel> secondaryLabels) {
    }

    public record NlpSecondaryLabel(String labelName, List<String> synonyms, String description) {
    }
}
