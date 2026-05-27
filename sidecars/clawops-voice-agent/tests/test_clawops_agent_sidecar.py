import json
import os
import pathlib
import sys
import threading
import unittest
from datetime import datetime, timezone
from http.server import HTTPServer
from unittest.mock import patch
from urllib.error import HTTPError
from urllib.request import Request, urlopen

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

from clawops_agent_sidecar import (
    CALLS_PATH,
    RUNTIME_MODE_DRY_RUN,
    RUNTIME_MODE_REAL_AGENT,
    READINESS_PATH,
    SIGNATURE_HEADER,
    TIMESTAMP_HEADER,
    SidecarConfig,
    build_real_agent_call_request,
    make_handler,
    parse_runtime_mode,
    sign,
)
from real_agent_adapter import (  # noqa: E402
    APPROVAL_REQUIRED,
    REAL_AGENT_DISABLED,
    REAL_AGENT_NOT_IMPLEMENTED,
    SDK_NOT_INSTALLED,
    SECRET_MISSING,
    SPRING_PREFLIGHT_REQUIRED,
)


PLACEHOLDER_PHONE_NUMBER = "+15550100001"
SIGNING_KEY = "fake-sidecar-signing-key"
SPRING_SIGNING_KEY = "fake-spring-signing-key"


class DryRunSidecarServer:
    def __init__(self, config, sdk_probe=None):
        self.server = HTTPServer(("127.0.0.1", 0), make_handler(config, sdk_probe))
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)

    def __enter__(self):
        self.thread.start()
        return self

    def __exit__(self, exc_type, exc, tb):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)

    @property
    def base_url(self):
        return f"http://127.0.0.1:{self.server.server_address[1]}"


class ClawOpsAgentSidecarTest(unittest.TestCase):
    def test_readiness_returns_dry_run_status(self):
        with DryRunSidecarServer(config()) as server:
            body = request_json(server.base_url + READINESS_PATH, method="GET")

        self.assertTrue(body["ready"])
        self.assertEqual(body["profile"], "test")
        self.assertEqual(body["runtimeMode"], RUNTIME_MODE_DRY_RUN)
        self.assertFalse(body["realCallEnabled"])
        self.assertFalse(body["realAgentEnabled"])
        self.assertTrue(body["realAgentApprovalRequired"])
        self.assertTrue(body["springPreflightRequired"])
        self.assertEqual(body["allowlistCount"], 1)
        self.assertFalse(body["clawOpsConfigured"])
        self.assertFalse(body["openAiConfigured"])
        self.assertEqual(body["agentRuntime"], RUNTIME_MODE_DRY_RUN)
        self.assertNotIn(SIGNING_KEY, json.dumps(body))

    def test_real_agent_candidate_mode_keeps_readiness_and_rejects_call_with_preview(self):
        real_agent_config = config(runtime_mode=RUNTIME_MODE_REAL_AGENT, real_agent_enabled=True)
        present_env = {
            "CLAWOPS_API_KEY": "present",
            "CLAWOPS_ACCOUNT_ID": "present",
            "CLAWOPS_FROM_NUMBER": "present",
            "OPENAI_API_KEY": "present",
        }
        with patch.dict(os.environ, present_env, clear=True):
            with DryRunSidecarServer(real_agent_config, sdk_probe=lambda: True) as server:
                readiness = request_json(server.base_url + READINESS_PATH, method="GET")
                with self.assertRaises(HTTPError) as context:
                    request_json(server.base_url + CALLS_PATH, payload=real_agent_payload())

        self.assertTrue(readiness["ready"])
        self.assertEqual(readiness["runtimeMode"], RUNTIME_MODE_REAL_AGENT)
        self.assertTrue(readiness["realAgentEnabled"])
        self.assertEqual(readiness["warnings"], [])
        self.assertEqual(context.exception.code, 403)
        error_body = json.loads(context.exception.read().decode("utf-8"))
        gate_preview = error_body["realAgentGatePreview"]
        serialized = json.dumps(error_body)
        self.assertEqual(error_body["error"], "real_agent_blocked")
        self.assertEqual(gate_preview["runtimeMode"], RUNTIME_MODE_REAL_AGENT)
        self.assertTrue(gate_preview["realAgentEnabled"])
        self.assertFalse(gate_preview["allowed"])
        self.assertIn(APPROVAL_REQUIRED, gate_preview["blockReasons"])
        self.assertIn(SPRING_PREFLIGHT_REQUIRED, gate_preview["blockReasons"])
        self.assertNotIn(REAL_AGENT_DISABLED, gate_preview["blockReasons"])
        self.assertNotIn(SECRET_MISSING, gate_preview["blockReasons"])
        self.assertNotIn(SDK_NOT_INSTALLED, gate_preview["blockReasons"])
        self.assertNotIn(REAL_AGENT_NOT_IMPLEMENTED, gate_preview["blockReasons"])
        self.assertNotIn(PLACEHOLDER_PHONE_NUMBER, serialized)
        self.assertNotIn(SIGNING_KEY, serialized)

    def test_runtime_mode_parser_defaults_to_dry_run(self):
        self.assertEqual(parse_runtime_mode(None), RUNTIME_MODE_DRY_RUN)
        self.assertEqual(parse_runtime_mode(""), RUNTIME_MODE_DRY_RUN)
        self.assertEqual(parse_runtime_mode("unexpected"), RUNTIME_MODE_DRY_RUN)
        self.assertEqual(parse_runtime_mode("real-agent"), RUNTIME_MODE_REAL_AGENT)

    def test_call_endpoint_rejects_bad_signature(self):
        with DryRunSidecarServer(config()) as server:
            with self.assertRaises(HTTPError) as context:
                request_json(
                    server.base_url + CALLS_PATH,
                    payload=call_payload(),
                    headers={SIGNATURE_HEADER: "bad-signature", TIMESTAMP_HEADER: now_timestamp()},
                )

        self.assertEqual(context.exception.code, 401)

    def test_call_endpoint_accepts_allowlisted_dry_run(self):
        with patch.dict(os.environ, {}, clear=True):
            with DryRunSidecarServer(config(), sdk_probe=lambda: False) as server:
                body = request_json(server.base_url + CALLS_PATH, payload=call_payload())

        serialized = json.dumps(body)
        self.assertTrue(body["accepted"])
        self.assertEqual(body["provider"], "CLAWOPS_SIDECAR")
        self.assertEqual(body["providerStatus"], "DRY_RUN_ACCEPTED")
        self.assertEqual(body["idempotencyKey"], "clawops-sidecar-dry-run:100")
        self.assertTrue(body["springEventPreview"]["ready"])
        self.assertEqual(body["springEventPreview"]["endpointPath"], "/internal/reservations/provider-events/clawops-agent")
        self.assertEqual(body["springEventPreview"]["eventType"], "RESERVATION_CONFIRMED")
        self.assertEqual(body["springEventPreview"]["providerStatus"], "AI_CONFIRMED")
        self.assertIn(SIGNATURE_HEADER, body["springEventPreview"]["headerNames"])
        self.assertIn("****0001", body["message"])
        self.assertIn("realAgentGatePreview", body)
        self.assertNotIn(PLACEHOLDER_PHONE_NUMBER, serialized)
        self.assertNotIn(SIGNING_KEY, serialized)
        self.assertNotIn(SPRING_SIGNING_KEY, serialized)

    def test_call_endpoint_includes_real_agent_gate_preview(self):
        with patch.dict(os.environ, {}, clear=True):
            with DryRunSidecarServer(config(), sdk_probe=lambda: False) as server:
                body = request_json(server.base_url + CALLS_PATH, payload=call_payload())

        preview = body["realAgentGatePreview"]
        serialized = json.dumps(preview)

        self.assertEqual(preview["runtimeMode"], RUNTIME_MODE_DRY_RUN)
        self.assertFalse(preview["realAgentEnabled"])
        self.assertTrue(preview["approvalRequired"])
        self.assertTrue(preview["springPreflightRequired"])
        self.assertFalse(preview["allowed"])
        self.assertTrue(preview["blocked"])
        self.assertFalse(preview["sdkInstalled"])
        self.assertIn(REAL_AGENT_DISABLED, preview["blockReasons"])
        self.assertIn(APPROVAL_REQUIRED, preview["blockReasons"])
        self.assertIn(SPRING_PREFLIGHT_REQUIRED, preview["blockReasons"])
        self.assertIn(SECRET_MISSING, preview["blockReasons"])
        self.assertIn(SDK_NOT_INSTALLED, preview["blockReasons"])
        self.assertEqual(
            preview["missingRequiredEnvNames"],
            ["CLAWOPS_API_KEY", "CLAWOPS_ACCOUNT_ID", "CLAWOPS_FROM_NUMBER", "OPENAI_API_KEY"],
        )
        self.assertNotIn(PLACEHOLDER_PHONE_NUMBER, serialized)
        self.assertNotIn(SIGNING_KEY, serialized)
        self.assertNotIn(SPRING_SIGNING_KEY, serialized)

    def test_call_payload_builds_runner_request_without_exposing_raw_phone_in_repr(self):
        request = build_real_agent_call_request(call_payload(), PLACEHOLDER_PHONE_NUMBER)

        self.assertEqual(request.reservation_id, 100)
        self.assertEqual(request.sidecar_call_id, "dry-run-sidecar-100")
        self.assertEqual(request.target_phone_mask, "****0001")
        self.assertEqual(request.restaurant_name, "예약식당")
        self.assertEqual(request.reservation_date_time, "2026-06-01T19:00:00")
        self.assertEqual(request.party_size, 4)
        self.assertNotIn(PLACEHOLDER_PHONE_NUMBER, repr(request))

    def test_call_endpoint_can_preview_non_confirmed_sample(self):
        payload = call_payload()
        payload["dryRunResultSample"] = "alternative-time"
        with DryRunSidecarServer(config()) as server:
            body = request_json(server.base_url + CALLS_PATH, payload=payload)

        preview = body["springEventPreview"]
        self.assertTrue(preview["ready"])
        self.assertEqual(preview["eventType"], "RESERVATION_NEEDS_CONFIRMATION")
        self.assertEqual(preview["providerStatus"], "AI_NEEDS_CONFIRMATION_ALTERNATIVE_TIME")
        self.assertNotEqual(preview["eventType"], "RESERVATION_CONFIRMED")

    def test_call_endpoint_rejects_non_allowlisted_target(self):
        payload = call_payload()
        payload["targetPhoneNumber"] = "+15550100999"
        with DryRunSidecarServer(config()) as server:
            with self.assertRaises(HTTPError) as context:
                request_json(server.base_url + CALLS_PATH, payload=payload)

        self.assertEqual(context.exception.code, 403)
        error_body = context.exception.read().decode("utf-8")
        self.assertNotIn("+15550100999", error_body)
        self.assertNotIn(SIGNING_KEY, error_body)


def config(
    runtime_mode=RUNTIME_MODE_DRY_RUN,
    real_call_enabled=False,
    real_agent_enabled=False,
    real_agent_approval_granted=False,
    real_agent_approval_required=True,
    require_spring_preflight=True,
):
    return SidecarConfig(
        profile="test",
        runtime_mode=runtime_mode,
        real_call_enabled=real_call_enabled,
        real_agent_enabled=real_agent_enabled,
        real_agent_approval_granted=real_agent_approval_granted,
        real_agent_approval_required=real_agent_approval_required,
        require_spring_preflight=require_spring_preflight,
        allowed_target_numbers=(PLACEHOLDER_PHONE_NUMBER,),
        internal_signing_key=SIGNING_KEY,
        bind_host="127.0.0.1",
        port=0,
        spring_internal_base_url="",
        spring_internal_signing_key=SPRING_SIGNING_KEY,
    )


def call_payload():
    return {
        "reservationId": 100,
        "targetPhoneNumber": PLACEHOLDER_PHONE_NUMBER,
        "restaurantName": "예약식당",
        "reservationDateTime": "2026-06-01T19:00:00",
        "partySize": 4,
        "requestNote": "창가 자리",
        "idempotencyKey": "clawops-sidecar-dry-run:100",
        "dryRun": True,
    }


def real_agent_payload():
    payload = call_payload()
    payload["dryRun"] = False
    payload["springPreflightPassed"] = True
    return payload


def request_json(url, method="POST", payload=None, headers=None):
    raw_body = "" if payload is None else json.dumps(payload, separators=(",", ":"))
    timestamp = (headers or {}).get(TIMESTAMP_HEADER, now_timestamp())
    request_headers = {
        "Content-Type": "application/json",
        TIMESTAMP_HEADER: timestamp,
        SIGNATURE_HEADER: sign(SIGNING_KEY, timestamp, raw_body),
    }
    if headers:
        request_headers.update(headers)
    request = Request(
        url,
        data=None if method == "GET" else raw_body.encode("utf-8"),
        headers=request_headers,
        method=method,
    )
    with urlopen(request, timeout=2) as response:
        return json.loads(response.read().decode("utf-8"))


def now_timestamp():
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


if __name__ == "__main__":
    unittest.main()
