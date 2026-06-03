"""Map Voice Agent result schema payloads to Spring internal event requests.

This module is dry-run safe. It does not call Spring, ClawOps, OpenAI, or any
phone provider. The returned dict is the payload shape expected by Spring's
`ClawOpsAgentProviderEventRequest`.
"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from datetime import datetime
from typing import Any


PROVIDER = "CLAWOPS_SIDECAR"
REQUIRED_FIELDS = {
    "resultStatus",
    "summary",
    "confirmedDateTime",
    "partySize",
    "reservationNameProvided",
    "phoneNumberProvided",
    "restaurantRequestedNameOrPhone",
    "alternativeTimeSuggested",
    "alternativeDateTime",
    "failureReason",
    "transcriptSummary",
}
RESULT_STATUSES = {"CONFIRMED", "UNAVAILABLE", "NEEDS_CONFIRMATION", "FAILED"}
SPRING_EVENT_BY_STATUS = {
    "CONFIRMED": "RESERVATION_CONFIRMED",
    "UNAVAILABLE": "RESERVATION_UNAVAILABLE",
    "NEEDS_CONFIRMATION": "RESERVATION_NEEDS_CONFIRMATION",
}
FAILURE_EVENT_BY_REASON = {
    "AI_RESULT_TOOL_MISSING": "AI_PARSE_FAILED",
    "CALL_CONNECTION_FAILED": "CALL_CONNECTION_FAILED",
    "CALL_NO_ANSWER": "CALL_NO_ANSWER",
    "CALL_BUSY": "CALL_BUSY",
    "PROVIDER_FATAL_ERROR": "PROVIDER_FATAL_ERROR",
}
PROVIDER_FATAL_ERROR_PREFIX = "PROVIDER_FATAL_ERROR__"


@dataclass(frozen=True)
class ReservationResultMappingContext:
    reservation_id: int
    requested_date_time: str
    requested_party_size: int
    sidecar_call_id: str
    provider_call_id: str | None = None
    occurred_at: str | None = None


def map_reservation_result_to_spring_event(
    ai_result: dict[str, Any],
    context: ReservationResultMappingContext,
) -> dict[str, Any]:
    validation_error = validate_ai_result(ai_result)
    if validation_error:
        return parse_failed_event(context, validation_error, ai_result)

    conflict_reason = confirmed_result_conflict(ai_result, context)
    if conflict_reason:
        return needs_confirmation_event(context, ai_result, conflict_reason, "AI_RESULT_CONFLICT")

    result_status = ai_result["resultStatus"]
    if result_status == "CONFIRMED":
        return standard_event(context, ai_result, "RESERVATION_CONFIRMED", "AI_CONFIRMED")
    if result_status == "UNAVAILABLE":
        return standard_event(context, ai_result, "RESERVATION_UNAVAILABLE", "AI_UNAVAILABLE")
    if result_status == "NEEDS_CONFIRMATION":
        provider_status = "AI_NEEDS_CONFIRMATION_ALTERNATIVE_TIME" if ai_result["alternativeTimeSuggested"] \
            else "AI_NEEDS_CONFIRMATION"
        return standard_event(context, ai_result, "RESERVATION_NEEDS_CONFIRMATION", provider_status)

    failure_reason = ai_result["failureReason"] or "AI_RESULT_FAILED"
    event_type = failure_event_type(failure_reason)
    return standard_event(context, ai_result, event_type, "AI_FAILED", failure_reason=failure_reason)


def validate_ai_result(payload: dict[str, Any]) -> str | None:
    if not isinstance(payload, dict):
        return "AI_RESULT_NOT_OBJECT"
    keys = set(payload)
    if keys != REQUIRED_FIELDS:
        return "AI_RESULT_SCHEMA_FIELDS_INVALID"
    if payload["resultStatus"] not in RESULT_STATUSES:
        return "AI_RESULT_STATUS_INVALID"
    if not isinstance(payload["summary"], str) or not payload["summary"].strip():
        return "AI_RESULT_SUMMARY_INVALID"
    for key in (
        "reservationNameProvided",
        "phoneNumberProvided",
        "restaurantRequestedNameOrPhone",
        "alternativeTimeSuggested",
    ):
        if not isinstance(payload[key], bool):
            return "AI_RESULT_BOOLEAN_FIELD_INVALID"
    if payload["partySize"] is not None:
        if not isinstance(payload["partySize"], int) or not 1 <= payload["partySize"] <= 20:
            return "AI_RESULT_PARTY_SIZE_INVALID"
    if payload["resultStatus"] == "CONFIRMED":
        if not payload["confirmedDateTime"]:
            return "AI_RESULT_CONFIRMED_DATETIME_REQUIRED"
        if payload["alternativeTimeSuggested"]:
            return "AI_RESULT_CONFIRMED_WITH_ALTERNATIVE_INVALID"
    if payload["resultStatus"] == "NEEDS_CONFIRMATION":
        if payload["confirmedDateTime"] is not None:
            return "AI_RESULT_NEEDS_CONFIRMATION_CONFIRMED_DATETIME_INVALID"
        if payload["alternativeTimeSuggested"] and payload["alternativeDateTime"] is None:
            return "AI_RESULT_ALTERNATIVE_DATETIME_REQUIRED"
    if payload["resultStatus"] == "FAILED" and not payload["failureReason"]:
        return "AI_RESULT_FAILURE_REASON_REQUIRED"
    return None


def confirmed_result_conflict(
    payload: dict[str, Any],
    context: ReservationResultMappingContext,
) -> str | None:
    if payload["resultStatus"] != "CONFIRMED":
        return None
    if payload["confirmedDateTime"] != context.requested_date_time:
        return "AI_RESULT_CONFIRMED_DATETIME_CONFLICT"
    if payload["partySize"] != context.requested_party_size:
        return "AI_RESULT_CONFIRMED_PARTY_SIZE_CONFLICT"
    return None


def parse_failed_event(
    context: ReservationResultMappingContext,
    failure_reason: str,
    payload: dict[str, Any],
) -> dict[str, Any]:
    result_message = public_result_message(context, payload, failure_reason=failure_reason)
    return base_event(
        context=context,
        payload=payload,
        event_type="AI_PARSE_FAILED",
        provider_status="AI_PARSE_FAILED",
        retryable=False,
        failure_reason=failure_reason,
        ai_summary=result_message,
        result_message=result_message,
    )


def failure_event_type(failure_reason: str) -> str:
    if failure_reason.startswith(PROVIDER_FATAL_ERROR_PREFIX):
        return "PROVIDER_FATAL_ERROR"
    return FAILURE_EVENT_BY_REASON.get(failure_reason, "PROVIDER_TRANSIENT_ERROR")


def needs_confirmation_event(
    context: ReservationResultMappingContext,
    payload: dict[str, Any],
    failure_reason: str,
    provider_status: str,
) -> dict[str, Any]:
    result_message = public_result_message(context, payload, failure_reason=failure_reason)
    return base_event(
        context=context,
        payload=payload,
        event_type="RESERVATION_NEEDS_CONFIRMATION",
        provider_status=provider_status,
        retryable=False,
        failure_reason=failure_reason,
        ai_summary=result_message,
        result_message=result_message,
    )


def standard_event(
    context: ReservationResultMappingContext,
    payload: dict[str, Any],
    event_type: str,
    provider_status: str,
    failure_reason: str | None = None,
) -> dict[str, Any]:
    result_message = public_result_message(context, payload, failure_reason=failure_reason)
    return base_event(
        context=context,
        payload=payload,
        event_type=event_type,
        provider_status=provider_status,
        retryable=event_type in {"CALL_CONNECTION_FAILED", "CALL_NO_ANSWER", "CALL_BUSY", "PROVIDER_TRANSIENT_ERROR"},
        failure_reason=failure_reason,
        ai_summary=result_message,
        result_message=result_message,
    )


def public_result_message(
    context: ReservationResultMappingContext,
    payload: dict[str, Any],
    failure_reason: str | None = None,
) -> str:
    requested_time = display_public_datetime(context.requested_date_time)
    requested_party_size = context.requested_party_size
    result_status = payload.get("resultStatus")

    if failure_reason:
        if failure_reason.startswith("AI_RESULT_CONFIRMED_"):
            return f"{requested_time} 예약 결과 확인 필요"
        if failure_reason.startswith("AI_RESULT_"):
            return "전화 예약 결과 확인 실패"

    if result_status == "CONFIRMED":
        confirmed_time = display_public_datetime(payload.get("confirmedDateTime") or context.requested_date_time)
        party_size = payload.get("partySize") or requested_party_size
        return f"{confirmed_time} {party_size}명 예약 성공"
    if result_status == "UNAVAILABLE":
        party_size = payload.get("partySize") or requested_party_size
        return f"{requested_time} {party_size}명 예약 불가"
    if result_status == "NEEDS_CONFIRMATION":
        if payload.get("alternativeTimeSuggested") and payload.get("alternativeDateTime"):
            return f"{display_public_datetime(payload.get('alternativeDateTime'))} 대체 시간 제안받음"
        return f"{requested_time} 예약 확인 필요"
    if result_status == "FAILED":
        return "전화 예약 실패"
    return "전화 예약 결과 확인 실패"


def display_public_datetime(value: Any) -> str:
    if not isinstance(value, str) or not value:
        return "요청 시간"
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return value
    meridiem = "오전" if parsed.hour < 12 else "오후"
    hour = parsed.hour % 12 or 12
    return f"{parsed.month}월 {parsed.day}일 {meridiem} {hour}시 {parsed.minute:02d}분"


def base_event(
    context: ReservationResultMappingContext,
    payload: dict[str, Any],
    event_type: str,
    provider_status: str,
    retryable: bool,
    failure_reason: str | None,
    ai_summary: str | None,
    result_message: str | None,
) -> dict[str, Any]:
    payload_hash = stable_hash(payload)
    return {
        "provider": PROVIDER,
        "reservationId": context.reservation_id,
        "providerCallId": context.provider_call_id,
        "sidecarCallId": context.sidecar_call_id,
        "eventType": event_type,
        "providerStatus": provider_status,
        "occurredAt": context.occurred_at or datetime.now().replace(microsecond=0).isoformat(),
        "retryable": retryable,
        "failureReason": failure_reason,
        "aiSummary": ai_summary,
        "resultMessage": result_message,
        "idempotencyKey": f"{context.sidecar_call_id}:{event_type}:{payload_hash[:16]}",
        "rawPayloadHash": payload_hash,
    }


def stable_hash(payload: dict[str, Any]) -> str:
    serialized = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(serialized.encode("utf-8")).hexdigest()
