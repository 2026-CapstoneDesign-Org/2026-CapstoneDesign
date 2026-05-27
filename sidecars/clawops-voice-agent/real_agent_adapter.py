"""Real-agent adapter dependency gate.

This module is a skeleton for a future ClawOps/OpenAI adapter. It performs no
SDK imports, API calls, Voice Agent execution, phone calls, or secret reads.
"""

from __future__ import annotations

import importlib.util
from dataclasses import dataclass, field
from typing import Callable


RUNTIME_MODE_REAL_AGENT = "real-agent"
REQUIRED_SECRET_NAMES = (
    "CLAWOPS_API_KEY",
    "CLAWOPS_ACCOUNT_ID",
    "CLAWOPS_FROM_NUMBER",
    "OPENAI_API_KEY",
)
REQUIRED_SDK_MODULES = ("clawops", "openai")

REAL_AGENT_DISABLED = "REAL_AGENT_DISABLED"
APPROVAL_REQUIRED = "APPROVAL_REQUIRED"
SPRING_PREFLIGHT_REQUIRED = "SPRING_PREFLIGHT_REQUIRED"
SDK_NOT_INSTALLED = "SDK_NOT_INSTALLED"
SECRET_MISSING = "SECRET_MISSING"
TARGET_NOT_ALLOWLISTED = "TARGET_NOT_ALLOWLISTED"
REAL_AGENT_NOT_IMPLEMENTED = "REAL_AGENT_NOT_IMPLEMENTED"


@dataclass(frozen=True)
class RealAgentDependencyContext:
    runtime_mode: str
    real_agent_enabled: bool
    approval_required: bool
    approval_granted: bool
    spring_preflight_required: bool
    spring_preflight_passed: bool
    target_phone_number: str = field(repr=False)
    allowed_target_numbers: tuple[str, ...] = field(default_factory=tuple, repr=False)
    required_secret_names: tuple[str, ...] = REQUIRED_SECRET_NAMES
    present_secret_names: tuple[str, ...] = field(default_factory=tuple)


@dataclass(frozen=True)
class RealAgentGateResult:
    allowed: bool
    block_reasons: tuple[str, ...]
    missing_secret_names: tuple[str, ...] = ()
    sdk_installed: bool = False


class RealAgentBlockedError(RuntimeError):
    def __init__(self, gate_result: RealAgentGateResult) -> None:
        super().__init__(",".join(gate_result.block_reasons))
        self.gate_result = gate_result


def evaluate_real_agent_gate(
    context: RealAgentDependencyContext,
    sdk_probe: Callable[[], bool] | None = None,
    implementation_ready: bool = False,
) -> RealAgentGateResult:
    reasons: list[str] = []
    if context.runtime_mode != RUNTIME_MODE_REAL_AGENT or not context.real_agent_enabled:
        reasons.append(REAL_AGENT_DISABLED)
    if context.approval_required and not context.approval_granted:
        reasons.append(APPROVAL_REQUIRED)
    if context.spring_preflight_required and not context.spring_preflight_passed:
        reasons.append(SPRING_PREFLIGHT_REQUIRED)
    if normalize_phone(context.target_phone_number) not in normalized_allowed_numbers(context.allowed_target_numbers):
        reasons.append(TARGET_NOT_ALLOWLISTED)

    missing_secret_names = tuple(
        name for name in context.required_secret_names
        if name not in set(context.present_secret_names)
    )
    if missing_secret_names:
        reasons.append(SECRET_MISSING)

    probe = sdk_probe or sdk_dependencies_installed
    sdk_installed = probe()
    if not sdk_installed:
        reasons.append(SDK_NOT_INSTALLED)

    if not reasons and not implementation_ready:
        reasons.append(REAL_AGENT_NOT_IMPLEMENTED)
    return RealAgentGateResult(
        allowed=not reasons,
        block_reasons=tuple(dict.fromkeys(reasons)),
        missing_secret_names=missing_secret_names,
        sdk_installed=sdk_installed,
    )


class RealAgentAdapter:
    """Future adapter boundary. It is intentionally non-executable for now."""

    def __init__(
        self,
        dependency_context: RealAgentDependencyContext,
        sdk_probe: Callable[[], bool] | None = None,
    ) -> None:
        self.dependency_context = dependency_context
        self.sdk_probe = sdk_probe

    def evaluate(self) -> RealAgentGateResult:
        return evaluate_real_agent_gate(self.dependency_context, self.sdk_probe)

    def run_reservation_call(self, request):
        gate_result = self.evaluate()
        raise RealAgentBlockedError(gate_result)


def sdk_dependencies_installed() -> bool:
    return all(importlib.util.find_spec(module_name) is not None for module_name in REQUIRED_SDK_MODULES)


def normalize_phone(value: str | None) -> str:
    if not value:
        return ""
    return "".join(ch for ch in value if ch.isdigit() or ch == "+")


def normalized_allowed_numbers(values: tuple[str, ...]) -> set[str]:
    return {normalize_phone(value) for value in values if normalize_phone(value)}
