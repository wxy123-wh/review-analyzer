# 京东评论采集接入

这个目录是当前项目的新数据入口，目标链路是：

```text
京东商品页 -> crawler/output/raw_reviews.jsonl -> POST /api/v1/reviews/import -> reviews_raw -> /api/v1/analysis/start
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

```powershell
python crawler/jd_reviews.py `
  --product-id 100127936932 `
  --product-code jd-100127936932 `
  --category bluetooth-earphone `
  --max-packets 20
```

脚本会打开浏览器。你需要人工正常登录、进入评论区域、滚动或点击评论筛选。脚本只监听浏览器正常收到的评论数据包，并写入：

```text
crawler/output/raw_reviews.jsonl
```

## 导入后端

先启动后端，然后执行：

```powershell
python crawler/import_reviews.py `
  --input crawler/output/raw_reviews.jsonl `
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
