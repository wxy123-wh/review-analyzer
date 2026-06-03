package com.wh.reputation.meta;

import com.wh.reputation.common.ApiResponse;
import com.wh.reputation.persistence.AspectRepository;
import com.wh.reputation.persistence.PlatformRepository;
import com.wh.reputation.persistence.ProductRepository;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/meta")
public class MetaController {
    private final ProductRepository productRepository;
    private final PlatformRepository platformRepository;
    private final AspectRepository aspectRepository;

    public MetaController(
            ProductRepository productRepository,
            PlatformRepository platformRepository,
            AspectRepository aspectRepository
    ) {
        this.productRepository = productRepository;
        this.platformRepository = platformRepository;
        this.aspectRepository = aspectRepository;
    }

    @GetMapping("/products")
    public ApiResponse<List<ProductMetaDto>> products() {
        var items = productRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(p -> new ProductMetaDto(p.getId(), p.getName(), p.getBrand(), p.getModel(), p.isCompetitor()))
                .toList();
        return ApiResponse.ok(items);
    }

    @GetMapping("/platforms")
    public ApiResponse<List<PlatformMetaDto>> platforms() {
        var items = platformRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(p -> new PlatformMetaDto(p.getId(), p.getName()))
                .toList();
        return ApiResponse.ok(items);
    }

    @GetMapping("/aspects")
    public ApiResponse<List<AspectMetaDto>> aspects() {
        var items = aspectRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(a -> new AspectMetaDto(a.getId(), a.getName()))
                .toList();
        return ApiResponse.ok(items);
    }
}
