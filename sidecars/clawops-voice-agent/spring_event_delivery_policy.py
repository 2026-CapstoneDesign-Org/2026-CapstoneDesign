"""Local-only Spring event delivery policy candidates.

This module does not send HTTP requests, start retries, run background workers,
or access queues. It only classifies fake transport outcomes for tests.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any


ACKED = "ACKED"
DUPLICATE_ACKED = "DUPLICATE_ACKED"
RETRYABLE_FAILURE = "RETRYABLE_FAILURE"
NON_RETRYABLE_FAILURE = "NON_RETRYABLE_FAILURE"
NETWORK_ERROR = "NETWORK_ERROR"
TIMEOUT = "TIMEOUT"


@dataclass(frozen=True)
class FakeSpringEventTransportResult:
    http_status: int | None = None
    error_type: str | None = None
    duplicate: bool = False


@dataclass(frozen=True)
class SpringEventDeliveryDecision:
    delivered: bool
    ack_status: str
    retryable: bool
    failure_reason: str | None = None


def classify_fake_delivery_result(
    transport_result: FakeSpringEventTransportResult,
) -> SpringEventDeliveryDecision:
    if transport_result.duplicate:
        return SpringEventDeliveryDecision(True, DUPLICATE_ACKED, False)
    if transport_result.error_type in {NETWORK_ERROR, TIMEOUT}:
        return SpringEventDeliveryDecision(
            False,
            RETRYABLE_FAILURE,
            True,
            transport_result.error_type,
        )
    status = transport_result.http_status
    if status is not None and 200 <= status < 300:
        return SpringEventDeliveryDecision(True, ACKED, False)
    if status is not None and 400 <= status < 500:
        return SpringEventDeliveryDecision(False, NON_RETRYABLE_FAILURE, False, f"HTTP_{status}")
    if status is not None and status >= 500:
        return SpringEventDeliveryDecision(False, RETRYABLE_FAILURE, True, f"HTTP_{status}")
    return SpringEventDeliveryDecision(False, NON_RETRYABLE_FAILURE, False, "NO_TRANSPORT_RESULT")


def build_fake_delivery_preview(dispatch_candidate: Any, transport_result: FakeSpringEventTransportResult) -> dict[str, Any]:
    decision = classify_fake_delivery_result(transport_result)
    payload = dispatch_candidate.payload
    return {
        "endpointPath": dispatch_candidate.endpoint_path,
        "eventType": payload.get("eventType"),
        "idempotencyKey": payload.get("idempotencyKey"),
        "ackStatus": decision.ack_status,
        "delivered": decision.delivered,
        "retryable": decision.retryable,
        "failureReason": decision.failure_reason,
    }
