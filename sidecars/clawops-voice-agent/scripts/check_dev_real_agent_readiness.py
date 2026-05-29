#!/usr/bin/env python3
"""Check dev real-agent readiness without starting calls.

This script is diagnostic only. It never calls ClawOps/OpenAI, never starts a
Voice Agent, and never places phone calls. It prints booleans, HTTP statuses,
and block reason names only; it does not print bearer tokens, signing keys, or
raw phone numbers.
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import urllib.error
import urllib.request
from datetime import datetime, timezone
from typing import Any


DEFAULT_BASE_URL = "https://wagu.uk"
INBOUND_PATH = "/webhooks/reservations/call-providers/clawops/inbound"
SWAGGER_PATH = "/swagger-ui/index.html"
PREFLIGHT_PATH = "/admin/reservations/clawops-real-call/preflight"
SIDECAR_READINESS_PATH = "/internal/clawops-agent/readiness"
TIMESTAMP_HEADER = "X-Request-Timestamp"
SIGNATURE_HEADER = "X-Internal-Signature"


def main() -> int:
    base_url = os.getenv("DEV_BASE_URL", DEFAULT_BASE_URL).rstrip("/")
    print(f"baseUrl={base_url}")
    check_public_endpoint(base_url, SWAGGER_PATH, "swagger")
    check_public_endpoint(base_url, INBOUND_PATH, "clawopsInbound")
    check_spring_preflight(base_url)
    check_local_sidecar_readiness()
    return 0


def check_public_endpoint(base_url: str, path: str, label: str) -> None:
    result = request("GET", base_url + path)
    print(f"{label}.httpStatus={result['status']}")
    print(f"{label}.reachable={200 <= result['status'] < 400}")


def check_spring_preflight(base_url: str) -> None:
    token = os.getenv("DEV_ADMIN_BEARER_TOKEN", "")
    target = os.getenv("DEV_PREFLIGHT_TARGET_PHONE_NUMBER", "")
    print(f"springPreflight.tokenConfigured={bool(token)}")
    print(f"springPreflight.targetConfigured={bool(target)}")
    if not token or not target:
        print("springPreflight.skipped=true")
        print("springPreflight.skipReason=TOKEN_OR_TARGET_MISSING")
        return

    payload = json.dumps({"targetPhoneNumber": target}, separators=(",", ":")).encode("utf-8")
    result = request(
        "POST",
        base_url + PREFLIGHT_PATH,
        body=payload,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {token}",
        },
    )
    print(f"springPreflight.httpStatus={result['status']}")
    if result["status"] != 200:
        print("springPreflight.realCallCandidate=false")
        print("springPreflight.blockReasons=[HTTP_STATUS_NOT_OK]")
        return
    data = parse_json(result["body"])
    print(f"springPreflight.realCallCandidate={bool(data.get('realCallCandidate'))}")
    print(f"springPreflight.blockReasons={json.dumps(data.get('blockReasons') or [])}")
    print(f"springPreflight.devProfileActive={bool(data.get('devProfileActive'))}")
    print(f"springPreflight.prodProfileActive={bool(data.get('prodProfileActive'))}")
    print(f"springPreflight.callingEnabled={bool(data.get('callingEnabled'))}")
    print(f"springPreflight.realCallEnabled={bool(data.get('realCallEnabled'))}")
    print(f"springPreflight.schedulerEnabled={bool(data.get('schedulerEnabled'))}")
    print(f"springPreflight.allowlistPresent={bool(data.get('allowlistPresent'))}")
    print(f"springPreflight.targetNumberAllowlisted={bool(data.get('targetNumberAllowlisted'))}")


def check_local_sidecar_readiness() -> None:
    base_url = os.getenv("SIDECAR_BASE_URL", os.getenv("CLAWOPS_SIDECAR_BASE_URL", "")).rstrip("/")
    signing_key = os.getenv("SIDECAR_INTERNAL_SIGNING_KEY", os.getenv("CLAWOPS_SIDECAR_INTERNAL_SIGNING_KEY", ""))
    print(f"sidecar.baseUrlConfigured={bool(base_url)}")
    print(f"sidecar.signingKeyConfigured={bool(signing_key)}")
    if not base_url or not signing_key:
        print("sidecar.readiness.skipped=true")
        print("sidecar.readiness.skipReason=BASE_URL_OR_SIGNING_KEY_MISSING")
        return

    timestamp = datetime.now(timezone.utc).replace(microsecond=0).isoformat()
    result = request(
        "GET",
        base_url + SIDECAR_READINESS_PATH,
        headers={
            TIMESTAMP_HEADER: timestamp,
            SIGNATURE_HEADER: sign(signing_key, timestamp, ""),
        },
    )
    print(f"sidecar.readiness.httpStatus={result['status']}")
    data = parse_json(result["body"])
    print(f"sidecar.readiness.ready={bool(data.get('ready'))}")
    print(f"sidecar.readiness.runtimeMode={data.get('runtimeMode') or ''}")
    print(f"sidecar.readiness.realCallEnabled={bool(data.get('realCallEnabled'))}")
    print(f"sidecar.readiness.realAgentEnabled={bool(data.get('realAgentEnabled'))}")
    print(f"sidecar.readiness.springPreflightRequired={bool(data.get('springPreflightRequired'))}")


def request(
    method: str,
    url: str,
    body: bytes | None = None,
    headers: dict[str, str] | None = None,
) -> dict[str, Any]:
    try:
        request_headers = {"User-Agent": "wagu-dev-readiness-check/1.0"}
        request_headers.update(headers or {})
        req = urllib.request.Request(url, data=body, headers=request_headers, method=method)
        with urllib.request.urlopen(req, timeout=8) as response:
            return {"status": response.status, "body": response.read().decode("utf-8", errors="replace")}
    except urllib.error.HTTPError as error:
        return {"status": error.code, "body": error.read().decode("utf-8", errors="replace")}
    except urllib.error.URLError as error:
        return {"status": 0, "body": error.__class__.__name__}


def parse_json(value: str) -> dict[str, Any]:
    try:
        parsed = json.loads(value)
    except json.JSONDecodeError:
        return {}
    return parsed if isinstance(parsed, dict) else {}


def sign(signing_key: str, timestamp: str, raw_body: str) -> str:
    base_string = f"{timestamp}\n{raw_body}"
    digest = hmac.new(signing_key.encode("utf-8"), base_string.encode("utf-8"), hashlib.sha256).digest()
    return base64.b64encode(digest).decode("ascii")


if __name__ == "__main__":
    raise SystemExit(main())
