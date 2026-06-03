#!/usr/bin/env python3
"""Run the dev AI-call smoke path from the server.

This helper is for the demo/dev EC2 box. It checks local sidecar readiness,
Spring preflight, and optionally creates one AI-call reservation through Spring.
It never prints JWTs, signing keys, API keys, or raw phone numbers.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import json
import os
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


DEFAULT_CONFIG_DIR = Path("/home/ubuntu/config")
DEFAULT_BASE_URL = "http://127.0.0.1:8080"
DEFAULT_SIDECAR_ENV_FILE = DEFAULT_CONFIG_DIR / "sidecar-real-agent.env"
READINESS_PATH = "/internal/clawops-agent/readiness"
PREFLIGHT_PATH = "/admin/reservations/clawops-real-call/preflight"
TIMESTAMP_HEADER = "X-Request-Timestamp"
SIGNATURE_HEADER = "X-Internal-Signature"


def main() -> int:
    args = parse_args()
    config_dir = Path(args.config_dir)
    sidecar_env = load_env_file(Path(args.sidecar_env_file))
    jwt_secret = get_nested_yaml_value(config_dir / "application-key.yml", "jwt.secret")
    allowed_numbers = split_csv(sidecar_env.get("SIDECAR_ALLOWED_TARGET_NUMBERS", ""))
    sidecar_base_url = args.sidecar_base_url or sidecar_url_from_env(sidecar_env)
    signing_key = sidecar_env.get("SIDECAR_INTERNAL_SIGNING_KEY", "")

    print(f"config.baseUrl={args.base_url.rstrip('/')}")
    print(f"config.userIdConfigured={bool(args.user_id)}")
    print(f"config.restaurantId={args.restaurant_id}")
    print(f"config.jwtSecretConfigured={bool(jwt_secret)}")
    print(f"config.sidecarBaseUrlConfigured={bool(sidecar_base_url)}")
    print(f"config.sidecarSigningKeyConfigured={bool(signing_key)}")
    print(f"config.allowlistCount={len(allowed_numbers)}")

    if not jwt_secret:
        print("abort.reason=JWT_SECRET_MISSING")
        return 2
    if not args.user_id:
        print("abort.reason=USER_ID_REQUIRED")
        return 2
    if len(allowed_numbers) != 1:
        print("abort.reason=ALLOWLIST_COUNT_MUST_BE_ONE")
        return 2

    readiness_ok = check_sidecar_readiness(sidecar_base_url, signing_key)
    preflight_ok = check_spring_preflight(args.base_url.rstrip("/"), jwt_secret, allowed_numbers[0])
    if not readiness_ok or not preflight_ok:
        print("placeCall.skipped=true")
        return 3

    if not args.place_call:
        print("placeCall.skipped=true")
        print("placeCall.skipReason=PLACE_CALL_FLAG_NOT_SET")
        return 0

    reservation_id = create_ai_call_reservation(
        args.base_url.rstrip("/"),
        jwt_secret,
        args.user_id,
        args.restaurant_id,
        args.date,
        args.time,
        args.party_size,
        args.request_note,
    )
    if reservation_id is None:
        return 4
    if args.no_poll:
        return 0
    return poll_reservation(args.base_url.rstrip("/"), jwt_secret, args.user_id, reservation_id, args.poll_timeout_seconds)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default=os.getenv("DEV_SPRING_BASE_URL", DEFAULT_BASE_URL))
    parser.add_argument("--sidecar-base-url", default=os.getenv("DEV_SIDECAR_BASE_URL", ""))
    parser.add_argument("--config-dir", default=os.getenv("DEV_CONFIG_DIR", str(DEFAULT_CONFIG_DIR)))
    parser.add_argument("--sidecar-env-file", default=os.getenv("DEV_SIDECAR_ENV_FILE", str(DEFAULT_SIDECAR_ENV_FILE)))
    parser.add_argument("--user-id", default=os.getenv("DEV_CALL_USER_ID", ""))
    parser.add_argument("--restaurant-id", type=int, default=int(os.getenv("DEV_CALL_RESTAURANT_ID", "9")))
    parser.add_argument("--date", required=True, help="Reservation date, for example 2026-07-07")
    parser.add_argument("--time", required=True, help="Reservation time, for example 20:30:00")
    parser.add_argument("--party-size", type=int, default=int(os.getenv("DEV_CALL_PARTY_SIZE", "2")))
    parser.add_argument("--request-note", default=os.getenv("DEV_CALL_REQUEST_NOTE", "시연 테스트"))
    parser.add_argument("--place-call", action="store_true", help="Actually create one Spring AI-call reservation.")
    parser.add_argument("--no-poll", action="store_true")
    parser.add_argument("--poll-timeout-seconds", type=int, default=240)
    return parser.parse_args()


def load_env_file(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = unquote(value.strip())
    return values


def unquote(value: str) -> str:
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
        return value[1:-1]
    return value


def get_nested_yaml_value(path: Path, dotted_key: str) -> str:
    target = dotted_key.split(".")
    stack: list[tuple[int, str]] = []
    if not path.exists():
        return ""
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or ":" not in stripped:
            continue
        indent = len(line) - len(line.lstrip(" "))
        key, value = stripped.split(":", 1)
        while stack and stack[-1][0] >= indent:
            stack.pop()
        stack.append((indent, key))
        if [item[1] for item in stack] == target:
            return unquote(value.strip())
    return ""


def split_csv(value: str) -> list[str]:
    return [item.strip() for item in value.split(",") if item.strip()]


def sidecar_url_from_env(values: dict[str, str]) -> str:
    bind_host = values.get("SIDECAR_BIND_HOST", "127.0.0.1")
    port = values.get("SIDECAR_PORT", "8091")
    if not bind_host or not port:
        return ""
    return f"http://{bind_host}:{port}"


def check_sidecar_readiness(base_url: str, signing_key: str) -> bool:
    print(f"sidecar.readiness.configured={bool(base_url and signing_key)}")
    if not base_url or not signing_key:
        print("sidecar.readiness.ready=false")
        print("sidecar.readiness.blockReason=CONFIG_MISSING")
        return False
    timestamp = datetime.now(timezone.utc).replace(microsecond=0).isoformat()
    result = request(
        "GET",
        base_url.rstrip("/") + READINESS_PATH,
        headers={
            TIMESTAMP_HEADER: timestamp,
            SIGNATURE_HEADER: sign(signing_key, timestamp, ""),
        },
    )
    data = parse_json(result["body"])
    ready = result["status"] == 200 and bool(data.get("ready"))
    print(f"sidecar.readiness.httpStatus={result['status']}")
    print(f"sidecar.readiness.ready={ready}")
    print(f"sidecar.readiness.runtimeMode={data.get('runtimeMode') or ''}")
    print(f"sidecar.readiness.realAgentEnabled={bool(data.get('realAgentEnabled'))}")
    return ready


def check_spring_preflight(base_url: str, jwt_secret: str, target_phone_number: str) -> bool:
    token = create_jwt(jwt_secret, "1", "ADMIN")
    payload = json.dumps({"targetPhoneNumber": target_phone_number}, separators=(",", ":")).encode("utf-8")
    result = request(
        "POST",
        base_url + PREFLIGHT_PATH,
        body=payload,
        headers={
            "Content-Type": "application/json",
            "Authorization": "Bearer " + token,
        },
    )
    data = parse_json(result["body"])
    block_reasons = data.get("blockReasons") or []
    ok = result["status"] == 200 and bool(data.get("realCallCandidate")) and not block_reasons
    print(f"spring.preflight.httpStatus={result['status']}")
    print(f"spring.preflight.realCallCandidate={bool(data.get('realCallCandidate'))}")
    print(f"spring.preflight.blockReasons={json.dumps(block_reasons, ensure_ascii=False)}")
    print(f"spring.preflight.devProfileActive={bool(data.get('devProfileActive'))}")
    print(f"spring.preflight.prodProfileActive={bool(data.get('prodProfileActive'))}")
    print(f"spring.preflight.schedulerEnabled={bool(data.get('schedulerEnabled'))}")
    print(f"spring.preflight.targetNumberAllowlisted={bool(data.get('targetNumberAllowlisted'))}")
    return ok


def create_ai_call_reservation(
    base_url: str,
    jwt_secret: str,
    user_id: str,
    restaurant_id: int,
    date: str,
    reservation_time: str,
    party_size: int,
    request_note: str,
) -> int | None:
    token = create_jwt(jwt_secret, user_id, "USER")
    payload = {
        "reservationDate": date,
        "reservationTime": reservation_time,
        "partySize": party_size,
        "requestNote": request_note,
    }
    result = request(
        "POST",
        f"{base_url}/restaurants/{restaurant_id}/reservations/ai-call",
        body=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": "Bearer " + token,
        },
        timeout=30,
    )
    data = parse_json(result["body"])
    reservation_id = data.get("id") or data.get("reservationId")
    print(f"reservation.create.httpStatus={result['status']}")
    print(f"reservation.create.created={bool(reservation_id)}")
    print(f"reservation.id={reservation_id or ''}")
    return int(reservation_id) if reservation_id else None


def poll_reservation(base_url: str, jwt_secret: str, user_id: str, reservation_id: int, timeout_seconds: int) -> int:
    token = create_jwt(jwt_secret, user_id, "USER")
    deadline = time.time() + timeout_seconds
    last_status = ""
    while time.time() < deadline:
        time.sleep(2)
        result = request(
            "GET",
            f"{base_url}/reservations/{reservation_id}",
            headers={"Authorization": "Bearer " + token},
            timeout=10,
        )
        data = parse_json(result["body"])
        status = str(data.get("status") or data.get("reservationStatus") or "")
        provider_status = str(data.get("providerStatus") or "")
        if status != last_status:
            print(f"reservation.poll.status={status}")
            print(f"reservation.poll.providerStatus={provider_status}")
            last_status = status
        if status and status not in {"REQUESTED", "CALLING"}:
            print(f"reservation.final.status={status}")
            print(f"reservation.final.providerStatus={provider_status}")
            print(f"reservation.final.resultMessageConfigured={bool(data.get('resultMessage'))}")
            print(f"reservation.final.failureReason={data.get('failureReason') or ''}")
            return 0 if status in {"CONFIRMED", "UNAVAILABLE", "NEEDS_CONFIRMATION", "FAILED", "CANCELLED"} else 5
    print("reservation.final.timeout=true")
    return 5


def create_jwt(secret: str, user_id: str, role: str) -> str:
    now = int(time.time())
    header = {"alg": "HS256", "typ": "JWT"}
    payload = {"sub": str(user_id), "role": role, "type": "access", "iat": now, "exp": now + 900}
    signing_input = b64url(json.dumps(header, separators=(",", ":")).encode("utf-8"))
    signing_input += "." + b64url(json.dumps(payload, separators=(",", ":")).encode("utf-8"))
    signature = hmac.new(secret.encode("utf-8"), signing_input.encode("ascii"), hashlib.sha256).digest()
    return signing_input + "." + b64url(signature)


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def sign(signing_key: str, timestamp: str, raw_body: str) -> str:
    digest = hmac.new(
        signing_key.encode("utf-8"),
        f"{timestamp}\n{raw_body}".encode("utf-8"),
        hashlib.sha256,
    ).digest()
    return base64.b64encode(digest).decode("ascii")


def request(
    method: str,
    url: str,
    body: bytes | None = None,
    headers: dict[str, str] | None = None,
    timeout: int = 8,
) -> dict[str, Any]:
    try:
        request_headers = {"User-Agent": "wagu-dev-ai-call-smoke/1.0"}
        request_headers.update(headers or {})
        req = urllib.request.Request(url, data=body, headers=request_headers, method=method)
        with urllib.request.urlopen(req, timeout=timeout) as response:
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


if __name__ == "__main__":
    raise SystemExit(main())
