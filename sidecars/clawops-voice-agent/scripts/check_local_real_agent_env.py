#!/usr/bin/env python3
"""Check local Spring/sidecar real-agent env separation.

This script reads env files or process env and prints only booleans, counts, and
safe status names. It must not print API keys, signing keys, JWTs, or raw phone
numbers. It never starts Spring, starts the sidecar, calls SDKs, or places calls.
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path


DEFAULT_SPRING_ENV_FILE = ".env.spring.local"
DEFAULT_SIDECAR_ENV_FILE = ".env.sidecar.real-agent.local"
SPRING_FORBIDDEN_SECRET_NAMES = (
    "CLAWOPS_API_KEY",
    "CLAWOPS_ACCOUNT_ID",
    "CLAWOPS_FROM_NUMBER",
    "OPENAI_API_KEY",
)
SIDECAR_REQUIRED_SECRET_NAMES = (
    "CLAWOPS_API_KEY",
    "CLAWOPS_ACCOUNT_ID",
    "CLAWOPS_FROM_NUMBER",
    "OPENAI_API_KEY",
)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--spring-env-file", default=DEFAULT_SPRING_ENV_FILE)
    parser.add_argument("--sidecar-env-file", default=DEFAULT_SIDECAR_ENV_FILE)
    args = parser.parse_args()

    spring = load_env(Path(args.spring_env_file))
    sidecar = load_env(Path(args.sidecar_env_file))
    report(spring, sidecar, Path(args.spring_env_file), Path(args.sidecar_env_file))
    return 0


def load_env(path: Path) -> dict[str, str]:
    values = dict(os.environ)
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


def report(spring: dict[str, str], sidecar: dict[str, str], spring_path: Path, sidecar_path: Path) -> None:
    profiles = split_csv(spring.get("SPRING_PROFILES_ACTIVE", ""))
    spring_allowlist = normalized_numbers(spring.get("RESERVATION_PROVIDER_CALL_ALLOWED_NUMBERS", ""))
    spring_override = normalize_phone(spring.get("RESERVATION_PROVIDER_DEV_TARGET_PHONE_OVERRIDE_NUMBER", ""))
    sidecar_allowlist = normalized_numbers(sidecar.get("SIDECAR_ALLOWED_TARGET_NUMBERS", ""))
    signing_keys_match = bool(spring.get("CLAWOPS_SIDECAR_INTERNAL_SIGNING_KEY")) and (
        spring.get("CLAWOPS_SIDECAR_INTERNAL_SIGNING_KEY") == sidecar.get("SIDECAR_INTERNAL_SIGNING_KEY")
    )
    spring_internal_keys_match = bool(sidecar.get("SPRING_INTERNAL_SIGNING_KEY")) and (
        sidecar.get("SPRING_INTERNAL_SIGNING_KEY") == spring.get("CLAWOPS_SIDECAR_INTERNAL_SIGNING_KEY")
    )
    forbidden_in_spring = [name for name in SPRING_FORBIDDEN_SECRET_NAMES if has_value(spring.get(name))]
    missing_sidecar_secrets = [name for name in SIDECAR_REQUIRED_SECRET_NAMES if not has_value(sidecar.get(name))]

    print(f"spring.envFile.exists={spring_path.exists()}")
    print(f"sidecar.envFile.exists={sidecar_path.exists()}")
    print(f"spring.profile.dev={contains(profiles, 'dev')}")
    print(f"spring.profile.prod={contains(profiles, 'prod')}")
    print(f"spring.providerModeClawOps={equals(spring.get('RESERVATION_PROVIDER_MODE'), 'CLAWOPS')}")
    print(f"spring.providerRuntimeSidecar={equals(spring.get('RESERVATION_PROVIDER_RUNTIME'), 'CLAWOPS_SIDECAR')}")
    print(f"spring.callingEnabled={truthy(spring.get('RESERVATION_PROVIDER_CALLING_ENABLED'))}")
    print(f"spring.realCallEnabled={truthy(spring.get('RESERVATION_PROVIDER_REAL_CALL_ENABLED'))}")
    print(f"spring.schedulerEnabled={truthy(spring.get('RESERVATION_SCHEDULER_ENABLED'))}")
    print(f"spring.allowlist.count={len(spring_allowlist)}")
    print(f"spring.devTargetOverride.enabled={truthy(spring.get('RESERVATION_PROVIDER_DEV_TARGET_PHONE_OVERRIDE_ENABLED'))}")
    print(f"spring.devTargetOverride.present={bool(spring_override)}")
    print(f"spring.devTargetOverride.matchesAllowlist={bool(spring_override and spring_override in spring_allowlist)}")
    print(f"spring.forbiddenSidecarSecretNames={','.join(forbidden_in_spring)}")

    print(f"sidecar.profileDev={equals(sidecar.get('SIDECAR_PROFILE'), 'dev')}")
    print(f"sidecar.runtimeRealAgent={equals(sidecar.get('SIDECAR_RUNTIME_MODE'), 'real-agent')}")
    print(f"sidecar.realCallEnabled={truthy(sidecar.get('SIDECAR_REAL_CALL_ENABLED'))}")
    print(f"sidecar.realAgentEnabled={truthy(sidecar.get('SIDECAR_REAL_AGENT_ENABLED'))}")
    print(f"sidecar.approvalRequired={truthy(sidecar.get('SIDECAR_REAL_AGENT_APPROVAL_REQUIRED'))}")
    print(f"sidecar.approvalGranted={truthy(sidecar.get('SIDECAR_REAL_AGENT_APPROVAL_GRANTED'))}")
    print(f"sidecar.springPreflightRequired={truthy(sidecar.get('SIDECAR_REQUIRE_SPRING_PREFLIGHT'))}")
    print(f"sidecar.allowlist.count={len(sidecar_allowlist)}")
    print(f"sidecar.allowlist.matchesSpring={bool(spring_allowlist and spring_allowlist == sidecar_allowlist)}")
    print(f"sidecar.requiredSecretNames.missing={','.join(missing_sidecar_secrets)}")
    print(f"internalSigning.springToSidecar.match={signing_keys_match}")
    print(f"internalSigning.sidecarToSpring.match={spring_internal_keys_match}")

    blockers = blockers_for_real_call(
        profiles,
        spring,
        sidecar,
        spring_allowlist,
        spring_override,
        sidecar_allowlist,
        forbidden_in_spring,
        missing_sidecar_secrets,
        signing_keys_match,
        spring_internal_keys_match,
    )
    print(f"realAgentEnvCandidate={not blockers}")
    print(f"blockReasons={','.join(blockers)}")


def blockers_for_real_call(
    profiles: list[str],
    spring: dict[str, str],
    sidecar: dict[str, str],
    spring_allowlist: set[str],
    spring_override: str,
    sidecar_allowlist: set[str],
    forbidden_in_spring: list[str],
    missing_sidecar_secrets: list[str],
    signing_keys_match: bool,
    spring_internal_keys_match: bool,
) -> list[str]:
    blockers: list[str] = []
    require(contains(profiles, "dev"), "SPRING_DEV_PROFILE_MISSING", blockers)
    require(not contains(profiles, "prod"), "SPRING_PROD_PROFILE_PRESENT", blockers)
    require(equals(spring.get("RESERVATION_PROVIDER_MODE"), "CLAWOPS"), "SPRING_PROVIDER_MODE_NOT_CLAWOPS", blockers)
    require(
        equals(spring.get("RESERVATION_PROVIDER_RUNTIME"), "CLAWOPS_SIDECAR"),
        "SPRING_PROVIDER_RUNTIME_NOT_SIDECAR",
        blockers,
    )
    require(truthy(spring.get("RESERVATION_PROVIDER_CALLING_ENABLED")), "SPRING_CALLING_DISABLED", blockers)
    require(truthy(spring.get("RESERVATION_PROVIDER_REAL_CALL_ENABLED")), "SPRING_REAL_CALL_DISABLED", blockers)
    require(not truthy(spring.get("RESERVATION_SCHEDULER_ENABLED")), "SPRING_SCHEDULER_ENABLED", blockers)
    require(len(spring_allowlist) == 1, "SPRING_ALLOWLIST_NOT_SINGLE", blockers)
    require(bool(spring_override and spring_override in spring_allowlist), "SPRING_OVERRIDE_NOT_ALLOWLISTED", blockers)
    require(not forbidden_in_spring, "SPRING_HAS_SIDECAR_SECRET_NAMES", blockers)

    require(equals(sidecar.get("SIDECAR_PROFILE"), "dev"), "SIDECAR_PROFILE_NOT_DEV", blockers)
    require(equals(sidecar.get("SIDECAR_RUNTIME_MODE"), "real-agent"), "SIDECAR_RUNTIME_NOT_REAL_AGENT", blockers)
    require(truthy(sidecar.get("SIDECAR_REAL_CALL_ENABLED")), "SIDECAR_REAL_CALL_DISABLED", blockers)
    require(truthy(sidecar.get("SIDECAR_REAL_AGENT_ENABLED")), "SIDECAR_REAL_AGENT_DISABLED", blockers)
    require(truthy(sidecar.get("SIDECAR_REAL_AGENT_APPROVAL_REQUIRED")), "SIDECAR_APPROVAL_NOT_REQUIRED", blockers)
    require(truthy(sidecar.get("SIDECAR_REAL_AGENT_APPROVAL_GRANTED")), "SIDECAR_APPROVAL_NOT_GRANTED", blockers)
    require(truthy(sidecar.get("SIDECAR_REQUIRE_SPRING_PREFLIGHT")), "SIDECAR_PREFLIGHT_NOT_REQUIRED", blockers)
    require(len(sidecar_allowlist) == 1, "SIDECAR_ALLOWLIST_NOT_SINGLE", blockers)
    require(bool(spring_allowlist and spring_allowlist == sidecar_allowlist), "SIDECAR_ALLOWLIST_MISMATCH", blockers)
    require(not missing_sidecar_secrets, "SIDECAR_SECRET_MISSING", blockers)
    require(signing_keys_match, "SPRING_TO_SIDECAR_SIGNING_KEY_MISMATCH", blockers)
    require(spring_internal_keys_match, "SIDECAR_TO_SPRING_SIGNING_KEY_MISMATCH", blockers)
    return blockers


def require(condition: bool, reason: str, blockers: list[str]) -> None:
    if not condition:
        blockers.append(reason)


def split_csv(value: str) -> list[str]:
    return [item.strip() for item in value.split(",") if item.strip()]


def normalized_numbers(value: str) -> set[str]:
    return {normalized for item in split_csv(value) if (normalized := normalize_phone(item))}


def normalize_phone(value: str | None) -> str:
    return "".join(ch for ch in (value or "") if ch.isdigit() or ch == "+")


def has_value(value: str | None) -> bool:
    return bool(value and value.strip())


def truthy(value: str | None) -> bool:
    return (value or "").strip().lower() in {"1", "true", "yes", "y", "on"}


def equals(value: str | None, expected: str) -> bool:
    return (value or "").strip().lower() == expected.lower()


def contains(values: list[str], expected: str) -> bool:
    return any(item.lower() == expected.lower() for item in values)


if __name__ == "__main__":
    raise SystemExit(main())
