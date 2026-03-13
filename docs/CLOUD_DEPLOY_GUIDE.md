# 蓝牙耳机评测决策系统 — 云端部署指南

本文档面向将本项目一键部署到云服务器的完整操作指南。

---

## 目录

1. [架构总览](#1-架构总览)
2. [云服务器选择与购买](#2-云服务器选择与购买)
3. [服务器初始化](#3-服务器初始化)
4. [部署步骤（Docker Compose 方案）](#4-部署步骤docker-compose-方案)
5. [域名与 HTTPS 配置（可选）](#5-域名与-https-配置可选)
6. [运维与监控](#6-运维与监控)
7. [常见问题](#7-常见问题)

---

## 1. 架构总览

```
                        ┌──────────────────────────────────┐
用户浏览器  ──HTTP──▶   │  Nginx (前端静态 + /api 反向代理)  │  :80
                        └───────────────┬──────────────────┘
                                        │ proxy_pass
                        ┌───────────────▼──────────────────┐
                        │  Spring Boot 后端服务              │  :8080 (内部)
                        │  ├─ PostgreSQL (数据持久化)        │
                        │  ├─ Redis (缓存)                   │
                        │  └─ NLP Service (FastAPI)          │
                        └──────────────────────────────────┘
```

生产模式只暴露 **一个端口 (80)**，Nginx 负责：
- 提供前端静态文件
- 将 `/api/*` 请求反向代理到后端

---

## 2. 云服务器选择与购买

### 方案 A：阿里云 ECS（推荐国内用户）

| 配置项 | 推荐值 |
|--------|--------|
| 规格 | 2核 4G 以上 |
| 系统 | Ubuntu 22.04 LTS / Alibaba Cloud Linux 3 |
| 磁盘 | 40GB SSD |
| 带宽 | 按量付费 或 5Mbps |
| 安全组 | 开放 80、443 端口 |

购买地址：https://ecs.console.aliyun.com

### 方案 B：腾讯云 CVM

购买地址：https://cloud.tencent.com/product/cvm

### 方案 C：AWS EC2（海外用户）

推荐 `t3.medium`（2核4G），地区选 `ap-northeast-1`（东京）或 `ap-southeast-1`（新加坡）。

> **提示**：所有方案核心要求都一样——**能运行 Docker** 就行。

---

## 3. 服务器初始化

以 Ubuntu 22.04 为例，通过 SSH 登录后执行：

### 3.1 安装 Docker & Docker Compose

```bash
# 更新系统
sudo apt update && sudo apt upgrade -y

# 安装 Docker（官方一键脚本）
curl -fsSL https://get.docker.com | sudo sh

# 将当前用户加入 docker 组（免 sudo）
sudo usermod -aG docker $USER

# 退出再登录使权限生效
exit
# 重新 SSH 登录后验证
docker --version
docker compose version
```

### 3.2 安装 Git

```bash
sudo apt install -y git
```

### 3.3 开放防火墙端口

```bash
# 如果使用 ufw
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 22/tcp
sudo ufw enable
```

> **阿里云/腾讯云用户**：还需要在**云控制台的安全组**中放行 80 和 443 端口。

---

## 4. 部署步骤（Docker Compose 方案）

### 4.1 拉取代码

```bash
cd /opt
sudo git clone <你的仓库地址> wh
sudo chown -R $USER:$USER wh
cd wh
```

> 如果代码没有上传到 Git 仓库，可以用 `scp` 从本地上传：
> ```powershell
> # 在本地 PowerShell 执行（Windows）
> scp -r D:\code\wh root@<服务器IP>:/opt/wh
> ```

### 4.2 配置环境变量

```bash
cp .env.example .env
nano .env
```

修改以下关键配置：

```ini
# 数据库密码（生产环境必须修改！）
POSTGRES_PASSWORD=<一个强密码>

# OneBound API 密钥（如需使用评论同步功能）
ONEBOUND_API_KEY=<你的密钥>
ONEBOUND_API_SECRET=<你的密钥>
```

### 4.3 启动生产环境

```bash
# 使用生产 compose 文件（Nginx 替代 Vite dev server）
docker compose -f docker-compose.prod.yml up --build -d
```

### 4.4 验证服务

```bash
# 检查容器状态
docker compose -f docker-compose.prod.yml ps

# 健康检查
curl http://localhost/api/v1/health
# 预期输出: {"status":"UP","timestamp":"..."}

# 查看日志（排查问题时使用）
docker compose -f docker-compose.prod.yml logs -f backend
```

### 4.5 访问系统

在浏览器中打开：
```
http://<你的服务器公网IP>
```

---

## 5. 域名与 HTTPS 配置（可选）

### 5.1 绑定域名

1. 在域名服务商处添加 A 记录：`review.yourdomain.com` → `服务器公网IP`
2. 等待 DNS 生效（通常 5-10 分钟）

### 5.2 自动申请 HTTPS 证书（Let's Encrypt）

在服务器上安装 Certbot：

```bash
sudo apt install -y certbot

# 先暂停 Docker 服务释放 80 端口
docker compose -f docker-compose.prod.yml down

# 申请证书
sudo certbot certonly --standalone -d review.yourdomain.com

# 证书会保存到：
# /etc/letsencrypt/live/review.yourdomain.com/fullchain.pem
# /etc/letsencrypt/live/review.yourdomain.com/privkey.pem
```

（如果需要，请后续配置 `nginx.prod.conf` 支持 HTTPS 并重新发布镜像即可。本演示只提供 http 协议）

---

## 6. 运维与监控

### 6.1 常用命令

```bash
# 停止所有服务
docker compose -f docker-compose.prod.yml down

# 重启所有服务
docker compose -f docker-compose.prod.yml restart

# 仅重启后端
docker compose -f docker-compose.prod.yml restart backend

# 更新代码后重新部署
git pull
docker compose -f docker-compose.prod.yml up --build -d

# 查看实时日志
docker compose -f docker-compose.prod.yml logs -f

# 查看数据库
docker exec -it wh-postgres psql -U earphone -d earphone_review
```

### 6.2 数据备份

```bash
# 备份 PostgreSQL 数据
docker exec wh-postgres pg_dump -U earphone earphone_review > backup_$(date +%Y%m%d).sql

# 恢复数据
cat backup_20260312.sql | docker exec -i wh-postgres psql -U earphone earphone_review
```

### 6.3 自动重启

`docker-compose.prod.yml` 中已配置 `restart: unless-stopped`，服务器重启后容器会自动恢复运行。

---

## 7. 常见问题

### Q: 容器启动报端口冲突
```bash
# 检查占用端口的进程
sudo lsof -i :80
# 停掉冲突进程或修改 docker-compose.prod.yml 中的 PUBLIC_PORT
```

### Q: 后端容器不断重启
```bash
docker compose -f docker-compose.prod.yml logs backend
# 常见原因：PostgreSQL 未启动完成，等 healthcheck 通过后后端会自动连接
```

### Q: 前端加载白屏
确认 Nginx 配置中 `proxy_pass` 地址与后端容器名一致（`backend`），并检查前端构建时环境变量是否有问题。

### Q: 磁盘空间不足
```bash
# 清理 Docker 构建缓存
docker system prune -f
docker builder prune -f
```

---

## 快速部署速查卡

```bash
# 1. SSH 登录服务器
ssh root@<IP>

# 2. 安装 Docker
curl -fsSL https://get.docker.com | sh

# 3. 拉取代码
cd /opt && git clone <仓库地址> wh && cd wh

# 4. 配置环境变量
cp .env.example .env && nano .env

# 5. 一键启动
docker compose -f docker-compose.prod.yml up --build -d

# 6. 验证
curl http://localhost/api/v1/health
```

🎉 **完成！** 浏览器访问 `http://<服务器IP>` 即可使用系统。
