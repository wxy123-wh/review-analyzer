package com.wh.reputation.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wh.reputation.persistence.AspectEntity;
import com.wh.reputation.persistence.AspectRepository;
import org.springframework.core.annotation.Order;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;

@Component
@Order(1)
public class AspectSeeder implements ApplicationRunner {
    private final AspectRepository aspectRepository;
    private final ObjectMapper objectMapper;

    public AspectSeeder(AspectRepository aspectRepository, ObjectMapper objectMapper) {
        this.aspectRepository = aspectRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (aspectRepository.count() > 0) {
            return;
        }

        var path = DataFileLocator.resolveRequired("aspects.json");
        JsonNode root;
        try (var in = Files.newInputStream(path)) {
            root = objectMapper.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("加载维度配置失败：" + path, e);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalStateException("维度配置文件格式错误（应为对象）：" + path);
        }

        var fields = root.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            String productType = entry.getKey();
            JsonNode aspectsNode = entry.getValue();

            if (!aspectsNode.isArray()) {
                throw new IllegalStateException("维度配置文件格式错误（产品类型 " + productType + " 应为数组）：" + path);
            }

            for (JsonNode node : aspectsNode) {
                String name = node.path("name").asText(null);
                JsonNode keywords = node.get("keywords");
                if (name == null || name.isBlank() || keywords == null || !keywords.isArray()) {
                    throw new IllegalStateException("维度配置项格式错误：" + node);
                }

                String keywordsJson;
                try {
                    keywordsJson = objectMapper.writeValueAsString(keywords);
                } catch (IOException e) {
                    throw new IllegalStateException("维度关键词序列化失败：" + name, e);
                }

                aspectRepository.save(new AspectEntity(name, productType, keywordsJson, LocalDateTime.now()));
            }
        }
    }
}
