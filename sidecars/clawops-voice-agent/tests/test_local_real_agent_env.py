import contextlib
import io
import pathlib
import tempfile
import unittest
from unittest.mock import patch
import sys


sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "scripts"))

import check_local_real_agent_env as env_check  # noqa: E402


SPRING_SIGNING_KEY = "fake-shared-signing-key"
SIDECAR_SECRET = "fake-sidecar-secret-value"
PLACEHOLDER_PHONE_NUMBER = "+15550100001"


class LocalRealAgentEnvTest(unittest.TestCase):
    def test_checker_reports_ready_candidate_without_printing_secret_values(self):
        spring_text = "\n".join(
            [
                "SPRING_PROFILES_ACTIVE=dev,db,key",
                "RESERVATION_PROVIDER_MODE=CLAWOPS",
                "RESERVATION_PROVIDER_RUNTIME=CLAWOPS_SIDECAR",
                "RESERVATION_PROVIDER_CALLING_ENABLED=true",
                "RESERVATION_PROVIDER_REAL_CALL_ENABLED=true",
                "RESERVATION_SCHEDULER_ENABLED=false",
                f"RESERVATION_PROVIDER_CALL_ALLOWED_NUMBERS={PLACEHOLDER_PHONE_NUMBER}",
                "RESERVATION_PROVIDER_DEV_TARGET_PHONE_OVERRIDE_ENABLED=true",
                f"RESERVATION_PROVIDER_DEV_TARGET_PHONE_OVERRIDE_NUMBER={PLACEHOLDER_PHONE_NUMBER}",
                f"CLAWOPS_SIDECAR_INTERNAL_SIGNING_KEY={SPRING_SIGNING_KEY}",
            ]
        )
        sidecar_text = "\n".join(
            [
                "SIDECAR_PROFILE=dev",
                "SIDECAR_RUNTIME_MODE=real-agent",
                "SIDECAR_REAL_CALL_ENABLED=true",
                "SIDECAR_REAL_AGENT_ENABLED=true",
                "SIDECAR_REAL_AGENT_APPROVAL_REQUIRED=true",
                "SIDECAR_REAL_AGENT_APPROVAL_GRANTED=true",
                "SIDECAR_REQUIRE_SPRING_PREFLIGHT=true",
                f"SIDECAR_ALLOWED_TARGET_NUMBERS={PLACEHOLDER_PHONE_NUMBER}",
                f"SIDECAR_INTERNAL_SIGNING_KEY={SPRING_SIGNING_KEY}",
                f"SPRING_INTERNAL_SIGNING_KEY={SPRING_SIGNING_KEY}",
                f"CLAWOPS_API_KEY={SIDECAR_SECRET}",
                "CLAWOPS_ACCOUNT_ID=fake-account",
                f"CLAWOPS_FROM_NUMBER={SIDECAR_SECRET}",
                f"OPENAI_API_KEY={SIDECAR_SECRET}",
            ]
        )

        output = run_checker(spring_text, sidecar_text)

        self.assertIn("realAgentEnvCandidate=True", output)
        self.assertIn("blockReasons=", output)
        self.assertNotIn(SPRING_SIGNING_KEY, output)
        self.assertNotIn(SIDECAR_SECRET, output)
        self.assertNotIn(PLACEHOLDER_PHONE_NUMBER, output)

    def test_checker_blocks_when_spring_contains_sidecar_secret_names(self):
        spring_text = "\n".join(
            [
                "SPRING_PROFILES_ACTIVE=dev,db,key",
                "RESERVATION_PROVIDER_MODE=CLAWOPS",
                "RESERVATION_PROVIDER_RUNTIME=CLAWOPS_SIDECAR",
                "RESERVATION_PROVIDER_CALLING_ENABLED=true",
                "RESERVATION_PROVIDER_REAL_CALL_ENABLED=true",
                "RESERVATION_SCHEDULER_ENABLED=false",
                f"RESERVATION_PROVIDER_CALL_ALLOWED_NUMBERS={PLACEHOLDER_PHONE_NUMBER}",
                "RESERVATION_PROVIDER_DEV_TARGET_PHONE_OVERRIDE_ENABLED=true",
                f"RESERVATION_PROVIDER_DEV_TARGET_PHONE_OVERRIDE_NUMBER={PLACEHOLDER_PHONE_NUMBER}",
                f"CLAWOPS_SIDECAR_INTERNAL_SIGNING_KEY={SPRING_SIGNING_KEY}",
                f"OPENAI_API_KEY={SIDECAR_SECRET}",
            ]
        )
        sidecar_text = ""

        output = run_checker(spring_text, sidecar_text)

        self.assertIn("SPRING_HAS_SIDECAR_SECRET_NAMES", output)
        self.assertIn("SIDECAR_SECRET_MISSING", output)
        self.assertNotIn(SIDECAR_SECRET, output)
        self.assertNotIn(PLACEHOLDER_PHONE_NUMBER, output)


def run_checker(spring_text, sidecar_text):
    with tempfile.TemporaryDirectory() as tmpdir:
        spring_path = pathlib.Path(tmpdir) / ".env.spring.local"
        sidecar_path = pathlib.Path(tmpdir) / ".env.sidecar.real-agent.local"
        spring_path.write_text(spring_text, encoding="utf-8")
        sidecar_path.write_text(sidecar_text, encoding="utf-8")
        buffer = io.StringIO()
        with patch("sys.argv", [
            "check_local_real_agent_env.py",
            "--spring-env-file",
            str(spring_path),
            "--sidecar-env-file",
            str(sidecar_path),
        ]):
            with patch.dict("os.environ", {}, clear=True):
                with contextlib.redirect_stdout(buffer):
                    env_check.main()
        return buffer.getvalue()


if __name__ == "__main__":
    unittest.main()
