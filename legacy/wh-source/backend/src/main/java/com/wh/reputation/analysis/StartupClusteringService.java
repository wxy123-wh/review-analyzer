package com.wh.reputation.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 应用启动时自动执行聚类分析服务
 * 在应用完全启动后，异步为所有产品执行聚类分析
 */
@Service
public class StartupClusteringService {

    private static final Logger log = LoggerFactory.getLogger(StartupClusteringService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ClusterAnalysisService clusterAnalysisService;

    @Value("${clustering.auto-run-on-startup:true}")
    private boolean autoRunOnStartup;

    public StartupClusteringService(JdbcTemplate jdbcTemplate, ClusterAnalysisService clusterAnalysisService) {
        this.jdbcTemplate = jdbcTemplate;
        this.clusterAnalysisService = clusterAnalysisService;
    }

    /**
     * 监听应用就绪事件，触发自动聚类
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!autoRunOnStartup) {
            log.info("自动聚类已禁用（clustering.auto-run-on-startup=false）");
            return;
        }
        log.info("应用已就绪，准备执行自动聚类任务...");
        runClusteringForAllProducts();
    }

    /**
     * 异步执行聚类任务，避免阻塞主线程
     */
    @Async
    public void runClusteringForAllProducts() {
        try {
            log.info("开始为所有产品执行聚类分析...");

            // 查询所有产品ID
            List<Long> productIds = jdbcTemplate.queryForList(
                    "SELECT id FROM product ORDER BY id",
                    Long.class);

            if (productIds.isEmpty()) {
                log.info("没有找到任何产品，跳过聚类任务");
                return;
            }

            log.info("找到 {} 个产品，开始批量聚类", productIds.size());
            int successCount = 0;
            int failCount = 0;

            // 为每个产品执行聚类
            for (Long productId : productIds) {
                try {
                    log.info("正在为产品 {} 执行聚类...", productId);

                    // 执行聚类（不限定时间范围，分析所有评论）
                    clusterAnalysisService.recompute(productId, null, null);

                    successCount++;
                    log.info("产品 {} 聚类完成", productId);
                } catch (Exception e) {
                    failCount++;
                    log.error("产品 {} 聚类失败: {}", productId, e.getMessage(), e);
                    // 继续处理下一个产品
                }
            }

            log.info("自动聚类任务完成！成功: {}, 失败: {}, 总计: {}",
                    successCount, failCount, productIds.size());

        } catch (Exception e) {
            log.error("执行自动聚类任务时发生错误: {}", e.getMessage(), e);
        }
    }
}
