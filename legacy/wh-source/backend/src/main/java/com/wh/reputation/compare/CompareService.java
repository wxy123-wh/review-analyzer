package com.wh.reputation.compare;

import com.wh.reputation.analysis.AnalysisQueryService;
import com.wh.reputation.analysis.AspectAnalysisItemDto;
import com.wh.reputation.common.BadRequestException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CompareService {
    private final AnalysisQueryService analysisQueryService;

    public CompareService(AnalysisQueryService analysisQueryService) {
        this.analysisQueryService = analysisQueryService;
    }

    public CompareAspectsResponseDto compareAspects(Long productId, Long competitorId, LocalDate start, LocalDate end) {
        if (productId == null) {
            throw new BadRequestException("缺少产品编号");
        }
        if (competitorId == null) {
            throw new BadRequestException("缺少竞品编号");
        }
        if (productId.equals(competitorId)) {
            throw new BadRequestException("竞品不能与本品相同");
        }

        List<AspectAnalysisItemDto> self = analysisQueryService.aspects(productId, start, end).items();
        List<AspectAnalysisItemDto> competitor = analysisQueryService.aspects(competitorId, start, end).items();

        Map<Long, AspectAnalysisItemDto> competitorMap = competitor.stream()
                .filter(i -> i != null && i.aspectId() != null)
                .collect(Collectors.toMap(AspectAnalysisItemDto::aspectId, i -> i, (a, b) -> a));

        List<CompareAspectItemDto> items = new ArrayList<>();
        List<Double> negDiffs = new ArrayList<>();
        for (AspectAnalysisItemDto s : self) {
            if (s == null || s.aspectId() == null) {
                continue;
            }
            AspectAnalysisItemDto c = competitorMap.getOrDefault(s.aspectId(), null);
            CompareRateDto sRate = new CompareRateDto(s.posRate(), s.neuRate(), s.negRate());
            CompareRateDto cRate = c == null
                    ? new CompareRateDto(0.0, 0.0, 0.0)
                    : new CompareRateDto(c.posRate(), c.neuRate(), c.negRate());
            CompareRateDto diff = new CompareRateDto(
                    sRate.posRate() - cRate.posRate(),
                    sRate.neuRate() - cRate.neuRate(),
                    sRate.negRate() - cRate.negRate()
            );
            negDiffs.add(diff.negRate());
            items.add(new CompareAspectItemDto(
                    s.aspectId(),
                    s.aspectName(),
                    sRate,
                    cRate,
                    diff,
                    new CompareNormalizedDto(0.0)
            ));
        }

        double min = negDiffs.stream().min(Comparator.naturalOrder()).orElse(0.0);
        double max = negDiffs.stream().max(Comparator.naturalOrder()).orElse(0.0);
        double span = max - min;

        List<CompareAspectItemDto> normalizedItems = items.stream()
                .map(i -> {
                    double normalized = span <= 1e-12 ? 0.5 : (i.diff().negRate() - min) / span;
                    return new CompareAspectItemDto(
                            i.aspectId(),
                            i.aspectName(),
                            i.self(),
                            i.competitor(),
                            i.diff(),
                            new CompareNormalizedDto(normalized)
                    );
                })
                .sorted(Comparator.comparingLong(i -> i.aspectId() == null ? Long.MAX_VALUE : i.aspectId()))
                .toList();

        return new CompareAspectsResponseDto(normalizedItems);
    }
}
