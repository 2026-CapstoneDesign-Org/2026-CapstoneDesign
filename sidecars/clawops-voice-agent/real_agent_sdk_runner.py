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
from dataclasses import dataclass, field
from typing import Any, Callable

from real_agent_adapter import REAL_AGENT_NOT_IMPLEMENTED, RealAgentBlockedError, RealAgentGateResult
from real_agent_interface import RealAgentCallRequest, RealAgentCallResult


SDK_MODULE_NAMES = ("clawops", "openai")
SDK_NOT_READY = "SDK_NOT_READY"
DEFAULT_PROMPT_PATH = pathlib.Path(__file__).resolve().parent / "prompts" / "reservation_agent_prompt.md"
WAIT_RESULT_TOOL_SUBMITTED = "RESULT_TOOL_SUBMITTED"
WAIT_CALL_ENDED = "CALL_ENDED"


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
        "- Before ending the call, call `submit_reservation_call_result` exactly once.",
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
        "builtinTools": "NONE",
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
        builtin_tools=BuiltinTool.NONE,
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

        Call this exactly once before saying goodbye or ending the call. Use
        CONFIRMED only when the restaurant clearly accepted the original
        requested date-time and party size. Use NEEDS_CONFIRMATION for
        alternative times, deposits, extra information, special conditions, or
        ambiguous outcomes. Use UNAVAILABLE only when the restaurant clearly
        cannot accept the requested reservation and gives no usable alternative.
        Use FAILED for connection or technical failure.
        """
        result_box.clear()
        result_box.update({
            "resultStatus": resultStatus,
            "summary": summary,
            "confirmedDateTime": none_if_blank(confirmedDateTime),
            "partySize": partySize if partySize > 0 else None,
            "reservationNameProvided": reservationNameProvided,
            "phoneNumberProvided": phoneNumberProvided,
            "restaurantRequestedNameOrPhone": restaurantRequestedNameOrPhone,
            "alternativeTimeSuggested": alternativeTimeSuggested,
            "alternativeDateTime": none_if_blank(alternativeDateTime),
            "failureReason": none_if_blank(failureReason),
            "transcriptSummary": none_if_blank(transcriptSummary),
        })
        result_event.set()
        return json.dumps({"accepted": True}, separators=(",", ":"))

    async def on_failed(call_session: Any, reason: str) -> None:
        failure_box["reason"] = reason or "CALL_CONNECTION_FAILED"

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
            try:
                await call_session.hangup()
            except Exception:
                pass
        if not ai_result:
            ai_result = failed_result(
                "AI_RESULT_TOOL_MISSING",
                "AI result tool was not submitted before the call ended.",
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
        ai_result = failed_result(type(exception).__name__, "Real-agent SDK call failed.")
        return RealAgentCallResult(ai_result, request.sidecar_call_id, None, ai_result["failureReason"], True)
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


def none_if_blank(value: str | None) -> str | None:
    if value is None:
        return None
    stripped = str(value).strip()
    return stripped or None
