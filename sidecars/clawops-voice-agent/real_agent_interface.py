"""Real-agent interface boundary for future ClawOps/OpenAI wiring.

This module intentionally imports no ClawOps or OpenAI SDKs. It defines the
shape a later real Voice Agent runner must implement, plus a fake runner for
local contract tests.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Protocol

from spring_event_dispatch_candidate import load_sample_result


FAKE_OUTCOME_SAMPLE_BY_STATUS = {
    "CONFIRMED": "confirmed.json",
    "UNAVAILABLE": "unavailable.json",
    "NEEDS_CONFIRMATION": "alternative-time.json",
    "FAILED": "connection-failed.json",
}


@dataclass(frozen=True)
class RealAgentCallRequest:
    reservation_id: int
    sidecar_call_id: str
    target_phone_number: str = field(repr=False)
    target_phone_mask: str = ""
    restaurant_name: str = ""
    reservation_date_time: str = ""
    party_size: int = 1
    request_note: str | None = None
    reservation_name_available: bool = True
    reservation_contact_available: bool = True
    reservation_name: str = ""
    reservation_contact_number: str = field(default="", repr=False)


@dataclass(frozen=True)
class RealAgentCallResult:
    ai_result: dict[str, Any]
    sidecar_call_id: str
    provider_call_id: str | None
    failure_reason: str | None
    retryable: bool


class RealAgentRunner(Protocol):
    def run_reservation_call(self, request: RealAgentCallRequest) -> RealAgentCallResult:
        """Return a Voice Agent reservation result schema payload."""


class FakeRealAgentRunner:
    """SDK-free fake runner used only for local tests."""

    def __init__(self, result_status: str) -> None:
        if result_status not in FAKE_OUTCOME_SAMPLE_BY_STATUS:
            raise ValueError("unsupported fake real-agent result status")
        self.result_status = result_status

    def run_reservation_call(self, request: RealAgentCallRequest) -> RealAgentCallResult:
        ai_result = load_sample_result(FAKE_OUTCOME_SAMPLE_BY_STATUS[self.result_status])
        return RealAgentCallResult(
            ai_result=ai_result,
            sidecar_call_id=request.sidecar_call_id,
            provider_call_id=f"fake-provider-call-{request.reservation_id}",
            failure_reason=ai_result.get("failureReason"),
            retryable=ai_result.get("resultStatus") == "FAILED",
        )
