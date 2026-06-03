# -*- coding: utf-8 -*-
"""Small crawler service for backend-triggered collection.

The service keeps job state in memory and mirrors it to crawler/output/jobs.
Live crawls still require a normal user-controlled browser session. The code
does not bypass login, captcha, or platform risk controls.
"""

from __future__ import annotations

import json
import re
import threading
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Literal

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from .jd_reviews import collect_jd_reviews


OUTPUT_DIR = Path("crawler/output")
JOBS_DIR = OUTPUT_DIR / "jobs"
DEFAULT_PROGRESS_DIR = OUTPUT_DIR / "progress"
COMPLIANCE_NOTICE = "采集仅监听用户正常浏览产生的评论数据；不会绕过登录、验证码或平台风控。出现验证时任务会停止并提示人工处理。"


class CrawlStartRequest(BaseModel):
    productUrl: str = Field(..., min_length=1)
    productCode: str = Field(..., min_length=1)
    category: str = "bluetooth-earphone"
    maxPackets: int = Field(default=20, ge=1, le=200)
    waitSeconds: int = Field(default=120, ge=10, le=3600)
    outputMode: Literal["raw", "import-ready"] = "raw"
    outputPath: str | None = None


class CrawlJobResponse(BaseModel):
    jobId: str
    status: str
    productUrl: str
    productCode: str
    category: str
    maxPackets: int
    outputPath: str
    progressPath: str
    capturedPackets: int = 0
    newReviewCount: int = 0
    errorMessage: str | None = None
    message: str
    complianceNotice: str = COMPLIANCE_NOTICE
    createdAt: str
    startedAt: str | None = None
    finishedAt: str | None = None


app = FastAPI(title="review-analyzer-crawler", version="0.1.0")
executor = ThreadPoolExecutor(max_workers=1)
jobs: dict[str, dict[str, Any]] = {}
jobs_lock = threading.Lock()


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def safe_filename(value: str) -> str:
    return re.sub(r"[^a-zA-Z0-9_.-]+", "-", value).strip("-") or "product"


def job_path(job_id: str) -> Path:
    return JOBS_DIR / f"{job_id}.json"


def write_job(job: dict[str, Any]) -> None:
    JOBS_DIR.mkdir(parents=True, exist_ok=True)
    job_path(job["jobId"]).write_text(json.dumps(job, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def set_job(job_id: str, **updates: Any) -> dict[str, Any]:
    with jobs_lock:
        if job_id not in jobs:
            raise KeyError(job_id)
        jobs[job_id].update(updates)
        snapshot = dict(jobs[job_id])
    write_job(snapshot)
    return snapshot


def read_job(job_id: str) -> dict[str, Any] | None:
    with jobs_lock:
        if job_id in jobs:
            return dict(jobs[job_id])
    path = job_path(job_id)
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def run_crawl_job(job_id: str) -> None:
    job = set_job(job_id, status="RUNNING", startedAt=now_iso(), message="采集任务运行中，请在浏览器中正常登录并打开评论区域。")
    try:
        result = collect_jd_reviews(
            product_url=job["productUrl"],
            product_code=job["productCode"],
            category=job["category"],
            output=Path(job["outputPath"]),
            progress_path=Path(job["progressPath"]),
            max_packets=int(job["maxPackets"]),
            wait_seconds=int(job["waitSeconds"]),
        )
        final_status = "WAITING_FOR_HUMAN" if result.status == "NEEDS_HUMAN_VERIFICATION" else "SUCCEEDED"
        set_job(
            job_id,
            status=final_status,
            capturedPackets=result.capturedPackets,
            newReviewCount=result.newReviewCount,
            message=result.message,
            finishedAt=now_iso(),
        )
    except Exception as exc:
        set_job(job_id, status="FAILED", errorMessage=str(exc), message="采集任务失败。", finishedAt=now_iso())


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP", "timestamp": now_iso()}


@app.post("/crawl/start", response_model=CrawlJobResponse, status_code=202)
def start_crawl(request: CrawlStartRequest) -> dict[str, Any]:
    job_id = uuid.uuid4().hex[:12]
    output_path = Path(request.outputPath) if request.outputPath else OUTPUT_DIR / f"raw_reviews_{safe_filename(request.productCode)}.jsonl"
    progress_path = DEFAULT_PROGRESS_DIR / f"jd_{safe_filename(request.productCode)}.json"
    job = {
        "jobId": job_id,
        "status": "QUEUED",
        "productUrl": request.productUrl,
        "productCode": request.productCode,
        "category": request.category,
        "maxPackets": request.maxPackets,
        "waitSeconds": request.waitSeconds,
        "outputMode": request.outputMode,
        "outputPath": str(output_path),
        "progressPath": str(progress_path),
        "capturedPackets": 0,
        "newReviewCount": 0,
        "errorMessage": None,
        "message": "采集任务已入队。请保持浏览器可见，并在出现验证时人工处理或停止。",
        "complianceNotice": COMPLIANCE_NOTICE,
        "createdAt": now_iso(),
        "startedAt": None,
        "finishedAt": None,
    }
    with jobs_lock:
        jobs[job_id] = job
    write_job(job)
    executor.submit(run_crawl_job, job_id)
    return job


@app.get("/crawl/jobs/{job_id}", response_model=CrawlJobResponse)
def get_crawl_job(job_id: str) -> dict[str, Any]:
    job = read_job(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="crawl job not found")
    return job

