#!/usr/bin/env python3
"""Dry-run ClawOps Voice Agent sidecar scaffold.

This server intentionally does not call ClawOps, OpenAI, or any phone provider.
It only exposes local HTTP endpoints for Spring-side contract tests.
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import threading
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer
from typing import Any, Callable

from real_agent_adapter import (
    REQUIRED_SECRET_NAMES,
    RealAgentDependencyContext,
    evaluate_real_agent_gate,
)
from real_agent_interface import RealAgentCallRequest
from real_agent_sdk_runner import RealAgentSdkRunner, check_real_agent_sdk_surface, failed_result
from reservation_result_mapper import ReservationResultMappingContext
from spring_event_dispatch_candidate import (
    SPRING_EVENT_PATH,
    build_dry_run_spring_event_preview,
    build_spring_event_dispatch_candidate,
    load_sample_result,
)


TIMESTAMP_HEADER = "X-Request-Timestamp"
SIGNATURE_HEADER = "X-Internal-Signature"
READINESS_PATH = "/internal/clawops-agent/readiness"
CALLS_PATH = "/internal/clawops-agent/calls"
DEFAULT_ALLOWED_SKEW_SECONDS = 300
RUNTIME_MODE_DRY_RUN = "dry-run"
RUNTIME_MODE_REAL_AGENT = "real-agent"
SUPPORTED_RUNTIME_MODES = {RUNTIME_MODE_DRY_RUN, RUNTIME_MODE_REAL_AGENT}


@dataclass(frozen=True)
class SidecarConfig:
    profile: str
    runtime_mode: str
    real_call_enabled: bool
    real_agent_enabled: bool
    real_agent_approval_granted: bool
    real_agent_approval_required: bool
    require_spring_preflight: bool
    allowed_target_numbers: tuple[str, ...]
    internal_signing_key: str
    bind_host: str
    port: int
    spring_internal_base_url: str
    spring_internal_signing_key: str

    @staticmethod
    def from_env() -> "SidecarConfig":
        return SidecarConfig(
            profile=os.getenv("SIDECAR_PROFILE", "dry-run"),
            runtime_mode=parse_runtime_mode(os.getenv("SIDECAR_RUNTIME_MODE", RUNTIME_MODE_DRY_RUN)),
            real_call_enabled=parse_bool(os.getenv("SIDECAR_REAL_CALL_ENABLED", "false")),
            real_agent_enabled=parse_bool(os.getenv("SIDECAR_REAL_AGENT_ENABLED", "false")),
            real_agent_approval_granted=parse_bool(os.getenv("SIDECAR_REAL_AGENT_APPROVAL_GRANTED", "false")),
            real_agent_approval_required=parse_bool(os.getenv("SIDECAR_REAL_AGENT_APPROVAL_REQUIRED", "true")),
            require_spring_preflight=parse_bool(os.getenv("SIDECAR_REQUIRE_SPRING_PREFLIGHT", "true")),
            allowed_target_numbers=parse_allowed_numbers(os.getenv("SIDECAR_ALLOWED_TARGET_NUMBERS", "")),
            internal_signing_key=os.getenv("SIDECAR_INTERNAL_SIGNING_KEY", ""),
            bind_host=os.getenv("SIDECAR_BIND_HOST", "127.0.0.1"),
            port=int(os.getenv("SIDECAR_PORT", "18080")),
            spring_internal_base_url=os.getenv("SPRING_INTERNAL_BASE_URL", ""),
            spring_internal_signing_key=os.getenv("SPRING_INTERNAL_SIGNING_KEY", ""),
        )


def parse_runtime_mode(value: str | None) -> str:
    normalized = (value or "").strip().lower()
    if normalized in SUPPORTED_RUNTIME_MODES:
        return normalized
    return RUNTIME_MODE_DRY_RUN


def parse_bool(value: str) -> bool:
    return value.strip().lower() in {"1", "true", "yes", "y", "on"}


def parse_allowed_numbers(value: str) -> tuple[str, ...]:
    numbers = []
    for item in value.split(","):
        normalized = normalize_phone(item)
        if normalized:
            numbers.append(normalized)
    return tuple(numbers)


def normalize_phone(value: str | None) -> str:
    if not value:
        return ""
    normalized = "".join(ch for ch in value if ch.isdigit() or ch == "+")
    return normalized


def mask_phone(value: str | None) -> str:
    normalized = normalize_phone(value)
    if not normalized:
        return ""
    if len(normalized) <= 4:
        return "****"
    return "****" + normalized[-4:]


def sign(signing_key: str, timestamp: str, raw_body: str) -> str:
    base_string = f"{timestamp}\n{raw_body}"
    digest = hmac.new(
        signing_key.encode("utf-8"),
        base_string.encode("utf-8"),
        hashlib.sha256,
    ).digest()
    return base64.b64encode(digest).decode("ascii")


def timestamp_allowed(timestamp: str, allowed_skew_seconds: int = DEFAULT_ALLOWED_SKEW_SECONDS) -> bool:
    try:
        parsed = datetime.fromisoformat(timestamp.replace("Z", "+00:00"))
    except ValueError:
        return False
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    drift = abs((datetime.now(timezone.utc) - parsed.astimezone(timezone.utc)).total_seconds())
    return drift <= allowed_skew_seconds


def verify_signature(config: SidecarConfig, headers: Any, raw_body: str) -> bool:
    if not config.internal_signing_key:
        return False
    timestamp = headers.get(TIMESTAMP_HEADER, "")
    provided_signature = headers.get(SIGNATURE_HEADER, "")
    if not timestamp_allowed(timestamp):
        return False
    expected_signature = sign(config.internal_signing_key, timestamp, raw_body)
    return hmac.compare_digest(expected_signature, provided_signature)


def make_handler(
    config: SidecarConfig,
    real_agent_sdk_probe: Callable[[], bool] | None = None,
) -> type[BaseHTTPRequestHandler]:
    class ClawOpsDryRunHandler(BaseHTTPRequestHandler):
        server_version = "ClawOpsDryRunSidecar/0.1"

        def do_GET(self) -> None:
            if self.path != READINESS_PATH:
                self.write_json(404, {"error": "not_found"})
                return
            if config.internal_signing_key and not verify_signature(config, self.headers, ""):
                self.write_json(401, {"error": "signature_invalid"})
                return
            ready = bool(config.internal_signing_key)
            warnings = []
            if not config.internal_signing_key:
                warnings.append("internal_signing_key_missing")
            if config.real_call_enabled and config.runtime_mode == RUNTIME_MODE_DRY_RUN:
                warnings.append("real_call_enabled_not_supported_in_dry_run")
            if not config.require_spring_preflight:
                warnings.append("spring_preflight_required_should_remain_true")
            self.write_json(
                200,
                {
                    "ready": ready,
                    "profile": config.profile,
                    "runtimeMode": config.runtime_mode,
                    "realCallEnabled": config.real_call_enabled,
                    "realAgentEnabled": config.real_agent_enabled,
                    "realAgentApprovalGranted": config.real_agent_approval_granted,
                    "realAgentApprovalRequired": config.real_agent_approval_required,
                    "springPreflightRequired": config.require_spring_preflight,
                    "allowlistCount": len(config.allowed_target_numbers),
                    "clawOpsConfigured": all(name in os.environ for name in ("CLAWOPS_API_KEY", "CLAWOPS_ACCOUNT_ID", "CLAWOPS_FROM_NUMBER")),
                    "openAiConfigured": "OPENAI_API_KEY" in os.environ,
                    "agentRuntime": config.runtime_mode,
                    "warnings": warnings,
                },
            )

        def do_POST(self) -> None:
            if self.path != CALLS_PATH:
                self.write_json(404, {"error": "not_found"})
                return
            raw_body = self.read_body()
            if not verify_signature(config, self.headers, raw_body):
                self.write_json(401, {"error": "signature_invalid"})
                return
            try:
                payload = json.loads(raw_body)
            except json.JSONDecodeError:
                self.write_json(400, {"error": "invalid_json"})
                return
            target_phone = normalize_phone(payload.get("targetPhoneNumber"))
            real_agent_request = build_real_agent_call_request(payload, target_phone)
            if target_phone not in config.allowed_target_numbers:
                self.write_json(403, {"error": "target_not_allowlisted"})
                return
            if payload.get("dryRun") is not True:
                if config.runtime_mode == RUNTIME_MODE_REAL_AGENT and config.real_agent_enabled and config.real_call_enabled:
                    self.handle_real_agent_call(payload, real_agent_request, target_phone, real_agent_sdk_probe)
                    return
                self.write_json(
                    403,
                    {
                        "error": "real_agent_blocked",
                        "realAgentGatePreview": build_real_agent_gate_preview(
                            config,
                            target_phone,
                            real_agent_sdk_probe,
                        ),
                    },
                )
                return
            reservation_id = payload.get("reservationId")
            idempotency_key = payload.get("idempotencyKey") or f"dry-run:{reservation_id}"
            try:
                ai_result = load_sample_result(payload.get("dryRunResultSample"))
                spring_event_preview = build_dry_run_spring_event_preview(
                    payload,
                    ai_result,
                    config.spring_internal_signing_key,
                )
                real_agent_gate_preview = build_real_agent_gate_preview(
                    config,
                    target_phone,
                    real_agent_sdk_probe,
                )
            except ValueError as exception:
                self.write_json(400, {"error": str(exception)})
                return
            self.write_json(
                200,
                {
                    "accepted": True,
                    "provider": "CLAWOPS_SIDECAR",
                    "providerCallId": f"dry-run-provider-call-{reservation_id}",
                    "providerStatus": "DRY_RUN_ACCEPTED",
                    "sidecarCallId": f"dry-run-sidecar-{reservation_id}",
                    "idempotencyKey": idempotency_key,
                    "message": f"dry-run accepted for {mask_phone(target_phone)}",
                    "springEventPreview": spring_event_preview,
                    "realAgentGatePreview": real_agent_gate_preview,
                },
            )

        def handle_real_agent_call(
            self,
            payload: dict[str, Any],
            real_agent_request: RealAgentCallRequest,
            target_phone: str,
            real_agent_sdk_probe: Callable[[], bool] | None,
        ) -> None:
            gate_result = evaluate_real_agent_gate(
                RealAgentDependencyContext(
                    runtime_mode=config.runtime_mode,
                    real_agent_enabled=config.real_agent_enabled,
                    approval_required=config.real_agent_approval_required,
                    approval_granted=config.real_agent_approval_granted,
                    spring_preflight_required=config.require_spring_preflight,
                    spring_preflight_passed=payload.get("springPreflightPassed") is True,
                    target_phone_number=target_phone,
                    allowed_target_numbers=config.allowed_target_numbers,
                    present_secret_names=present_env_names(REQUIRED_SECRET_NAMES),
                ),
                sdk_probe=real_agent_sdk_probe or (lambda: check_real_agent_sdk_surface().ready),
                implementation_ready=True,
            )
            if not gate_result.allowed:
                self.write_json(
                    403,
                    {
                        "error": "real_agent_blocked",
                        "realAgentGatePreview": gate_preview_from_result(config, gate_result),
                    },
                )
                return
            sidecar_call_id = real_agent_request.sidecar_call_id
            threading.Thread(
                target=run_real_agent_worker,
                args=(config, real_agent_request, gate_result),
                daemon=True,
            ).start()
            self.write_json(
                202,
                {
                    "accepted": True,
                    "provider": "CLAWOPS_SIDECAR",
                    "providerCallId": sidecar_call_id,
                    "providerStatus": "CLAWOPS_SIDECAR_REAL_AGENT_ACCEPTED",
                    "sidecarCallId": sidecar_call_id,
                    "idempotencyKey": payload.get("idempotencyKey") or f"clawops-sidecar-real-agent:{real_agent_request.reservation_id}",
                    "message": f"real-agent accepted for {mask_phone(target_phone)}",
                    "realAgentGatePreview": gate_preview_from_result(config, gate_result),
                },
            )

        def read_body(self) -> str:
            length = int(self.headers.get("Content-Length", "0"))
            return self.rfile.read(length).decode("utf-8") if length else ""

        def write_json(self, status: int, payload: dict[str, Any]) -> None:
            body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, format: str, *args: Any) -> None:
            return

    return ClawOpsDryRunHandler


def build_real_agent_gate_preview(
    config: SidecarConfig,
    target_phone: str,
    sdk_probe: Callable[[], bool] | None = None,
) -> dict[str, Any]:
    gate_result = evaluate_real_agent_gate(
        RealAgentDependencyContext(
            runtime_mode=config.runtime_mode,
            real_agent_enabled=config.real_agent_enabled,
            approval_required=config.real_agent_approval_required,
            approval_granted=config.real_agent_approval_granted,
            spring_preflight_required=config.require_spring_preflight,
            spring_preflight_passed=False,
            target_phone_number=target_phone,
            allowed_target_numbers=config.allowed_target_numbers,
            present_secret_names=present_env_names(REQUIRED_SECRET_NAMES),
        ),
        sdk_probe=sdk_probe,
    )
    return gate_preview_from_result(config, gate_result)


def gate_preview_from_result(config: SidecarConfig, gate_result) -> dict[str, Any]:
    return {
        "runtimeMode": config.runtime_mode,
        "realAgentEnabled": config.real_agent_enabled,
        "approvalRequired": config.real_agent_approval_required,
        "approvalGranted": config.real_agent_approval_granted,
        "springPreflightRequired": config.require_spring_preflight,
        "allowed": gate_result.allowed,
        "blocked": not gate_result.allowed,
        "blockReasons": list(gate_result.block_reasons),
        "missingRequiredEnvNames": list(gate_result.missing_secret_names),
        "sdkInstalled": gate_result.sdk_installed,
    }


def build_real_agent_call_request(payload: dict[str, Any], target_phone: str) -> RealAgentCallRequest:
    reservation_id = payload.get("reservationId")
    sidecar_call_id = f"sidecar-real-agent-{reservation_id}"
    return RealAgentCallRequest(
        reservation_id=safe_int(reservation_id, 0),
        sidecar_call_id=sidecar_call_id if payload.get("dryRun") is not True else f"dry-run-sidecar-{reservation_id}",
        target_phone_number=target_phone,
        target_phone_mask=mask_phone(target_phone),
        restaurant_name=str(payload.get("restaurantName") or ""),
        reservation_date_time=str(payload.get("reservationDateTime") or ""),
        party_size=safe_int(payload.get("partySize"), 1),
        request_note=payload.get("requestNote"),
        reservation_name_available=True,
        reservation_contact_available=True,
        reservation_name=str(payload.get("reservationName") or os.getenv("SIDECAR_RESERVATION_NAME", "테스트 예약자")),
        reservation_contact_number=str(payload.get("reservationContactNumber") or os.getenv("SIDECAR_RESERVATION_CONTACT_NUMBER", target_phone)),
    )


def safe_int(value: Any, default: int) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def present_env_names(names: tuple[str, ...]) -> tuple[str, ...]:
    return tuple(name for name in names if name in os.environ)


def run_real_agent_worker(
    config: SidecarConfig,
    request: RealAgentCallRequest,
    gate_result,
) -> None:
    try:
        ai_result = RealAgentSdkRunner(
            gate_result,
            implementation_enabled=True,
        ).run_reservation_call(request).ai_result
    except Exception:
        ai_result = failed_result(
            "PROVIDER_FATAL_ERROR",
            "Real-agent worker failed before delivering a reservation result.",
        )
    context = ReservationResultMappingContext(
        reservation_id=request.reservation_id,
        requested_date_time=request.reservation_date_time,
        requested_party_size=request.party_size,
        sidecar_call_id=request.sidecar_call_id,
        provider_call_id=request.sidecar_call_id,
    )
    candidate = build_spring_event_dispatch_candidate(
        ai_result,
        context,
        config.spring_internal_signing_key,
    )
    dispatch_spring_event(config, candidate)


def dispatch_spring_event(config: SidecarConfig, candidate) -> None:
    if not config.spring_internal_base_url:
        return
    url = config.spring_internal_base_url.rstrip("/") + SPRING_EVENT_PATH
    request = urllib.request.Request(
        url,
        data=candidate.raw_body.encode("utf-8"),
        headers=candidate.headers,
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            response.read()
    except (urllib.error.URLError, TimeoutError):
        return


def run() -> None:
    config = SidecarConfig.from_env()
    server = HTTPServer((config.bind_host, config.port), make_handler(config))
    print(f"clawops dry-run sidecar listening on {config.bind_host}:{config.port}")
    server.serve_forever()


if __name__ == "__main__":
    run()
