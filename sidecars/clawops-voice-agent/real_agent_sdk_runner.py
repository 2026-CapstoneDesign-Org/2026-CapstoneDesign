"""SDK-backed real-agent runner skeleton.

This module intentionally performs no top-level ClawOps/OpenAI imports. The
future SDK imports are isolated behind an explicit helper, and the runner still
hard-stops before creating SDK objects, starting a Voice Agent, or making calls.
"""

from __future__ import annotations

import importlib
import importlib.util
import asyncio
import inspect
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
POST_CALL_END_RESULT_GRACE_SECONDS = 8.0
POST_RESULT_CLOSING_GRACE_SECONDS = 10.0
CALL_ANSWER_GREETING_DELAY_SECONDS = 1.0
CONFIRMED_RESULT_STABILITY_SECONDS = 2.0
REALTIME_RESPONSE_READY_TIMEOUT_SECONDS = 3.0
TRANSCRIPT_PREVIEW_MAX_CHARS = 140


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
        "## Sidecar-Controlled First Turn",
        "The sidecar will trigger the first assistant response with per-response instructions.",
        "For the first assistant response, obey the per-response instructions only. Do not repeat or extend them.",
        "The first assistant response must not contain the requested date, time, party size, or the availability question.",
        "After the first response, stop speaking and wait for the staff to say whether the restaurant / branch name is correct.",
        "Do not say the reservation date/time/party size until the staff confirms the restaurant / branch name is correct.",
        "Do not accept a premature yes as reservation confirmation until after you have stated the requested date/time/party size.",
        "If the staff asks who is calling, repeat that you are an AI reservation assistant calling to check reservation availability, then ask whether the restaurant / branch name is correct. Do not close the call.",
        base_prompt,
        "## Current Reservation Request",
        f"- Restaurant name: {request.restaurant_name}",
        f"- Requested date-time: {request.reservation_date_time}",
        f"- Korean spoken date/time phrase: {spoken_reservation_datetime(request.reservation_date_time)}",
        f"- Korean spoken reservation phrase: {spoken_reservation_phrase(request)}",
        f"- Party size: {request.party_size}",
        f"- Request note: {request.request_note or ''}",
        f"- Reservation name: {request.reservation_name or '테스트 예약자'}",
        f"- Reservation contact: {request.reservation_contact_number or '테스트 연락처'}",
        f"- Restaurant / branch confirmation target: {request.restaurant_name}",
        "- Do not repeat the restaurant / branch confirmation within one assistant turn.",
        "- Say the Korean spoken reservation phrase slowly with the commas as natural pauses.",
        "- Never say the closing sentence unless `submit_reservation_call_result` was accepted.",
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
        "greeting": False,
        "turnDetection": {
            "type": "semantic_vad",
            "create_response": False,
            "eagerness": "low",
            "interrupt_response": False,
        },
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
        "postCallEndResultGraceSeconds": POST_CALL_END_RESULT_GRACE_SECONDS,
    }


def build_disconnect_config() -> dict[str, Any]:
    return {
        "method": "ClawOpsAgent.disconnect",
        "finally": True,
    }


def response_create_kwargs(
    reason: str,
    request: RealAgentCallRequest,
    transcript_entries: list[tuple[str, str]],
) -> dict[str, Any]:
    instructions = response_create_instructions(reason, request, transcript_entries)
    if not instructions:
        return {}
    return {"response": {"instructions": instructions}}


def response_create_instructions(
    reason: str,
    request: RealAgentCallRequest,
    transcript_entries: list[tuple[str, str]],
) -> str:
    if reason == "call_answered":
        return (
            "Respond in Korean. Speak only this Korean sentence once, with no words before or after it: "
            f"안녕하세요, 예약 가능 여부 확인을 위해 전화드린 AI 예약 도우미입니다. 혹시 {request.restaurant_name} 맞나요? "
            "Do not repeat any part of the sentence. After speaking it once, stop. "
            "Do not include the requested date, time, party size, or any reservation availability question in this response."
        )
    if reason == "user_transcript" and last_assistant_asked_branch_confirmation(transcript_entries):
        if user_rejected_branch(transcript_entries[-1][1]):
            return (
                "Respond in Korean. The staff indicated this may not be the requested restaurant / branch. "
                "Call `submit_reservation_call_result` with NEEDS_CONFIRMATION. "
                "Then speak exactly this one sentence only: 죄송합니다. 확인 후 다시 연락드리겠습니다. "
                "After speaking that sentence, stop."
            )
        if user_allows_branch_progress_from_context(transcript_entries):
            return (
                "Respond in Korean. Treat the staff response as sufficient to continue the demo call. "
                "Speak exactly this one sentence only: "
                f"{spoken_reservation_phrase(request)} 예약 가능할까요? "
                "After speaking that sentence, stop and wait."
            )
    if reason in {"closing_after_result", "closing_after_confirmed_result"}:
        return (
            "The requested reservation result is already decided. Do not ask another question. "
            "Respond in Korean. Speak exactly this one sentence only: "
            "네, 확인 감사합니다. 그 시간에 방문하겠습니다. 좋은 하루 되세요. "
            "After speaking that sentence, stop."
        )
    if reason == "closing_after_non_confirmed_result":
        return (
            "The requested reservation result is already decided and it is not confirmed. Do not ask another question. "
            "Respond in Korean. Speak exactly this one sentence only: "
            "네, 확인 감사합니다. 확인 후 다시 연락드리겠습니다. 좋은 하루 되세요. "
            "After speaking that sentence, stop."
        )
    if reason == "user_transcript" and last_assistant_asked_alternative_time(transcript_entries):
        return (
            "The staff just answered the alternative-time question. "
            "Do not ask the requested availability question again. "
            "If the staff suggested an alternative time, call `submit_reservation_call_result` with NEEDS_CONFIRMATION. "
            "If the staff says there is no usable alternative, call `submit_reservation_call_result` with UNAVAILABLE. "
            "If the answer is ambiguous, call `submit_reservation_call_result` with NEEDS_CONFIRMATION. "
            "Do not say you will visit at the requested time."
        )
    if reason == "user_transcript" and staff_acknowledged_provided_name_or_phone(request, transcript_entries):
        return (
            "The staff acknowledged the reservation name/contact that you provided after the requested availability was confirmed. "
            "Do not ask another question. "
            "Call `submit_reservation_call_result` with CONFIRMED. "
            f"Use confirmedDateTime exactly `{request.reservation_date_time}` and partySize exactly `{request.party_size}`. "
            f"The summary or transcriptSummary must mention {display_reservation_datetime(request.reservation_date_time)} and {request.party_size} people. "
            "After the tool is accepted, say only the confirmed closing sentence."
        )
    if reason == "user_transcript" and last_assistant_asked_availability(transcript_entries):
        latest_user_text = transcript_entries[-1][1]
        if contains_name_or_phone_request(latest_user_text):
            return name_or_phone_response_instructions(request, latest_user_text)
        if contains_unavailable_marker(latest_user_text) and not contains_alternative_time_marker(latest_user_text):
            return alternative_time_question_instructions()
        return (
            "The staff just answered the availability question. "
            "Do not ask the availability question again. "
            "If the answer clearly confirms the requested date/time/party size, call "
            "`submit_reservation_call_result` with CONFIRMED. "
            "If the answer says unavailable, suggests an alternative, or is ambiguous, "
            "call `submit_reservation_call_result` with the safest matching status. "
            "Do not speak another question unless the staff asked for missing details."
        )
    return ""


def alternative_time_question_instructions() -> str:
    return (
        "Respond in Korean. The staff said the requested reservation time is unavailable. "
        "Do not call `submit_reservation_call_result` yet. "
        "Speak exactly this one sentence only: 혹시 가능한 다른 시간대가 있을까요? "
        "After speaking that sentence, stop and wait."
    )


def name_or_phone_response_instructions(request: RealAgentCallRequest, staff_text: str) -> str:
    needs_name = contains_name_request(staff_text)
    needs_phone = contains_phone_request(staff_text)
    name = request.reservation_name or "테스트 예약자"
    contact = request.reservation_contact_number or "테스트 연락처"
    if needs_name and needs_phone:
        sentence = f"예약자는 {name}이고, 연락처는 {contact}입니다. 이 정보로 예약 부탁드립니다."
    elif needs_phone:
        sentence = f"연락처는 {contact}입니다. 이 연락처로 예약 부탁드립니다."
    else:
        sentence = f"예약자는 {name}입니다. 이 이름으로 예약 부탁드립니다."
    return (
        "Respond in Korean. The staff confirmed availability but requested reservation name or contact. "
        "Do not call `submit_reservation_call_result` yet. "
        f"Speak exactly this one sentence only: {sentence} "
        "After speaking that sentence, stop and wait for the staff to acknowledge the reservation."
    )


def last_assistant_asked_branch_confirmation(transcript_entries: list[tuple[str, str]]) -> bool:
    if len(transcript_entries) < 2:
        return False
    last_speaker, _ = transcript_entries[-1]
    if last_speaker != "user":
        return False
    for speaker, text in reversed(transcript_entries[:-1]):
        if speaker != "assistant":
            continue
        return assistant_asked_branch_confirmation(text)
    return False


def user_confirmed_branch(text: str) -> bool:
    normalized = normalize_korean_text(text)
    if any(token in normalized for token in ("맞나요", "맞습니까", "맞는지", "맞는건가", "맞는거", "맞냐", "맞니")):
        return False
    if normalized in {
        "네맞습니다",
        "예맞습니다",
        "맞습니다",
        "네맞아요",
        "예맞아요",
        "맞아요",
        "네맞아",
        "맞아",
        "네맞는데요",
        "예맞는데요",
    }:
        return True
    if normalized in {
        "여보세요",
        "안녕",
        "안녕하세요",
        "네",
        "예",
        "말씀하세요",
        "네말씀하세요",
        "예말씀하세요",
        "들립니다",
        "네들립니다",
        "예들립니다",
    }:
        return False
    if "아니" in normalized or "아닙" in normalized or "아닌" in normalized:
        return False
    return any(token in normalized for token in ("맞습니다", "맞아요", "맞는데요"))


def branch_confirmed_before_availability(transcript_entries: list[tuple[str, str]]) -> bool:
    awaiting_branch_confirmation = False
    branch_confirmed = False
    opening_phrase_seen_after_branch = False
    for speaker, text in transcript_entries:
        normalized = normalize_korean_text(text)
        if speaker == "assistant" and "맞나요" in normalized:
            awaiting_branch_confirmation = True
            branch_confirmed = False
            opening_phrase_seen_after_branch = False
            continue
        if awaiting_branch_confirmation and speaker == "user":
            if user_allows_branch_progress(text):
                branch_confirmed = True
                awaiting_branch_confirmation = False
                opening_phrase_seen_after_branch = False
            elif is_bare_yes(text) and opening_phrase_seen_after_branch:
                branch_confirmed = True
                awaiting_branch_confirmation = False
                opening_phrase_seen_after_branch = False
            elif user_rejected_branch(text):
                return False
            elif is_hearing_or_opening_phrase(text):
                opening_phrase_seen_after_branch = True
    return branch_confirmed


def user_allows_branch_progress(text: str) -> bool:
    return user_confirmed_branch(text)


def user_allows_branch_progress_from_context(transcript_entries: list[tuple[str, str]]) -> bool:
    if not transcript_entries:
        return False
    latest_text = transcript_entries[-1][1]
    if user_allows_branch_progress(latest_text):
        return True
    if not is_bare_yes(latest_text):
        return False
    for speaker, text in reversed(transcript_entries[:-1]):
        if speaker == "assistant" and assistant_asked_branch_confirmation(text):
            return False
        if speaker == "user" and is_hearing_or_opening_phrase(text):
            return True
    return False


def user_rejected_branch(text: str) -> bool:
    normalized = normalize_korean_text(text)
    return any(token in normalized for token in ("아니", "아닙", "아닌", "틀렸", "잘못"))


def assistant_asked_branch_confirmation(text: str) -> bool:
    normalized = normalize_korean_text(text)
    return "맞나요" in normalized and "가능할까요" not in normalized


def is_bare_yes(text: str) -> bool:
    return normalize_korean_text(text) in {"네", "예"}


def last_assistant_asked_availability(transcript_entries: list[tuple[str, str]]) -> bool:
    if len(transcript_entries) < 2:
        return False
    last_speaker, _ = transcript_entries[-1]
    if last_speaker != "user":
        return False
    for speaker, text in reversed(transcript_entries[:-1]):
        if speaker != "assistant":
            continue
        return assistant_asked_availability_question(text)
    return False


def last_assistant_asked_alternative_time(transcript_entries: list[tuple[str, str]]) -> bool:
    if len(transcript_entries) < 2:
        return False
    last_speaker, _ = transcript_entries[-1]
    if last_speaker != "user":
        return False
    for speaker, text in reversed(transcript_entries[:-1]):
        if speaker != "assistant":
            continue
        return assistant_asked_alternative_time_question(text)
    return False


def assistant_asked_alternative_time_question(text: str) -> bool:
    normalized = normalize_korean_text(text)
    return "다른시간" in normalized or "대체시간" in normalized or "가능한다른시간" in normalized


def assistant_asked_availability_question(text: str) -> bool:
    normalized = normalize_korean_text(text)
    return (
        "예약가능할까요" in normalized
        or ("가능할까요" in normalized and "예약" in normalized)
    )


def user_answer_text_after_latest_availability_question(transcript_entries: list[tuple[str, str]]) -> str:
    for index in range(len(transcript_entries) - 1, -1, -1):
        speaker, text = transcript_entries[index]
        if speaker == "assistant" and assistant_asked_availability_question(text):
            return normalize_result_text(
                *(entry_text for entry_speaker, entry_text in transcript_entries[index + 1:] if entry_speaker == "user")
            )
    return ""


def confirmed_availability_answer_after_question(transcript_entries: list[tuple[str, str]]) -> bool:
    return contains_confirmation_marker(user_answer_text_after_latest_availability_question(transcript_entries))


def latest_name_or_phone_request_after_availability(
    transcript_entries: list[tuple[str, str]],
) -> tuple[int, str] | None:
    latest_availability_index = None
    for index in range(len(transcript_entries) - 1, -1, -1):
        speaker, text = transcript_entries[index]
        if speaker == "assistant" and assistant_asked_availability_question(text):
            latest_availability_index = index
            break
    if latest_availability_index is None:
        return None
    for index in range(len(transcript_entries) - 1, latest_availability_index, -1):
        speaker, text = transcript_entries[index]
        if speaker == "user" and contains_name_or_phone_request(text):
            return index, text
    return None


def assistant_provided_requested_name_or_phone_after(
    request: RealAgentCallRequest,
    transcript_entries: list[tuple[str, str]],
    user_request_index: int,
    user_request_text: str,
) -> bool:
    assistant_text = normalize_result_text(
        *(text for speaker, text in transcript_entries[user_request_index + 1:] if speaker == "assistant")
    )
    if not assistant_text:
        return False
    normalized_assistant = normalize_korean_text(assistant_text)
    if contains_name_request(user_request_text):
        expected_name = normalize_korean_text(request.reservation_name or "테스트예약자")
        if expected_name and expected_name not in normalized_assistant:
            return False
    if contains_phone_request(user_request_text):
        expected_digits = digits_only(request.reservation_contact_number)
        assistant_digits = digits_only(assistant_text)
        if expected_digits and expected_digits not in assistant_digits:
            return False
        if not expected_digits and "테스트연락처" not in normalized_assistant:
            return False
    return True


def customer_acknowledged_after_assistant_info(
    transcript_entries: list[tuple[str, str]],
    user_request_index: int,
) -> bool:
    assistant_info_index = None
    for index in range(user_request_index + 1, len(transcript_entries)):
        if transcript_entries[index][0] == "assistant":
            assistant_info_index = index
            break
    if assistant_info_index is None:
        return False
    user_text_after_info = normalize_result_text(
        *(text for speaker, text in transcript_entries[assistant_info_index + 1:] if speaker == "user")
    )
    return contains_confirmation_marker(user_text_after_info) or contains_acknowledgement_marker(user_text_after_info)


def provided_name_or_phone_flags(
    request: RealAgentCallRequest,
    transcript_entries: list[tuple[str, str]],
) -> tuple[bool, bool]:
    name_or_phone_request = latest_name_or_phone_request_after_availability(transcript_entries)
    if name_or_phone_request is None:
        return False, False
    request_index, request_text = name_or_phone_request
    if not assistant_provided_requested_name_or_phone_after(
        request,
        transcript_entries,
        request_index,
        request_text,
    ):
        return False, False
    return contains_name_request(request_text), contains_phone_request(request_text)


def staff_acknowledged_provided_name_or_phone(
    request: RealAgentCallRequest,
    transcript_entries: list[tuple[str, str]],
) -> bool:
    if not transcript_entries or transcript_entries[-1][0] != "user":
        return False
    name_or_phone_request = latest_name_or_phone_request_after_availability(transcript_entries)
    if name_or_phone_request is None:
        return False
    request_index, request_text = name_or_phone_request
    if not assistant_provided_requested_name_or_phone_after(
        request,
        transcript_entries,
        request_index,
        request_text,
    ):
        return False
    return customer_acknowledged_after_assistant_info(transcript_entries, request_index)


def is_hearing_or_opening_phrase(text: str) -> bool:
    normalized = normalize_korean_text(text)
    return normalized in {
        "여보세요",
        "안녕",
        "안녕하세요",
        "네",
        "예",
        "말씀하세요",
        "네말씀하세요",
        "예말씀하세요",
        "들립니다",
        "네들립니다",
        "예들립니다",
    }


def normalize_korean_text(text: Any) -> str:
    return "".join(ch for ch in str(text or "").lower() if ch.isalnum())


def digits_only(text: Any) -> str:
    return "".join(ch for ch in str(text or "") if ch.isdigit())


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
    transcript_entries: list[tuple[str, str]] = []
    result_event = asyncio.Event()
    response_request_lock = asyncio.Lock()
    awaiting_assistant_response = False
    live_transcript_result_submitted = False
    initial_response_requested = False
    call_started_at = asyncio.get_running_loop().time()
    latest_user_transcript_at: float | None = None
    transcript_version = 0

    safe_log(
        "real_agent_call_start",
        reservationId=request.reservation_id,
        sidecarCallId=request.sidecar_call_id,
        targetPhoneMask=request.target_phone_mask,
    )
    session = OpenAIRealtime(
        api_key=os.environ.get("OPENAI_API_KEY"),
        system_prompt=skeleton.system_prompt,
        model=skeleton.session_config["model"],
        voice=skeleton.session_config["voice"],
        language=skeleton.session_config["language"],
        greeting=skeleton.session_config["greeting"],
        turn_detection=skeleton.session_config["turnDetection"],
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
        if should_wait_for_confirmed_result_stability(resultStatus, latest_user_transcript_at):
            return json.dumps(
                {
                    "accepted": False,
                    "reason": "The latest staff transcript may still be incomplete. Wait briefly, then if the staff asks for reservation name or contact, provide it before submitting CONFIRMED.",
                },
                separators=(",", ":"),
            )
        transcript_rejection_reason = final_result_transcript_rejection_reason(
            request,
            resultStatus,
            transcript_entries,
        )
        if transcript_rejection_reason:
            return json.dumps(
                {
                    "accepted": False,
                    "reason": transcript_rejection_reason,
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
        return accepted_result_tool_response(resultStatus)

    async def on_failed(call_session: Any, reason: str) -> None:
        failure_box["reason"] = reason or "CALL_CONNECTION_FAILED"
        safe_log(
            "real_agent_call_failed",
            reservationId=request.reservation_id,
            reason=canonical_call_failure_reason(reason) or "CALL_CONNECTION_FAILED",
        )

    async def request_realtime_response(reason: str, *, allow_after_result: bool = False) -> bool:
        nonlocal awaiting_assistant_response
        async with response_request_lock:
            if result_box and not allow_after_result:
                return False
            if awaiting_assistant_response and not allow_after_result:
                return False
            connection = getattr(session, "_connection", None)
            if connection is None:
                return False
            response = getattr(connection, "response", None)
            create = getattr(response, "create", None)
            if create is None:
                return False
            awaiting_assistant_response = True
        try:
            create_kwargs = response_create_kwargs(reason, request, transcript_entries)
            if not create_kwargs:
                async with response_request_lock:
                    awaiting_assistant_response = False
                safe_log(
                    "real_agent_response_not_requested",
                    reservationId=request.reservation_id,
                    reason=reason,
                )
                return False
            created = create(**create_kwargs)
            if inspect.isawaitable(created):
                await created
            safe_log(
                "real_agent_response_requested",
                reservationId=request.reservation_id,
                reason=reason,
            )
            return True
        except Exception:
            async with response_request_lock:
                awaiting_assistant_response = False
            safe_log(
                "real_agent_response_request_failed",
                reservationId=request.reservation_id,
                reason=reason,
            )
            return False

    async def request_realtime_response_when_ready(reason: str, delay_seconds: float) -> None:
        if delay_seconds > 0:
            await asyncio.sleep(delay_seconds)
        if not hasattr(session, "_connection"):
            return
        deadline = asyncio.get_running_loop().time() + REALTIME_RESPONSE_READY_TIMEOUT_SECONDS
        while getattr(session, "_connection", None) is None:
            if result_box or asyncio.get_running_loop().time() >= deadline:
                return
            await asyncio.sleep(0.1)
        await request_realtime_response(reason)

    async def on_call_start(call_session: Any) -> None:
        nonlocal initial_response_requested
        if initial_response_requested:
            safe_log(
                "real_agent_call_answered_duplicate_ignored",
                reservationId=request.reservation_id,
            )
            return
        initial_response_requested = True
        safe_log(
            "real_agent_call_answered",
            reservationId=request.reservation_id,
            greetingDelaySeconds=CALL_ANSWER_GREETING_DELAY_SECONDS,
        )
        if CALL_ANSWER_GREETING_DELAY_SECONDS <= 0:
            await request_realtime_response_when_ready("call_answered", 0)
        else:
            asyncio.create_task(
                request_realtime_response_when_ready(
                    "call_answered",
                    CALL_ANSWER_GREETING_DELAY_SECONDS,
                )
            )

    async def submit_live_transcript_result_when_stable(
        candidate_result: dict[str, Any],
        candidate_version: int,
    ) -> None:
        nonlocal live_transcript_result_submitted
        await asyncio.sleep(CONFIRMED_RESULT_STABILITY_SECONDS)
        if result_box or transcript_version != candidate_version:
            return
        refreshed_result = infer_ai_result_from_transcripts(request, transcript_entries)
        if not refreshed_result:
            return
        if str(refreshed_result.get("resultStatus") or "").strip().upper() != "CONFIRMED":
            result_box.update(refreshed_result)
        else:
            result_box.update(candidate_result)
        live_transcript_result_submitted = True
        result_event.set()
        safe_log(
            "real_agent_transcript_live_result",
            reservationId=request.reservation_id,
            resultStatus=result_box.get("resultStatus"),
        )

    async def on_transcript(call_session: Any, speaker: str, transcript: str) -> None:
        nonlocal awaiting_assistant_response, live_transcript_result_submitted, latest_user_transcript_at, transcript_version
        normalized_speaker = "user" if speaker == "user" else "assistant"
        text = str(transcript or "").strip()
        if not text:
            return
        transcript_entries.append((normalized_speaker, text))
        safe_log(
            "real_agent_transcript",
            reservationId=request.reservation_id,
            speaker=normalized_speaker,
            textPreview=mask_sensitive_text(text[:TRANSCRIPT_PREVIEW_MAX_CHARS]),
        )
        if normalized_speaker == "assistant":
            async with response_request_lock:
                awaiting_assistant_response = False
        elif normalized_speaker == "user":
            latest_user_transcript_at = asyncio.get_running_loop().time()
            transcript_version += 1
            asyncio.create_task(request_realtime_response("user_transcript"))
        if not result_box:
            live_result = infer_ai_result_from_transcripts(request, transcript_entries)
            if live_result:
                if str(live_result.get("resultStatus") or "").strip().upper() == "CONFIRMED":
                    asyncio.create_task(
                        submit_live_transcript_result_when_stable(live_result, transcript_version)
                    )
                else:
                    result_box.update(live_result)
                    live_transcript_result_submitted = True
                    result_event.set()
                    safe_log(
                        "real_agent_transcript_live_result",
                        reservationId=request.reservation_id,
                        resultStatus=live_result.get("resultStatus"),
                    )

    agent.on("call_failed")(on_failed)

    call_session = None
    try:
        call_session = await agent.call(
            request.target_phone_number,
            timeout=skeleton.call_config["timeoutSeconds"],
        )
        call_session.on("call_failed", on_failed)
        call_session.on("transcript", on_transcript)
        call_session.on("call_start", on_call_start)
        safe_log(
            "real_agent_call_queued",
            reservationId=request.reservation_id,
            sidecarCallId=request.sidecar_call_id,
            providerCallId=getattr(call_session, "call_id", None),
        )
        wait_outcome = await wait_for_result_tool_or_call_end(call_session, result_event, timeout_seconds=180)
        ai_result = coerce_tool_result_payload(result_box)
        if wait_outcome == WAIT_RESULT_TOOL_SUBMITTED:
            safe_log(
                "real_agent_result_tool_submitted",
                reservationId=request.reservation_id,
                resultStatus=ai_result.get("resultStatus"),
            )
            if ai_result:
                await request_realtime_response(closing_response_reason(ai_result), allow_after_result=True)
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
            ai_result = infer_ai_result_from_transcripts(request, transcript_entries)
            if ai_result:
                safe_log(
                    "real_agent_transcript_fallback_result",
                    reservationId=request.reservation_id,
                    resultStatus=ai_result.get("resultStatus"),
                )
            else:
                failure_reason = canonical_call_failure_reason(failure_box.get("reason"))
                ai_result = failed_result(
                    failure_reason or "AI_RESULT_TOOL_MISSING",
                    "Call ended before the AI submitted a reservation result.",
                )
                safe_log(
                    "real_agent_result_missing",
                    reservationId=request.reservation_id,
                    failureReason=ai_result.get("failureReason"),
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
        safe_log("real_agent_timeout", reservationId=request.reservation_id)
        return RealAgentCallResult(ai_result, request.sidecar_call_id, None, "CALL_TIMEOUT", True)
    except Exception as exception:
        failure_reason = provider_fatal_failure_reason(exception)
        ai_result = failed_result(failure_reason, "Real-agent SDK call failed.")
        safe_log(
            "real_agent_exception",
            reservationId=request.reservation_id,
            failureReason=failure_reason,
        )
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
        if call_end_task in done:
            try:
                await asyncio.wait_for(
                    result_event.wait(),
                    timeout=POST_CALL_END_RESULT_GRACE_SECONDS,
                )
                return WAIT_RESULT_TOOL_SUBMITTED
            except asyncio.TimeoutError:
                return WAIT_CALL_ENDED
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


def infer_ai_result_from_transcripts(
    request: RealAgentCallRequest,
    transcript_entries: list[tuple[str, str]],
) -> dict[str, Any] | None:
    """Best-effort demo fallback when the model speaks but misses the result tool."""

    user_text = normalize_result_text(
        *(text for speaker, text in transcript_entries if speaker == "user")
    )
    assistant_text = normalize_result_text(
        *(text for speaker, text in transcript_entries if speaker == "assistant")
    )
    all_text = normalize_result_text(user_text, assistant_text)
    if not user_text and not assistant_text:
        return None

    requested_summary = requested_reservation_summary(request)
    transcript_summary = compact_transcript_summary(transcript_entries)
    requested_context_present = result_text_mentions_requested_time(
        all_text,
        request.reservation_date_time,
    ) and result_text_mentions_party_size(all_text, request.party_size)
    branch_confirmed = branch_confirmed_before_availability(transcript_entries)
    name_provided, phone_provided = provided_name_or_phone_flags(request, transcript_entries)

    alternative_date_time = extract_alternative_datetime(request, user_text)
    if alternative_time_question_answered_without_alternative(transcript_entries):
        return {
            "resultStatus": "UNAVAILABLE",
            "summary": f"{requested_summary} 예약이 불가능하다고 확인했습니다.",
            "confirmedDateTime": None,
            "partySize": request.party_size,
            "reservationNameProvided": False,
            "phoneNumberProvided": False,
            "restaurantRequestedNameOrPhone": contains_name_or_phone_request(user_text),
            "alternativeTimeSuggested": False,
            "alternativeDateTime": None,
            "failureReason": None,
            "transcriptSummary": transcript_summary,
        }
    if contains_alternative_time_marker(user_text) or contains_assistant_needs_confirmation_marker(assistant_text):
        return {
            "resultStatus": "NEEDS_CONFIRMATION",
            "summary": f"{requested_summary}에 대해 식당이 대체 시간 또는 추가 확인이 필요하다고 답했습니다.",
            "confirmedDateTime": None,
            "partySize": None,
            "reservationNameProvided": False,
            "phoneNumberProvided": False,
            "restaurantRequestedNameOrPhone": contains_name_or_phone_request(user_text),
            "alternativeTimeSuggested": alternative_date_time is not None,
            "alternativeDateTime": alternative_date_time,
            "failureReason": None if alternative_date_time else "NEEDS_CONFIRMATION_ALTERNATIVE_TIME_UNPARSED",
            "transcriptSummary": transcript_summary,
        }
    if contains_unavailable_marker(user_text) or contains_assistant_unavailable_marker(assistant_text):
        return None
    if (
        branch_confirmed
        and requested_context_present
        and confirmed_availability_answer_after_question(transcript_entries)
        and final_result_transcript_rejection_reason(request, "CONFIRMED", transcript_entries) is None
    ):
        return {
            "resultStatus": "CONFIRMED",
            "summary": f"{requested_summary} 예약이 가능하다고 확인했습니다.",
            "confirmedDateTime": request.reservation_date_time,
            "partySize": request.party_size,
            "reservationNameProvided": name_provided,
            "phoneNumberProvided": phone_provided,
            "restaurantRequestedNameOrPhone": contains_name_or_phone_request(user_text),
            "alternativeTimeSuggested": False,
            "alternativeDateTime": None,
            "failureReason": None,
            "transcriptSummary": transcript_summary,
        }
    if contains_ambiguous_marker(user_text):
        return {
            "resultStatus": "NEEDS_CONFIRMATION",
            "summary": f"{requested_summary} 예약 가능 여부가 명확하지 않아 사용자 확인이 필요합니다.",
            "confirmedDateTime": None,
            "partySize": None,
            "reservationNameProvided": False,
            "phoneNumberProvided": False,
            "restaurantRequestedNameOrPhone": contains_name_or_phone_request(user_text),
            "alternativeTimeSuggested": False,
            "alternativeDateTime": None,
            "failureReason": "AI_RESULT_TOOL_MISSING_AMBIGUOUS_TRANSCRIPT",
            "transcriptSummary": transcript_summary,
        }
    return None


def requested_reservation_summary(request: RealAgentCallRequest) -> str:
    return f"{display_reservation_datetime(request.reservation_date_time)} {request.party_size}명"


def display_reservation_datetime(value: str) -> str:
    match = re.match(
        r"(?P<year>\d{4})-(?P<month>\d{2})-(?P<day>\d{2})[tT ](?P<hour>\d{2}):(?P<minute>\d{2})",
        value or "",
    )
    if not match:
        return value or "요청한 시간"
    return (
        f"{int(match.group('month'))}월 {int(match.group('day'))}일 "
        f"{int(match.group('hour'))}시 {int(match.group('minute'))}분"
    )


def spoken_reservation_phrase(request: RealAgentCallRequest) -> str:
    return f"{spoken_reservation_datetime(request.reservation_date_time)}, {spoken_party_size(request.party_size)}"


def spoken_reservation_datetime(value: str) -> str:
    match = re.match(
        r"(?P<year>\d{4})-(?P<month>\d{2})-(?P<day>\d{2})[tT ](?P<hour>\d{2}):(?P<minute>\d{2})",
        value or "",
    )
    if not match:
        return value or "요청한 시간"
    hour = int(match.group("hour"))
    minute = int(match.group("minute"))
    meridiem = "오전" if hour < 12 else "오후"
    spoken_hour = hour if 1 <= hour <= 12 else hour - 12
    if spoken_hour == 0:
        spoken_hour = 12
    minute_part = f"{minute}분" if minute else "정각"
    return (
        f"{int(match.group('month'))}월 {int(match.group('day'))}일, "
        f"{meridiem} {spoken_hour}시 {minute_part}"
    )


def spoken_party_size(value: int) -> str:
    korean_count = {
        1: "한 명",
        2: "두 명",
        3: "세 명",
        4: "네 명",
        5: "다섯 명",
        6: "여섯 명",
        7: "일곱 명",
        8: "여덟 명",
        9: "아홉 명",
        10: "열 명",
    }
    return korean_count.get(value, f"{value}명")


def compact_transcript_summary(entries: list[tuple[str, str]]) -> str:
    if not entries:
        return ""
    parts = []
    for speaker, text in entries[-8:]:
        label = "직원" if speaker == "user" else "AI"
        parts.append(f"{label}: {text.strip()}")
    return mask_sensitive_text(" / ".join(parts))[:900]


def contains_confirmation_marker(text: str) -> bool:
    if not text or contains_unavailable_marker(text):
        return False
    patterns = (
        r"가능\s*(합니다|해요|하세요|합니다\.|해요\.)?",
        r"예약\s*가능",
        r"네[, ]*가능",
        r"예[, ]*가능",
        r"됩니다",
        r"돼요",
        r"좋습니다",
        r"오케이",
        r"ok",
    )
    return any(re.search(pattern, text, re.IGNORECASE) for pattern in patterns)


def contains_acknowledgement_marker(text: str) -> bool:
    if not text or contains_unavailable_marker(text):
        return False
    patterns = (
        r"알겠습니다",
        r"확인했습니다",
        r"접수",
        r"예약.*됐",
        r"예약.*완료",
        r"네$",
        r"예$",
    )
    return any(re.search(pattern, text, re.IGNORECASE) for pattern in patterns)


def contains_unavailable_marker(text: str) -> bool:
    if not text:
        return False
    patterns = (
        r"불가능",
        r"안\s*(됩니다|돼요|되세요)",
        r"안\s*될",
        r"어렵",
        r"마감",
        r"자리가?\s*없",
        r"예약.*안",
        r"안.*예약",
        r"full",
        r"unavailable",
    )
    return any(re.search(pattern, text, re.IGNORECASE) for pattern in patterns)


def alternative_time_question_answered_without_alternative(
    transcript_entries: list[tuple[str, str]],
) -> bool:
    user_text = user_answer_text_after_latest_alternative_time_question(transcript_entries)
    if not user_text:
        return False
    if contains_unavailable_marker(user_text) or contains_no_alternative_marker(user_text):
        return True
    if contains_alternative_time_marker(user_text):
        return False
    return False


def user_answer_text_after_latest_alternative_time_question(
    transcript_entries: list[tuple[str, str]],
) -> str:
    for index in range(len(transcript_entries) - 1, -1, -1):
        speaker, text = transcript_entries[index]
        if speaker == "assistant" and assistant_asked_alternative_time_question(text):
            return normalize_result_text(
                *(entry_text for entry_speaker, entry_text in transcript_entries[index + 1:] if entry_speaker == "user")
            )
    return ""


def contains_no_alternative_marker(text: str) -> bool:
    if not text:
        return False
    normalized = normalize_korean_text(text)
    return any(token in normalized for token in ("없어요", "없습니다", "없는데요", "안돼요", "안됩니다", "어렵습니다"))


def contains_alternative_time_marker(text: str) -> bool:
    if not text:
        return False
    patterns = (
        r"다른\s*시간",
        r"대체",
        r"대신",
        r"어떠세요",
        r"어떨까요",
        r"일곱\s*시",
        r"여덟\s*시",
        r"아홉\s*시",
        r"칠\s*시",
        r"팔\s*시",
        r"구\s*시",
        r"몇\s*시.*가능",
        r"\d+\s*시.*가능",
        r"그\s*시간.*안",
        r"alternative",
    )
    return any(re.search(pattern, text, re.IGNORECASE) for pattern in patterns)


def contains_assistant_needs_confirmation_marker(text: str) -> bool:
    if not text:
        return False
    return any(token in text for token in ("사용자 확인", "확인이 필요", "대안", "대체", "제안"))


def contains_assistant_unavailable_marker(text: str) -> bool:
    if not text or contains_assistant_needs_confirmation_marker(text):
        return False
    return any(token in text for token in ("불가", "어렵", "불가능"))


def contains_assistant_confirmation_marker(text: str) -> bool:
    if not text:
        return False
    patterns = (
        r"예약.*가능하다고\s*확인",
        r"가능하다고\s*확인",
        r"예약.*확정",
        r"예약.*가능합니다",
    )
    return any(re.search(pattern, text, re.IGNORECASE) for pattern in patterns)


def extract_alternative_datetime(request: RealAgentCallRequest, text: str) -> str | None:
    if not text:
        return None
    date_match = re.match(r"(?P<date>\d{4}-\d{2}-\d{2})[tT ](?P<hour>\d{2}):", request.reservation_date_time or "")
    if not date_match:
        return None
    requested_hour = int(date_match.group("hour"))
    parsed_time = parse_spoken_time(text, requested_hour)
    if parsed_time is None:
        return None
    hour, minute = parsed_time
    return f"{date_match.group('date')}T{hour:02d}:{minute:02d}:00"


def parse_spoken_time(text: str, requested_hour: int) -> tuple[int, int] | None:
    match = re.search(r"(?P<hour>\d{1,2})\s*시\s*(?P<minute>\d{1,2}\s*분|반)?", text)
    if match:
        hour = int(match.group("hour"))
        minute_token = match.group("minute") or ""
        minute = 30 if "반" in minute_token else safe_minute(minute_token)
        return normalize_spoken_hour(hour, requested_hour), minute

    korean_hour = {
        "한": 1,
        "두": 2,
        "세": 3,
        "네": 4,
        "다섯": 5,
        "여섯": 6,
        "일곱": 7,
        "여덟": 8,
        "아홉": 9,
        "열": 10,
        "칠": 7,
        "팔": 8,
        "구": 9,
    }
    for token, hour in korean_hour.items():
        pattern = rf"{token}\s*시\s*(?P<half>반)?"
        match = re.search(pattern, text)
        if match:
            minute = 30 if match.group("half") else 0
            return normalize_spoken_hour(hour, requested_hour), minute
    return None


def safe_minute(value: str) -> int:
    digits = "".join(ch for ch in value if ch.isdigit())
    if not digits:
        return 0
    minute = int(digits)
    return minute if 0 <= minute <= 59 else 0


def normalize_spoken_hour(hour: int, requested_hour: int) -> int:
    if requested_hour >= 12 and 1 <= hour <= 11:
        return hour + 12
    return hour


def contains_name_or_phone_request(text: str) -> bool:
    if not text:
        return False
    return contains_name_request(text) or contains_phone_request(text)


def contains_name_request(text: str) -> bool:
    if not text:
        return False
    return any(token in text for token in ("성함", "이름", "예약자"))


def contains_phone_request(text: str) -> bool:
    if not text:
        return False
    return any(token in text for token in ("전화번호", "연락처", "번호"))


def contains_ambiguous_marker(text: str) -> bool:
    if not text:
        return False
    return any(token in text for token in ("다시", "뭐라고", "무슨", "들리", "들립", "잠시", "확인"))


def closing_response_reason(ai_result: dict[str, Any]) -> str:
    if str(ai_result.get("resultStatus") or "").strip().upper() == "CONFIRMED":
        return "closing_after_confirmed_result"
    return "closing_after_non_confirmed_result"


def accepted_result_tool_response(result_status: Any = "CONFIRMED") -> str:
    normalized_status = str(result_status or "").strip().upper()
    next_action = (
        "Tell the restaurant: 네, 확인 감사합니다. 그 시간에 방문하겠습니다. 좋은 하루 되세요."
        if normalized_status == "CONFIRMED"
        else "Tell the restaurant: 네, 확인 감사합니다. 확인 후 다시 연락드리겠습니다. 좋은 하루 되세요."
    )
    return json.dumps(
        {
            "accepted": True,
            "nextAction": next_action,
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


def safe_log(event: str, **fields: Any) -> None:
    safe_fields = {key: mask_sensitive_text(value) for key, value in fields.items()}
    print(
        json.dumps(
            {
                "event": event,
                **safe_fields,
            },
            ensure_ascii=False,
            separators=(",", ":"),
        ),
        flush=True,
    )


def mask_sensitive_text(value: Any) -> Any:
    if not isinstance(value, str):
        return value
    masked = re.sub(r"(?i)bearer\s+[a-z0-9._~+/=-]+", "Bearer ***", value)
    masked = re.sub(r"sk-[A-Za-z0-9_-]{8,}", "sk-***", masked)
    masked = re.sub(r"\+?\d[\d -]{6,}\d", "***PHONE***", masked)
    return masked


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


def should_wait_for_confirmed_result_stability(
    result_status: Any,
    latest_user_transcript_at: float | None,
    now: float | None = None,
    stability_seconds: float | None = None,
) -> bool:
    if str(result_status).strip().upper() != "CONFIRMED":
        return False
    if latest_user_transcript_at is None:
        return False
    stability_seconds = (
        CONFIRMED_RESULT_STABILITY_SECONDS
        if stability_seconds is None
        else stability_seconds
    )
    if stability_seconds <= 0:
        return False
    current_time = asyncio.get_running_loop().time() if now is None else now
    return current_time - latest_user_transcript_at < stability_seconds


def final_result_transcript_rejection_reason(
    request: RealAgentCallRequest,
    result_status: Any,
    transcript_entries: list[tuple[str, str]],
) -> str | None:
    normalized_status = str(result_status).strip().upper()
    if normalized_status != "CONFIRMED":
        return None
    if not confirmed_availability_answer_after_question(transcript_entries):
        return (
            "CONFIRMED requires a clear customer confirmation after the assistant asks "
            "the requested reservation date/time and party size. Branch confirmation alone is not enough."
        )
    name_or_phone_request = latest_name_or_phone_request_after_availability(transcript_entries)
    if name_or_phone_request is None:
        return None
    request_index, request_text = name_or_phone_request
    if not assistant_provided_requested_name_or_phone_after(
        request,
        transcript_entries,
        request_index,
        request_text,
    ):
        return (
            "The restaurant requested reservation name or contact after confirming availability. "
            "Provide the requested information before submitting CONFIRMED."
        )
    if not customer_acknowledged_after_assistant_info(transcript_entries, request_index):
        return (
            "The restaurant requested reservation name or contact. Wait for the staff to acknowledge "
            "the provided reservation information before submitting CONFIRMED."
        )
    return None


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
