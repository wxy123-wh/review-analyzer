# 京东评论采集接入

这个目录是当前项目的新数据入口，目标链路是：

```text
京东商品页 -> crawler/output/raw_reviews.jsonl -> POST /api/v1/reviews/import -> reviews_raw -> /api/v1/analysis/start
```

后端或前端要按“输入商品链接即可重新采集评论”接入时，推荐调用本目录的轻量 FastAPI 服务：

```text
POST /crawl/start -> crawler/output/raw_reviews_{productCode}.jsonl
GET /crawl/jobs/{id} -> 查看采集进度、输出文件和人工验证提示
```

## 与参考仓库的关系

参考仓库已下载到：

```text
.tmp/external/Crawling-User-Reviews-from-JD.com
```

本目录没有直接照搬参考仓库的大脚本，而是抽取了几个适合当前项目的能力：

- DrissionPage 浏览器辅助采集。
- 网络响应包监听。
- JSONL 原始评论输出。
- 根据评论 ID 或内容哈希去重。
- 进度文件断点记录。
- 保守等待，降低访问频率。
- 检测验证码/风控提示后暂停，不做绕过。

## 安装

```powershell
python -m pip install -r crawler/requirements.txt
```

## 采集京东评论

方式一：直接用 CLI。既可以传商品 ID，也可以传商品链接：

```powershell
python crawler/jd_reviews.py `
  --product-url https://item.jd.com/100127936932.html `
  --product-code jd-100127936932 `
  --category bluetooth-earphone `
  --max-packets 20
```

脚本会打开浏览器。你需要人工正常登录、进入评论区域、滚动或点击评论筛选。脚本只监听浏览器正常收到的评论数据包，并写入：

```text
crawler/output/raw_reviews.jsonl
```

方式二：启动 crawler 服务，供后端调用：

```powershell
python -m uvicorn crawler.service:app --host 127.0.0.1 --port 8010
```

启动采集任务：

```powershell
curl -X POST http://127.0.0.1:8010/crawl/start `
  -H "Content-Type: application/json" `
  -d "{\"productUrl\":\"https://item.jd.com/100127936932.html\",\"productCode\":\"jd-100127936932\",\"category\":\"bluetooth-earphone\",\"maxPackets\":20}"
```

查询任务：

```powershell
curl http://127.0.0.1:8010/crawl/jobs/{jobId}
```

服务会把同一 `productCode` 的采集结果追加到 `crawler/output/raw_reviews_{productCode}.jsonl`。写入时继续依赖 `source + sourceReviewId` 去重；重复采集同一评论不会重复写入，后端导入时也会继续按 `sourceReviewId` upsert 到已有商品。

如果需要在不打开浏览器的情况下验证解析逻辑，可以使用 dry-run：

```powershell
python crawler/jd_reviews.py `
  --product-url https://item.jd.com/100127936932.html `
  --product-code jd-100127936932 `
  --dry-run-packet crawler/tests/fixtures/jd_packet.json
```

## 导入后端

先启动后端，然后执行：

```powershell
python crawler/import_reviews.py `
  --input crawler/output/raw_reviews_jd-100127936932.jsonl `
  --backend http://localhost:8080 `
  --product-code jd-100127936932
```

导入成功后，再触发分析：

```powershell
curl -X POST http://localhost:8080/api/v1/analysis/start `
  -H "Content-Type: application/json" `
  -d "{\"productCode\":\"jd-100127936932\"}"
```

## 合规边界

这个采集模块只用于课程项目和合规数据采集演示。不实现验证码绕过、登录绕过、反爬破解或高频请求。出现验证时，需要人工处理或停止采集。
