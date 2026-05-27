import json
import pathlib
import sys
import unittest


sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

from real_agent_interface import (  # noqa: E402
    FakeRealAgentRunner,
    RealAgentCallRequest,
    RealAgentRunner,
)
from reservation_result_mapper import ReservationResultMappingContext  # noqa: E402
from spring_event_dispatch_candidate import build_spring_event_dispatch_candidate  # noqa: E402


SIGNING_KEY = "fake-spring-internal-signing-key"
TIMESTAMP = "2026-06-01T10:00:00Z"


class RealAgentInterfaceTest(unittest.TestCase):
    def test_fake_real_agent_returns_confirmed_result(self):
        result = FakeRealAgentRunner("CONFIRMED").run_reservation_call(request())

        self.assertEqual(result.ai_result["resultStatus"], "CONFIRMED")
        self.assertEqual(result.provider_call_id, "fake-provider-call-100")
        self.assertIsNone(result.failure_reason)
        self.assertFalse(result.retryable)
        assert_no_sensitive_values(self, result.ai_result)

    def test_fake_real_agent_returns_unavailable_result(self):
        result = FakeRealAgentRunner("UNAVAILABLE").run_reservation_call(request())

        self.assertEqual(result.ai_result["resultStatus"], "UNAVAILABLE")
        self.assertIsNone(result.failure_reason)
        self.assertFalse(result.retryable)

    def test_fake_real_agent_returns_needs_confirmation_result(self):
        result = FakeRealAgentRunner("NEEDS_CONFIRMATION").run_reservation_call(request())

        self.assertEqual(result.ai_result["resultStatus"], "NEEDS_CONFIRMATION")
        self.assertTrue(result.ai_result["alternativeTimeSuggested"])
        self.assertIsNone(result.failure_reason)
        self.assertFalse(result.retryable)

    def test_fake_real_agent_returns_failed_result(self):
        result = FakeRealAgentRunner("FAILED").run_reservation_call(request())

        self.assertEqual(result.ai_result["resultStatus"], "FAILED")
        self.assertEqual(result.failure_reason, "CALL_CONNECTION_FAILED")
        self.assertTrue(result.retryable)

    def test_fake_result_connects_to_mapper_and_dispatch_candidate(self):
        expected_event_types = {
            "CONFIRMED": "RESERVATION_CONFIRMED",
            "UNAVAILABLE": "RESERVATION_UNAVAILABLE",
            "NEEDS_CONFIRMATION": "RESERVATION_NEEDS_CONFIRMATION",
            "FAILED": "CALL_CONNECTION_FAILED",
        }
        for status, expected_event_type in expected_event_types.items():
            with self.subTest(status=status):
                result = FakeRealAgentRunner(status).run_reservation_call(request())
                candidate = build_spring_event_dispatch_candidate(
                    result.ai_result,
                    context(result),
                    SIGNING_KEY,
                    TIMESTAMP,
                )

                self.assertEqual(candidate.payload["eventType"], expected_event_type)
                self.assertEqual(candidate.payload["providerCallId"], result.provider_call_id)
                self.assertEqual(candidate.payload["sidecarCallId"], result.sidecar_call_id)
                self.assertEqual(json.loads(candidate.raw_body), candidate.payload)
                assert_no_sensitive_values(self, candidate.payload)
                assert_no_sensitive_values(self, candidate.safe_preview())

    def test_fake_runner_satisfies_real_agent_runner_protocol(self):
        runner: RealAgentRunner = FakeRealAgentRunner("CONFIRMED")

        result = runner.run_reservation_call(request())

        self.assertEqual(result.ai_result["resultStatus"], "CONFIRMED")


def request():
    return RealAgentCallRequest(
        reservation_id=100,
        sidecar_call_id="fake-sidecar-call-100",
        target_phone_number="placeholder-target-token",
        target_phone_mask="****0000",
        restaurant_name="예약식당",
        reservation_date_time="2026-06-01T19:00:00",
        party_size=4,
        request_note="창가 자리",
        reservation_name_available=True,
        reservation_contact_available=True,
    )


def context(result):
    return ReservationResultMappingContext(
        reservation_id=100,
        requested_date_time="2026-06-01T19:00:00",
        requested_party_size=4,
        sidecar_call_id=result.sidecar_call_id,
        provider_call_id=result.provider_call_id,
        occurred_at="2026-06-01T10:00:00",
    )


def assert_no_sensitive_values(testcase, value):
    if isinstance(value, dict):
        for child in value.values():
            assert_no_sensitive_values(testcase, child)
        return
    if isinstance(value, list):
        for child in value:
            assert_no_sensitive_values(testcase, child)
        return
    if isinstance(value, str):
        testcase.assertNotIn("+1555", value)
        testcase.assertNotIn("010", value)
        testcase.assertNotIn("070", value)
        testcase.assertNotIn("Bearer ", value)
        testcase.assertNotIn("sk-", value)
        testcase.assertNotIn(SIGNING_KEY, value)


if __name__ == "__main__":
    unittest.main()
