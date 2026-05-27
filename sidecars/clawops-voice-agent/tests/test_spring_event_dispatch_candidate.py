import json
import pathlib
import sys
import unittest


sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

from reservation_result_mapper import ReservationResultMappingContext  # noqa: E402
from spring_event_dispatch_candidate import (  # noqa: E402
    CONTENT_TYPE_HEADER,
    CONTENT_TYPE_JSON,
    SIGNATURE_HEADER,
    SPRING_EVENT_PATH,
    TIMESTAMP_HEADER,
    build_spring_event_dispatch_candidate,
    load_sample_result,
    sign_internal_event,
)


SIGNING_KEY = "fake-spring-internal-signing-key"
TIMESTAMP = "2026-06-01T10:00:00Z"
REQUESTED_DATE_TIME = "2026-06-01T19:00:00"
REQUESTED_PARTY_SIZE = 4
SPRING_EVENT_FIXTURES_DIR = pathlib.Path(__file__).resolve().parents[1] / "contracts" / "spring-event-payloads"
EXPECTED_EVENT_TYPES = {
    "confirmed.json": "RESERVATION_CONFIRMED",
    "unavailable.json": "RESERVATION_UNAVAILABLE",
    "alternative-time.json": "RESERVATION_NEEDS_CONFIRMATION",
    "ambiguous.json": "RESERVATION_NEEDS_CONFIRMATION",
    "staff-did-not-understand.json": "RESERVATION_NEEDS_CONFIRMATION",
    "connection-failed.json": "CALL_CONNECTION_FAILED",
}


class SpringEventDispatchCandidateTest(unittest.TestCase):
    def test_sample_results_build_signed_dispatch_candidates(self):
        for sample_name, expected_event_type in EXPECTED_EVENT_TYPES.items():
            with self.subTest(sample=sample_name):
                candidate = build_spring_event_dispatch_candidate(
                    load_sample_result(sample_name),
                    context(),
                    SIGNING_KEY,
                    TIMESTAMP,
                )

                self.assertEqual(candidate.endpoint_path, SPRING_EVENT_PATH)
                self.assertEqual(candidate.payload["eventType"], expected_event_type)
                self.assertEqual(candidate.headers[CONTENT_TYPE_HEADER], CONTENT_TYPE_JSON)
                self.assertEqual(candidate.headers[TIMESTAMP_HEADER], TIMESTAMP)
                self.assertEqual(
                    candidate.headers[SIGNATURE_HEADER],
                    sign_internal_event(SIGNING_KEY, TIMESTAMP, candidate.raw_body),
                )
                self.assertEqual(json.loads(candidate.raw_body), candidate.payload)
                assert_no_sensitive_values(self, candidate.payload)
                assert_no_sensitive_values(self, candidate.headers)

    def test_invalid_schema_builds_safe_parse_failure_candidate(self):
        payload = load_sample_result("confirmed.json")
        payload.pop("summary")

        candidate = build_spring_event_dispatch_candidate(payload, context(), SIGNING_KEY, TIMESTAMP)

        self.assertEqual(candidate.payload["eventType"], "AI_PARSE_FAILED")
        self.assertEqual(candidate.payload["providerStatus"], "AI_PARSE_FAILED")
        self.assertEqual(candidate.payload["failureReason"], "AI_RESULT_SCHEMA_FIELDS_INVALID")
        self.assertIsNone(candidate.payload["aiSummary"])
        self.assertEqual(candidate.payload["resultMessage"], "AI result schema validation failed.")
        self.assertEqual(candidate.payload, load_spring_event_fixture("ai-parse-failed.json"))

    def test_confirmed_conflict_builds_needs_confirmation_candidate(self):
        payload = load_sample_result("confirmed.json")
        payload["partySize"] = 5

        candidate = build_spring_event_dispatch_candidate(payload, context(), SIGNING_KEY, TIMESTAMP)

        self.assertEqual(candidate.payload["eventType"], "RESERVATION_NEEDS_CONFIRMATION")
        self.assertEqual(candidate.payload["providerStatus"], "AI_RESULT_CONFLICT")
        self.assertEqual(candidate.payload["failureReason"], "AI_RESULT_CONFIRMED_PARTY_SIZE_CONFLICT")

    def test_safe_preview_omits_signature_value(self):
        candidate = build_spring_event_dispatch_candidate(
            load_sample_result("confirmed.json"),
            context(),
            SIGNING_KEY,
            TIMESTAMP,
        )

        preview = candidate.safe_preview()
        serialized = json.dumps(preview, ensure_ascii=False)

        self.assertTrue(preview["ready"])
        self.assertIn(SIGNATURE_HEADER, preview["headerNames"])
        self.assertNotIn(candidate.headers[SIGNATURE_HEADER], serialized)
        self.assertNotIn(SIGNING_KEY, serialized)

    def test_builder_payloads_match_spring_contract_fixtures(self):
        sample_to_fixture = {
            "confirmed.json": "confirmed.json",
            "unavailable.json": "unavailable.json",
            "alternative-time.json": "needs-confirmation.json",
            "connection-failed.json": "failed.json",
        }
        for sample_name, fixture_name in sample_to_fixture.items():
            with self.subTest(sample=sample_name, fixture=fixture_name):
                candidate = build_spring_event_dispatch_candidate(
                    load_sample_result(sample_name),
                    context(),
                    SIGNING_KEY,
                    TIMESTAMP,
                )

                self.assertEqual(candidate.payload, load_spring_event_fixture(fixture_name))


def context():
    return ReservationResultMappingContext(
        reservation_id=100,
        requested_date_time=REQUESTED_DATE_TIME,
        requested_party_size=REQUESTED_PARTY_SIZE,
        sidecar_call_id="dry-run-sidecar-100",
        provider_call_id=None,
        occurred_at="2026-06-01T10:00:00",
    )


def load_spring_event_fixture(name):
    with (SPRING_EVENT_FIXTURES_DIR / name).open(encoding="utf-8") as file:
        return json.load(file)


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
