import contextlib
import io
import json
import pathlib
import sys
import unittest
from unittest.mock import patch


sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "scripts"))

import check_dev_real_agent_readiness as readiness  # noqa: E402


FAKE_TOKEN = "fake-admin-token"
FAKE_SIGNING_KEY = "fake-internal-signing-key"
PLACEHOLDER_PHONE_NUMBER = "+15550100001"


class DevRealAgentReadinessTest(unittest.TestCase):
    def test_main_reports_public_and_skip_status_without_secret_values(self):
        def fake_request(method, url, body=None, headers=None):
            return {"status": 200, "body": "{}"}

        output = run_main(fake_request, {"DEV_BASE_URL": "https://dev.example.test"})

        self.assertIn("swagger.httpStatus=200", output)
        self.assertIn("clawopsInbound.reachable=True", output)
        self.assertIn("springPreflight.skipped=true", output)
        self.assertIn("sidecar.readiness.skipped=true", output)
        self.assertNotIn(FAKE_TOKEN, output)
        self.assertNotIn(FAKE_SIGNING_KEY, output)
        self.assertNotIn(PLACEHOLDER_PHONE_NUMBER, output)

    def test_preflight_prints_block_reason_names_without_token_or_target(self):
        def fake_request(method, url, body=None, headers=None):
            self.assertNotIn(FAKE_TOKEN, body.decode("utf-8") if body else "")
            if url.endswith(readiness.PREFLIGHT_PATH):
                self.assertEqual(headers["Authorization"], f"Bearer {FAKE_TOKEN}")
                return {
                    "status": 200,
                    "body": json.dumps(
                        {
                            "realCallCandidate": False,
                            "blockReasons": ["CALLING_DISABLED", "REAL_CALL_DISABLED"],
                            "devProfileActive": True,
                            "prodProfileActive": False,
                            "callingEnabled": False,
                            "realCallEnabled": False,
                            "schedulerEnabled": False,
                            "allowlistPresent": True,
                            "targetNumberAllowlisted": True,
                        },
                        separators=(",", ":"),
                    ),
                }
            return {"status": 200, "body": "{}"}

        output = run_main(
            fake_request,
            {
                "DEV_ADMIN_BEARER_TOKEN": FAKE_TOKEN,
                "DEV_PREFLIGHT_TARGET_PHONE_NUMBER": PLACEHOLDER_PHONE_NUMBER,
            },
        )

        self.assertIn('springPreflight.blockReasons=["CALLING_DISABLED", "REAL_CALL_DISABLED"]', output)
        self.assertIn("springPreflight.realCallCandidate=False", output)
        self.assertNotIn(FAKE_TOKEN, output)
        self.assertNotIn(PLACEHOLDER_PHONE_NUMBER, output)

    def test_sidecar_readiness_prints_safe_runtime_status_only(self):
        captured_headers = {}

        def fake_request(method, url, body=None, headers=None):
            if url.endswith(readiness.SIDECAR_READINESS_PATH):
                captured_headers.update(headers)
                return {
                    "status": 200,
                    "body": json.dumps(
                        {
                            "ready": True,
                            "runtimeMode": "real-agent",
                            "realCallEnabled": True,
                            "realAgentEnabled": True,
                            "springPreflightRequired": True,
                        },
                        separators=(",", ":"),
                    ),
                }
            return {"status": 200, "body": "{}"}

        output = run_main(
            fake_request,
            {
                "SIDECAR_BASE_URL": "http://127.0.0.1:8091",
                "SIDECAR_INTERNAL_SIGNING_KEY": FAKE_SIGNING_KEY,
            },
        )

        self.assertIn(readiness.SIGNATURE_HEADER, captured_headers)
        self.assertIn("sidecar.readiness.ready=True", output)
        self.assertIn("sidecar.readiness.runtimeMode=real-agent", output)
        self.assertNotIn(FAKE_SIGNING_KEY, output)
        self.assertNotIn(captured_headers[readiness.SIGNATURE_HEADER], output)


def run_main(fake_request, env):
    buffer = io.StringIO()
    with patch("check_dev_real_agent_readiness.request", fake_request):
        with patch.dict("os.environ", env, clear=True):
            with contextlib.redirect_stdout(buffer):
                readiness.main()
    return buffer.getvalue()


if __name__ == "__main__":
    unittest.main()
