package com.wh.reputation.nlp;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * NLP服务管理器
 * 负责在应用启动时自动启动Python NLP服务，并在应用关闭时停止服务
 */
@Component
public class NlpServiceManager {

    private static final Logger log = LoggerFactory.getLogger(NlpServiceManager.class);
    private static final int MAX_STARTUP_WAIT_SECONDS = 30;
    private static final int HEALTH_CHECK_INTERVAL_MS = 1000;

    @Value("${nlp.service.enabled:true}")
    private boolean enabled;

    @Value("${nlp.service.url:http://localhost:8000}")
    private String nlpServiceUrl;

    @Value("${nlp.service.port:8000}")
    private int nlpServicePort;

    private Process nlpProcess;
    private Path nlpServiceDir;

    /**
     * 应用启动后自动启动NLP服务
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startNlpService() {
        if (!enabled) {
            log.info("NLP服务已禁用（nlp.service.enabled=false）");
            return;
        }

        try {
            log.info("=".repeat(60));
            log.info("准备启动NLP服务...");

            // 1. 提取NLP服务文件到临时目录
            extractNlpServiceFiles();

            // 2. 检查Python环境
            if (!checkPythonEnvironment()) {
                log.error("Python环境检查失败，无法启动NLP服务");
                return;
            }

            // 3. 安装依赖
            installDependencies();

            // 4. 启动NLP服务
            startPythonProcess();

            // 5. 等待服务就绪
            if (waitForServiceReady()) {
                log.info("✓ NLP服务启动成功！");
                log.info("  服务地址: {}", nlpServiceUrl);
                log.info("  健康检查: {}/health", nlpServiceUrl);
            } else {
                log.error("✗ NLP服务启动超时或失败");
            }

            log.info("=".repeat(60));

        } catch (Exception e) {
            log.error("启动NLP服务时发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 提取NLP服务文件到临时目录
     */
    private void extractNlpServiceFiles() throws IOException {
        log.info("→ 提取NLP服务文件...");

        // 创建临时目录
        nlpServiceDir = Files.createTempDirectory("nlp_service_");
        log.debug("  临时目录: {}", nlpServiceDir);

        // 需要提取的文件列表
        String[] files = {
                "nlp_service/main.py",
                "nlp_service/clustering.py",
                "nlp_service/topic_modeling.py",
                "nlp_service/requirements.txt"
        };

        for (String file : files) {
            try {
                ClassPathResource resource = new ClassPathResource(file);
                if (resource.exists()) {
                    Path targetPath = nlpServiceDir.resolve(Paths.get(file).getFileName());
                    try (InputStream in = resource.getInputStream()) {
                        Files.copy(in, targetPath);
                        log.debug("  提取: {}", file);
                    }
                } else {
                    log.warn("  文件不存在: {}", file);
                }
            } catch (Exception e) {
                log.error("  提取文件失败: {}", file, e);
            }
        }

        log.info("  ✓ NLP服务文件提取完成");
    }

    /**
     * 检查Python环境
     */
    private boolean checkPythonEnvironment() {
        log.info("→ 检查Python环境...");
        try {
            ProcessBuilder pb = new ProcessBuilder("python", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String version = reader.readLine();

            boolean exited = process.waitFor(5, TimeUnit.SECONDS);
            if (exited && process.exitValue() == 0) {
                log.info("  ✓ {}", version);
                return true;
            } else {
                log.error("  ✗ Python命令执行失败");
                return false;
            }
        } catch (Exception e) {
            log.error("  ✗ 未找到Python环境: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 安装Python依赖
     */
    private void installDependencies() throws IOException, InterruptedException {
        log.info("→ 安装Python依赖...");

        Path requirementsPath = nlpServiceDir.resolve("requirements.txt");
        if (!Files.exists(requirementsPath)) {
            log.warn("  requirements.txt不存在，跳过依赖安装");
            return;
        }

        ProcessBuilder pb = new ProcessBuilder(
                "python", "-m", "pip", "install", "-q", "-r", "requirements.txt");
        pb.directory(nlpServiceDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // 读取输出但不打印（静默安装）
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        while (reader.readLine() != null) {
            // 静默读取
        }

        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (finished && process.exitValue() == 0) {
            log.info("  ✓ 依赖安装完成");
        } else {
            log.warn("  ⚠ 依赖安装可能失败或超时");
        }
    }

    /**
     * 启动Python NLP服务进程
     */
    private void startPythonProcess() throws IOException {
        log.info("→ 启动NLP服务进程...");

        ProcessBuilder pb = new ProcessBuilder("python", "main.py");
        pb.directory(nlpServiceDir.toFile());
        pb.redirectErrorStream(true);

        // 将输出重定向到日志
        nlpProcess = pb.start();

        // 在后台线程中读取进程输出
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(nlpProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[NLP服务] {}", line);
                }
            } catch (IOException e) {
                log.debug("NLP服务输出流关闭");
            }
        }).start();

        log.info("  ✓ NLP服务进程已启动 (PID: {})", nlpProcess.pid());
    }

    /**
     * 等待NLP服务就绪
     */
    private boolean waitForServiceReady() {
        log.info("→ 等待NLP服务就绪...");

        for (int i = 0; i < MAX_STARTUP_WAIT_SECONDS; i++) {
            try {
                Thread.sleep(HEALTH_CHECK_INTERVAL_MS);

                if (checkHealth()) {
                    log.info("  ✓ NLP服务健康检查通过 (耗时: {}s)", i + 1);
                    return true;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        log.error("  ✗ NLP服务启动超时 ({}s)", MAX_STARTUP_WAIT_SECONDS);
        return false;
    }

    /**
     * 健康检查
     */
    private boolean checkHealth() {
        try {
            URL url = new URL(nlpServiceUrl + "/health");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);

            int responseCode = conn.getResponseCode();
            conn.disconnect();

            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 应用关闭时停止NLP服务
     */
    @PreDestroy
    public void stopNlpService() {
        if (nlpProcess != null && nlpProcess.isAlive()) {
            log.info("正在停止NLP服务...");
            nlpProcess.destroy();

            try {
                boolean terminated = nlpProcess.waitFor(5, TimeUnit.SECONDS);
                if (!terminated) {
                    log.warn("NLP服务未能正常终止，强制关闭");
                    nlpProcess.destroyForcibly();
                }
                log.info("✓ NLP服务已停止");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                nlpProcess.destroyForcibly();
            }
        }

        // 清理临时文件
        if (nlpServiceDir != null) {
            try {
                Files.walk(nlpServiceDir)
                        .sorted((a, b) -> b.compareTo(a)) // 先删除文件，后删除目录
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                log.debug("删除临时文件失败: {}", path);
                            }
                        });
                log.info("✓ 临时文件已清理");
            } catch (IOException e) {
                log.warn("清理临时文件时出错: {}", e.getMessage());
            }
        }
    }
}
