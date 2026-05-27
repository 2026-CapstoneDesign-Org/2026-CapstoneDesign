"""Build Spring internal event dispatch candidates without sending HTTP."""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import pathlib
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any

from reservation_result_mapper import (
    ReservationResultMappingContext,
    map_reservation_result_to_spring_event,
)


TIMESTAMP_HEADER = "X-Request-Timestamp"
SIGNATURE_HEADER = "X-Internal-Signature"
CONTENT_TYPE_HEADER = "Content-Type"
CONTENT_TYPE_JSON = "application/json"
SPRING_EVENT_PATH = "/internal/reservations/provider-events/clawops-agent"
SAMPLES_DIR = pathlib.Path(__file__).resolve().parent / "contracts" / "samples"


@dataclass(frozen=True)
class SpringEventDispatchCandidate:
    endpoint_path: str
    payload: dict[str, Any]
    raw_body: str
    headers: dict[str, str]

    def safe_preview(self) -> dict[str, Any]:
        return {
            "ready": True,
            "endpointPath": self.endpoint_path,
            "eventType": self.payload.get("eventType"),
            "providerStatus": self.payload.get("providerStatus"),
            "payload": self.payload,
            "headerNames": sorted(self.headers),
        }


def build_spring_event_dispatch_candidate(
    ai_result: dict[str, Any],
    context: ReservationResultMappingContext,
    signing_key: str,
    timestamp: str | None = None,
) -> SpringEventDispatchCandidate:
    if not signing_key:
        raise ValueError("spring internal signing key is required for dispatch candidate")
    payload = map_reservation_result_to_spring_event(ai_result, context)
    raw_body = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    timestamp_value = timestamp or datetime.now(timezone.utc).replace(microsecond=0).isoformat()
    headers = {
        CONTENT_TYPE_HEADER: CONTENT_TYPE_JSON,
        TIMESTAMP_HEADER: timestamp_value,
        SIGNATURE_HEADER: sign_internal_event(signing_key, timestamp_value, raw_body),
    }
    return SpringEventDispatchCandidate(
        endpoint_path=SPRING_EVENT_PATH,
        payload=payload,
        raw_body=raw_body,
        headers=headers,
    )


def build_dry_run_spring_event_preview(
    call_payload: dict[str, Any],
    ai_result: dict[str, Any],
    signing_key: str,
) -> dict[str, Any]:
    if not signing_key:
        return {
            "ready": False,
            "reason": "SPRING_INTERNAL_SIGNING_KEY_MISSING",
            "endpointPath": SPRING_EVENT_PATH,
        }
    context = ReservationResultMappingContext(
        reservation_id=call_payload.get("reservationId"),
        requested_date_time=call_payload.get("reservationDateTime"),
        requested_party_size=call_payload.get("partySize"),
        sidecar_call_id=f"dry-run-sidecar-{call_payload.get('reservationId')}",
        provider_call_id=None,
    )
    return build_spring_event_dispatch_candidate(ai_result, context, signing_key).safe_preview()


def load_sample_result(sample_name: str | None) -> dict[str, Any]:
    normalized_name = normalize_sample_name(sample_name)
    sample_path = SAMPLES_DIR / normalized_name
    with sample_path.open(encoding="utf-8") as file:
        return json.load(file)


def normalize_sample_name(sample_name: str | None) -> str:
    if not sample_name:
        return "confirmed.json"
    value = sample_name.strip()
    if not value:
        return "confirmed.json"
    if not value.endswith(".json"):
        value = value + ".json"
    allowed = {
        "confirmed.json",
        "unavailable.json",
        "alternative-time.json",
        "name-phone-requested.json",
        "connection-failed.json",
        "staff-did-not-understand.json",
        "ambiguous.json",
    }
    if value not in allowed:
        raise ValueError("unsupported dry-run result sample")
    return value


def sign_internal_event(signing_key: str, timestamp: str, raw_body: str) -> str:
    base_string = f"{timestamp}\n{raw_body}"
    digest = hmac.new(
        signing_key.encode("utf-8"),
        base_string.encode("utf-8"),
        hashlib.sha256,
    ).digest()
    return base64.b64encode(digest).decode("ascii")
