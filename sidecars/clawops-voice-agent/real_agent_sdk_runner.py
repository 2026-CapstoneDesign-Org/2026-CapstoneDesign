"""SDK-backed real-agent runner skeleton.

This module intentionally performs no top-level ClawOps/OpenAI imports. The
future SDK imports are isolated behind an explicit helper, and the runner still
hard-stops before creating SDK objects, starting a Voice Agent, or making calls.
"""

from __future__ import annotations

import importlib
import importlib.util
import asyncio
import json
import os
import pathlib
import re
from dataclasses import dataclass, field
from typing import Any, Callable

from real_agent_adapter import REAL_AGENT_NOT_IMPLEMENTED, RealAgentBlockedError, RealAgentGateResult
from real_agent_interface import RealAgentCallRequest, RealAgentCallResult


SDK_MODULE_NAMES = ("clawops", "openai")
SDK_NOT_READY = "SDK_NOT_READY"
DEFAULT_PROMPT_PATH = pathlib.Path(__file__).resolve().parent / "prompts" / "reservation_agent_prompt.md"
WAIT_RESULT_TOOL_SUBMITTED = "RESULT_TOOL_SUBMITTED"
WAIT_CALL_ENDED = "CALL_ENDED"
MIN_SECONDS_BEFORE_FINAL_RESULT = 45.0
MIN_SECONDS_BEFORE_AI_FAILED_RESULT = 45.0
POST_RESULT_CLOSING_GRACE_SECONDS = 6.0


@dataclass(frozen=True)
class RealAgentSdkModules:
    clawops: Any
    openai: Any


@dataclass(frozen=True)
class RealAgentSdkSurfaceStatus:
    clawops_agent_importable: bool
    openai_realtime_importable: bool
    websockets_installed: bool
    missing: tuple[str, ...] = ()

    @property
    def ready(self) -> bool:
        return self.clawops_agent_importable and self.openai_realtime_importable and self.websockets_installed


@dataclass(frozen=True)
class RealAgentExecutionSkeleton:
    system_prompt: str
    session_config: dict[str, Any]
    agent_config: dict[str, Any]
    result_tool: dict[str, Any]
    call_config: dict[str, Any]
    result_wait_config: dict[str, Any]
    disconnect_config: dict[str, Any]
    target_phone_number: str = field(repr=False)


def load_real_agent_sdk_modules(
    importer: Callable[[str], Any] = importlib.import_module,
) -> RealAgentSdkModules:
    """Lazy import boundary for the later approved real-agent implementation."""

    modules = {name: importer(name) for name in SDK_MODULE_NAMES}
    return RealAgentSdkModules(clawops=modules["clawops"], openai=modules["openai"])


def check_real_agent_sdk_surface(
    importer: Callable[[str], Any] = importlib.import_module,
    module_finder: Callable[[str], Any] = importlib.util.find_spec,
) -> RealAgentSdkSurfaceStatus:
    """Check future SDK symbols without creating clients or starting agents."""

    missing: list[str] = []
    clawops_agent_importable = import_has_attrs(
        importer,
        "clawops.agent",
        ("ClawOpsAgent", "OpenAIRealtime", "BuiltinTool"),
        missing,
    )
    openai_realtime_importable = import_has_attrs(
        importer,
        "openai.resources.realtime.realtime",
        ("AsyncRealtimeConnection",),
        missing,
    )
    websockets_installed = module_finder("websockets") is not None
    if not websockets_installed:
        missing.append("websockets")
    return RealAgentSdkSurfaceStatus(
        clawops_agent_importable=clawops_agent_importable,
        openai_realtime_importable=openai_realtime_importable,
        websockets_installed=websockets_installed,
        missing=tuple(dict.fromkeys(missing)),
    )


def build_real_agent_execution_skeleton(
    request: RealAgentCallRequest,
    prompt_loader: Callable[[], str] | None = None,
) -> RealAgentExecutionSkeleton:
    """Build a pure execution plan without constructing SDK clients or sessions."""

    system_prompt = build_prompt_for_request(
        (prompt_loader or load_reservation_agent_prompt)(),
        request,
    )
    return RealAgentExecutionSkeleton(
        system_prompt=system_prompt,
        session_config=build_realtime_session_config(system_prompt),
        agent_config=build_agent_config(),
        result_tool=build_result_tool_spec(),
        call_config=build_call_config(request),
        result_wait_config=build_result_wait_config(),
        disconnect_config=build_disconnect_config(),
        target_phone_number=request.target_phone_number,
    )


def load_reservation_agent_prompt(path: pathlib.Path = DEFAULT_PROMPT_PATH) -> str:
    return path.read_text(encoding="utf-8")


def build_prompt_for_request(base_prompt: str, request: RealAgentCallRequest) -> str:
    return "\n\n".join([
        base_prompt,
        "## Current Reservation Request",
        f"- Restaurant name: {request.restaurant_name}",
        f"- Requested date-time: {request.reservation_date_time}",
        f"- Party size: {request.party_size}",
        f"- Request note: {request.request_note or ''}",
        f"- Reservation name: {request.reservation_name or '테스트 예약자'}",
        f"- Reservation contact: {request.reservation_contact_number or '테스트 연락처'}",
        "- Do not change the requested date-time or party size.",
        "- Call `submit_reservation_call_result` exactly once after the restaurant clearly answers.",
        "- After the result tool is accepted, say one short closing sentence before the call ends.",
    ])


def build_realtime_session_config(system_prompt: str) -> dict[str, Any]:
    return {
        "className": "OpenAIRealtime",
        "model": "gpt-realtime-2",
        "voice": "marin",
        "language": "ko",
        "greeting": True,
        "systemPrompt": system_prompt,
    }


def build_agent_config() -> dict[str, Any]:
    return {
        "className": "ClawOpsAgent",
        "apiKeyEnvName": "CLAWOPS_API_KEY",
        "accountIdEnvName": "CLAWOPS_ACCOUNT_ID",
        "fromNumberEnvName": "CLAWOPS_FROM_NUMBER",
        "recording": False,
        "builtinTools": "SEND_DTMF",
    }


def build_result_tool_spec() -> dict[str, Any]:
    return {
        "name": "submit_reservation_call_result",
        "purpose": (
            "Mandatory final result tool. The AI must call it exactly once before "
            "the call ends so Spring can map the result to a reservation event."
        ),
    }


def build_call_config(request: RealAgentCallRequest) -> dict[str, Any]:
    return {
        "method": "ClawOpsAgent.call",
        "timeoutSeconds": 60,
        "targetPhoneMask": request.target_phone_mask,
        "waitForResultTool": True,
        "disconnectFinally": True,
        "postResultClosingGraceSeconds": POST_RESULT_CLOSING_GRACE_SECONDS,
    }


def build_result_wait_config() -> dict[str, Any]:
    return {
        "source": "submit_reservation_call_result_tool",
        "missingResultPolicy": "AI_PARSE_FAILED",
        "conflictPolicy": "NEEDS_CONFIRMATION",
        "strategy": "race_result_tool_against_call_end",
    }


def build_disconnect_config() -> dict[str, Any]:
    return {
        "method": "ClawOpsAgent.disconnect",
        "finally": True,
    }


def coerce_tool_result_payload(tool_result: Any) -> dict[str, Any]:
    """Return a mapper-safe payload from the future result tool output."""

    if isinstance(tool_result, dict):
        return tool_result
    return {}


def import_has_attrs(
    importer: Callable[[str], Any],
    module_name: str,
    attr_names: tuple[str, ...],
    missing: list[str],
) -> bool:
    try:
        module = importer(module_name)
    except Exception:
        missing.append(module_name)
        return False
    ok = True
    for attr_name in attr_names:
        if not hasattr(module, attr_name):
            missing.append(f"{module_name}.{attr_name}")
            ok = False
    return ok


class MockedRealAgentSdkRunner:
    """SDK-object-free runner double for pre-secret local tests."""

    def __init__(
        self,
        ai_result_factory: Callable[[RealAgentCallRequest], dict[str, Any]],
        execution_skeleton_builder: Callable[
            [RealAgentCallRequest], RealAgentExecutionSkeleton
        ] = build_real_agent_execution_skeleton,
    ) -> None:
        self.ai_result_factory = ai_result_factory
        self.execution_skeleton_builder = execution_skeleton_builder

    def run_reservation_call(self, request: RealAgentCallRequest) -> RealAgentCallResult:
        self.execution_skeleton_builder(request)
        ai_result = coerce_tool_result_payload(self.ai_result_factory(request))
        return RealAgentCallResult(
            ai_result=ai_result,
            sidecar_call_id=request.sidecar_call_id,
            provider_call_id=f"mock-sdk-provider-call-{request.reservation_id}",
            failure_reason=ai_result.get("failureReason"),
            retryable=ai_result.get("resultStatus") == "FAILED",
        )


class RealAgentSdkRunner:
    """SDK runner boundary guarded by real-agent approval and safety gates."""

    def __init__(
        self,
        gate_result: RealAgentGateResult,
        sdk_loader: Callable[[], RealAgentSdkModules] = load_real_agent_sdk_modules,
        surface_checker: Callable[[], RealAgentSdkSurfaceStatus] = check_real_agent_sdk_surface,
        execution_skeleton_builder: Callable[
            [RealAgentCallRequest], RealAgentExecutionSkeleton
        ] = build_real_agent_execution_skeleton,
        implementation_enabled: bool = False,
    ) -> None:
        self.gate_result = gate_result
        self.sdk_loader = sdk_loader
        self.surface_checker = surface_checker
        self.execution_skeleton_builder = execution_skeleton_builder
        self.implementation_enabled = implementation_enabled

    def run_reservation_call(self, request: RealAgentCallRequest) -> RealAgentCallResult:
        return asyncio.run(self.run_reservation_call_async(request))

    async def run_reservation_call_async(self, request: RealAgentCallRequest) -> RealAgentCallResult:
        if not self.gate_result.allowed or self.gate_result.block_reasons:
            raise RealAgentBlockedError(self.gate_result)

        surface_status = self.surface_checker()
        if not surface_status.ready:
            raise RealAgentBlockedError(
                RealAgentGateResult(
                    allowed=False,
                    block_reasons=(SDK_NOT_READY,),
                    missing_secret_names=self.gate_result.missing_secret_names,
                    sdk_installed=self.gate_result.sdk_installed,
                )
            )

        skeleton = self.execution_skeleton_builder(request)

        if not self.implementation_enabled:
            raise RealAgentBlockedError(
                RealAgentGateResult(
                    allowed=False,
                    block_reasons=(REAL_AGENT_NOT_IMPLEMENTED,),
                    missing_secret_names=self.gate_result.missing_secret_names,
                    sdk_installed=self.gate_result.sdk_installed,
                )
            )

        return await run_real_agent_sdk_call(request, skeleton)


async def run_real_agent_sdk_call(
    request: RealAgentCallRequest,
    skeleton: RealAgentExecutionSkeleton,
) -> RealAgentCallResult:
    from clawops.agent import BuiltinTool, ClawOpsAgent, OpenAIRealtime

    result_box: dict[str, Any] = {}
    failure_box: dict[str, str] = {}
    result_event = asyncio.Event()
    call_started_at = asyncio.get_running_loop().time()

    session = OpenAIRealtime(
        api_key=os.environ.get("OPENAI_API_KEY"),
        system_prompt=skeleton.system_prompt,
        model=skeleton.session_config["model"],
        voice=skeleton.session_config["voice"],
        language=skeleton.session_config["language"],
        greeting=skeleton.session_config["greeting"],
    )
    agent = ClawOpsAgent(
        api_key=os.environ.get("CLAWOPS_API_KEY"),
        account_id=os.environ.get("CLAWOPS_ACCOUNT_ID"),
        base_url=os.environ.get("CLAWOPS_BASE_URL"),
        from_=os.environ.get("CLAWOPS_FROM_NUMBER", ""),
        session=session,
        recording=False,
        builtin_tools=[BuiltinTool.SEND_DTMF],
    )

    @agent.tool
    async def submit_reservation_call_result(
        resultStatus: str,
        summary: str,
        confirmedDateTime: str = "",
        partySize: int = 0,
        reservationNameProvided: bool = False,
        phoneNumberProvided: bool = False,
        restaurantRequestedNameOrPhone: bool = False,
        alternativeTimeSuggested: bool = False,
        alternativeDateTime: str = "",
        failureReason: str = "",
        transcriptSummary: str = "",
    ) -> str:
        """Mandatory final reservation result.

        Call this exactly once after the restaurant clearly answers. Use
        CONFIRMED only when the restaurant clearly accepted the original
        requested date-time and party size. Use NEEDS_CONFIRMATION for
        alternative times, deposits, extra information, special conditions, or
        ambiguous outcomes. Use UNAVAILABLE only when the restaurant clearly
        cannot accept the requested reservation and gives no usable alternative.
        Use FAILED for connection or technical failure.
        """
        if should_reject_early_final_result(resultStatus, call_started_at):
            return json.dumps(
                {
                    "accepted": False,
                    "reason": "Final result is too early. A hearing check or opening exchange is not a reservation outcome. Continue the conversation, answer staff questions, ask whether the exact date/time/party size is available, and submit the final result only after the staff clearly answers.",
                },
                separators=(",", ":"),
            )
        if should_reject_early_ai_failed_result(
            resultStatus,
            summary,
            failureReason,
            call_started_at,
        ):
            return json.dumps(
                {
                    "accepted": False,
                    "reason": "FAILED result is too early for an ongoing call. Continue the conversation, answer the staff question, and submit a final result only after a clear outcome.",
                },
                separators=(",", ":"),
            )
        result_rejection_reason = final_result_rejection_reason(
            request=request,
            result_status=resultStatus,
            summary=summary,
            confirmed_date_time=confirmedDateTime,
            party_size=partySize,
            alternative_time_suggested=alternativeTimeSuggested,
            alternative_date_time=alternativeDateTime,
            failure_reason=failureReason,
            transcript_summary=transcriptSummary,
        )
        if result_rejection_reason:
            return json.dumps(
                {
                    "accepted": False,
                    "reason": result_rejection_reason,
                },
                separators=(",", ":"),
            )
        result_box.clear()
        result_box.update({
            "resultStatus": resultStatus,
            "summary": summary,
            "confirmedDateTime": none_if_blank(confirmedDateTime),
            "partySize": coerce_optional_party_size(partySize),
            "reservationNameProvided": coerce_bool(reservationNameProvided),
            "phoneNumberProvided": coerce_bool(phoneNumberProvided),
            "restaurantRequestedNameOrPhone": coerce_bool(restaurantRequestedNameOrPhone),
            "alternativeTimeSuggested": coerce_bool(alternativeTimeSuggested),
            "alternativeDateTime": none_if_blank(alternativeDateTime),
            "failureReason": none_if_blank(failureReason),
            "transcriptSummary": none_if_blank(transcriptSummary),
        })
        result_event.set()
        return accepted_result_tool_response()

    async def on_failed(call_session: Any, reason: str) -> None:
        failure_box["reason"] = reason or "CALL_CONNECTION_FAILED"

    agent.on("call_failed")(on_failed)

    call_session = None
    try:
        call_session = await agent.call(
            request.target_phone_number,
            timeout=skeleton.call_config["timeoutSeconds"],
        )
        call_session.on("call_failed", on_failed)
        wait_outcome = await wait_for_result_tool_or_call_end(call_session, result_event, timeout_seconds=180)
        ai_result = coerce_tool_result_payload(result_box)
        if wait_outcome == WAIT_RESULT_TOOL_SUBMITTED:
            await asyncio.sleep(
                skeleton.call_config.get(
                    "postResultClosingGraceSeconds",
                    POST_RESULT_CLOSING_GRACE_SECONDS,
                )
            )
            try:
                await call_session.hangup()
            except Exception:
                pass
        if not ai_result:
            failure_reason = canonical_call_failure_reason(failure_box.get("reason"))
            ai_result = failed_result(
                failure_reason or "AI_RESULT_TOOL_MISSING",
                "Call ended before the AI submitted a reservation result.",
            )
        return RealAgentCallResult(
            ai_result=ai_result,
            sidecar_call_id=request.sidecar_call_id,
            provider_call_id=getattr(call_session, "call_id", None),
            failure_reason=ai_result.get("failureReason"),
            retryable=ai_result.get("resultStatus") == "FAILED",
        )
    except asyncio.TimeoutError:
        if call_session is not None:
            try:
                await call_session.hangup()
            except Exception:
                pass
        ai_result = failed_result("CALL_TIMEOUT", "Call did not finish within the local timeout.")
        return RealAgentCallResult(ai_result, request.sidecar_call_id, None, "CALL_TIMEOUT", True)
    except Exception as exception:
        failure_reason = provider_fatal_failure_reason(exception)
        ai_result = failed_result(failure_reason, "Real-agent SDK call failed.")
        return RealAgentCallResult(
            ai_result,
            request.sidecar_call_id,
            getattr(call_session, "call_id", None),
            ai_result["failureReason"],
            True,
        )
    finally:
        try:
            await agent.disconnect()
        except Exception:
            pass


async def wait_for_result_tool_or_call_end(
    call_session: Any,
    result_event: asyncio.Event,
    timeout_seconds: float,
) -> str:
    call_end_task = asyncio.create_task(call_session.wait())
    result_task = asyncio.create_task(result_event.wait())
    tasks = {call_end_task, result_task}
    try:
        done, pending = await asyncio.wait(
            tasks,
            timeout=timeout_seconds,
            return_when=asyncio.FIRST_COMPLETED,
        )
        if not done:
            raise asyncio.TimeoutError
        if result_event.is_set():
            return WAIT_RESULT_TOOL_SUBMITTED
        return WAIT_CALL_ENDED
    finally:
        for task in tasks:
            if not task.done():
                task.cancel()


def failed_result(reason: str, summary: str) -> dict[str, Any]:
    return {
        "resultStatus": "FAILED",
        "summary": summary,
        "confirmedDateTime": None,
        "partySize": None,
        "reservationNameProvided": False,
        "phoneNumberProvided": False,
        "restaurantRequestedNameOrPhone": False,
        "alternativeTimeSuggested": False,
        "alternativeDateTime": None,
        "failureReason": reason or "PROVIDER_FATAL_ERROR",
        "transcriptSummary": summary,
    }


def accepted_result_tool_response() -> str:
    return json.dumps(
        {
            "accepted": True,
            "nextAction": "Tell the restaurant: 네, 확인 감사합니다. 그 시간에 방문하겠습니다. 좋은 하루 되세요.",
        },
        ensure_ascii=False,
        separators=(",", ":"),
    )


def safe_exception_failure_reason(exception: Exception) -> str:
    reason = normalize_failure_reason(type(exception).__name__) or "PROVIDER_ERROR"
    status = normalize_failure_reason(getattr(exception, "status", None))
    code = normalize_failure_reason(getattr(exception, "code", None))
    message = safe_exception_message_token(exception)
    parts = [reason]
    if status:
        parts.append(f"HTTP_{status}")
    if code:
        parts.append(code)
    if message and message not in parts:
        parts.append(message)
    return "_".join(parts)[:180]


def provider_fatal_failure_reason(exception: Exception) -> str:
    detail = safe_exception_failure_reason(exception)
    if not detail:
        return "PROVIDER_FATAL_ERROR"
    return f"PROVIDER_FATAL_ERROR__{detail}"[:180]


def safe_exception_message_token(exception: Exception) -> str | None:
    message = str(exception)
    if not message:
        return None
    scrubbed = re.sub(r"(?i)bearer\s+[a-z0-9._~+/=-]+", "BEARER_TOKEN", message)
    scrubbed = re.sub(r"sk-[A-Za-z0-9_-]{8,}", "API_KEY", scrubbed)
    scrubbed = re.sub(r"\+?\d[\d -]{6,}\d", "PHONE_NUMBER", scrubbed)
    scrubbed = re.sub(r"[A-Za-z0-9_-]{24,}", "TOKEN", scrubbed)
    return normalize_failure_reason(scrubbed[:120])


def canonical_call_failure_reason(value: Any) -> str | None:
    normalized = normalize_failure_reason(value)
    if normalized in {"BUSY", "CALL_BUSY"}:
        return "CALL_BUSY"
    if normalized in {"NO_ANSWER", "CALL_NO_ANSWER", "NOANSWER"}:
        return "CALL_NO_ANSWER"
    if normalized in {"FAILED", "FAILURE", "CONNECTION_FAILED", "CALL_CONNECTION_FAILED"}:
        return "CALL_CONNECTION_FAILED"
    return normalized


def normalize_failure_reason(value: Any) -> str | None:
    if value is None:
        return None
    normalized = "".join(ch if ch.isalnum() else "_" for ch in str(value).upper())
    normalized = "_".join(part for part in normalized.split("_") if part)
    return normalized or None


def none_if_blank(value: str | None) -> str | None:
    if value is None:
        return None
    stripped = str(value).strip()
    return stripped or None


def coerce_optional_party_size(value: Any) -> int | None:
    try:
        party_size = int(value)
    except (TypeError, ValueError):
        return None
    return party_size if party_size > 0 else None


def coerce_bool(value: Any) -> bool:
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        normalized = value.strip().lower()
        if normalized in {"1", "true", "yes", "y", "on"}:
            return True
        if normalized in {"0", "false", "no", "n", "off", ""}:
            return False
    if isinstance(value, (int, float)):
        return bool(value)
    return False


def should_reject_early_ai_failed_result(
    result_status: Any,
    summary: Any,
    failure_reason: Any,
    call_started_at: float,
    minimum_seconds: float | None = None,
) -> bool:
    minimum_seconds = MIN_SECONDS_BEFORE_AI_FAILED_RESULT if minimum_seconds is None else minimum_seconds
    if str(result_status).strip().upper() != "FAILED":
        return False
    elapsed = asyncio.get_running_loop().time() - call_started_at
    if elapsed >= minimum_seconds:
        return False
    combined = f"{summary or ''} {failure_reason or ''}".lower()
    early_failure_markers = (
        "응답",
        "불명확",
        "연결 불안정",
        "확인하지 못",
        "no response",
        "unclear",
        "unstable",
        "not confirm",
    )
    return any(marker in combined for marker in early_failure_markers)


def should_reject_early_final_result(
    result_status: Any,
    call_started_at: float,
    minimum_seconds: float | None = None,
) -> bool:
    minimum_seconds = MIN_SECONDS_BEFORE_FINAL_RESULT if minimum_seconds is None else minimum_seconds
    normalized_status = str(result_status).strip().upper()
    if normalized_status != "FAILED":
        return False
    elapsed = asyncio.get_running_loop().time() - call_started_at
    return elapsed < minimum_seconds


def final_result_rejection_reason(
    request: RealAgentCallRequest,
    result_status: Any,
    summary: Any,
    confirmed_date_time: Any,
    party_size: Any,
    alternative_time_suggested: Any,
    alternative_date_time: Any,
    failure_reason: Any,
    transcript_summary: Any,
) -> str | None:
    normalized_status = str(result_status).strip().upper()
    if normalized_status not in {"CONFIRMED", "UNAVAILABLE", "NEEDS_CONFIRMATION", "FAILED"}:
        return None

    combined_text = normalize_result_text(summary, transcript_summary, failure_reason)
    if result_text_looks_hearing_check_only(combined_text):
        return (
            "A hearing check is not a reservation outcome. Continue the call, ask the exact "
            "requested date/time/party size, and submit a result only after the restaurant answers."
        )

    if normalized_status == "CONFIRMED":
        if none_if_blank(str(confirmed_date_time or "")) != request.reservation_date_time:
            return "CONFIRMED requires the exact requested reservationDateTime from the system."
        if coerce_optional_party_size(party_size) != request.party_size:
            return "CONFIRMED requires the exact requested partySize from the system."

    if normalized_status in {"CONFIRMED", "UNAVAILABLE", "NEEDS_CONFIRMATION"}:
        if not result_text_mentions_party_size(combined_text, request.party_size):
            return "The result summary must mention the requested party size before ending the call."
        if not result_text_mentions_requested_time(combined_text, request.reservation_date_time):
            return "The result summary must mention the requested date/time before ending the call."

    if normalized_status == "NEEDS_CONFIRMATION":
        if coerce_bool(alternative_time_suggested) and not none_if_blank(str(alternative_date_time or "")):
            return "Alternative time results must include alternativeDateTime."

    return None


def normalize_result_text(*values: Any) -> str:
    return " ".join(str(value or "").strip().lower() for value in values if value is not None)


def result_text_looks_hearing_check_only(text: str) -> bool:
    if not text:
        return False
    hearing_markers = ("들리", "소리", "hearing", "hear", "can you hear", "통화 확인")
    reservation_markers = (
        "예약",
        "가능",
        "불가",
        "마감",
        "대체",
        "시간",
        "인원",
        "명",
        "confirmed",
        "available",
        "unavailable",
        "alternative",
        "party",
    )
    return any(marker in text for marker in hearing_markers) and not any(
        marker in text for marker in reservation_markers
    )


def result_text_mentions_party_size(text: str, party_size: int) -> bool:
    if party_size <= 0:
        return False
    if str(party_size) in text:
        return True
    korean_count = {
        1: ("한 명", "1명"),
        2: ("두 명", "2명"),
        3: ("세 명", "3명"),
        4: ("네 명", "4명"),
        5: ("다섯 명", "5명"),
        6: ("여섯 명", "6명"),
        7: ("일곱 명", "7명"),
        8: ("여덟 명", "8명"),
        9: ("아홉 명", "9명"),
        10: ("열 명", "10명"),
    }
    return any(token in text for token in korean_count.get(party_size, ()))


def result_text_mentions_requested_time(text: str, reservation_date_time: str) -> bool:
    match = re.match(
        r"(?P<year>\d{4})-(?P<month>\d{2})-(?P<day>\d{2})[tT ](?P<hour>\d{2}):(?P<minute>\d{2})",
        reservation_date_time or "",
    )
    if not match:
        return bool(reservation_date_time and reservation_date_time.lower() in text)

    month = int(match.group("month"))
    day = int(match.group("day"))
    hour = int(match.group("hour"))
    minute = int(match.group("minute"))
    date_tokens = (
        f"{month}월 {day}일",
        f"{month}월{day}일",
        f"{match.group('month')}-{match.group('day')}",
        f"{match.group('year')}-{match.group('month')}-{match.group('day')}",
    )
    time_tokens = [
        f"{hour:02d}:{minute:02d}",
        f"{hour}:{minute:02d}",
        f"{hour}시",
    ]
    if hour > 12:
        time_tokens.extend((f"오후 {hour - 12}시", f"저녁 {hour - 12}시"))
    if minute:
        time_tokens.extend((f"{hour}시 {minute}분", f"{hour}시{minute}분"))

    return any(token in text for token in date_tokens) and any(token in text for token in time_tokens)
