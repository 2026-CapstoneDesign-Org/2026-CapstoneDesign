import json
import pathlib
import sys
import unittest


sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

from reservation_result_mapper import (  # noqa: E402
    ReservationResultMappingContext,
    map_reservation_result_to_spring_event,
)


SAMPLES_DIR = pathlib.Path(__file__).resolve().parents[1] / "contracts" / "samples"
REQUESTED_DATE_TIME = "2026-06-01T19:00:00"
REQUESTED_PARTY_SIZE = 4
SPRING_EVENT_FIELDS = {
    "provider",
    "reservationId",
    "providerCallId",
    "sidecarCallId",
    "eventType",
    "providerStatus",
    "occurredAt",
    "retryable",
    "failureReason",
    "aiSummary",
    "resultMessage",
    "idempotencyKey",
    "rawPayloadHash",
}


class ReservationResultMapperTest(unittest.TestCase):
    def test_samples_map_to_expected_spring_event_types(self):
        expectations = {
            "confirmed.json": ("RESERVATION_CONFIRMED", "AI_CONFIRMED"),
            "name-phone-requested.json": ("RESERVATION_CONFIRMED", "AI_CONFIRMED"),
            "unavailable.json": ("RESERVATION_UNAVAILABLE", "AI_UNAVAILABLE"),
            "alternative-time.json": (
                "RESERVATION_NEEDS_CONFIRMATION",
                "AI_NEEDS_CONFIRMATION_ALTERNATIVE_TIME",
            ),
            "ambiguous.json": ("RESERVATION_NEEDS_CONFIRMATION", "AI_NEEDS_CONFIRMATION"),
            "staff-did-not-understand.json": ("RESERVATION_NEEDS_CONFIRMATION", "AI_NEEDS_CONFIRMATION"),
            "connection-failed.json": ("CALL_CONNECTION_FAILED", "AI_FAILED"),
        }
        for sample_name, (event_type, provider_status) in expectations.items():
            with self.subTest(sample=sample_name):
                event = map_reservation_result_to_spring_event(load_sample(sample_name), context())

                self.assertEqual(set(event), SPRING_EVENT_FIELDS)
                self.assertEqual(event["provider"], "CLAWOPS_SIDECAR")
                self.assertEqual(event["reservationId"], 100)
                self.assertEqual(event["sidecarCallId"], "dry-run-sidecar-100")
                self.assertEqual(event["eventType"], event_type)
                self.assertEqual(event["providerStatus"], provider_status)
                self.assertTrue(event["idempotencyKey"].startswith(f"dry-run-sidecar-100:{event_type}:"))
                self.assertEqual(event["aiSummary"], event["resultMessage"])
                assert_no_sensitive_values(self, event)

    def test_result_message_is_public_concise_summary(self):
        expectations = {
            "confirmed.json": "6월 1일 오후 7시 00분 4명 예약 성공",
            "name-phone-requested.json": "6월 1일 오후 7시 00분 4명 예약 성공",
            "unavailable.json": "6월 1일 오후 7시 00분 4명 예약 불가",
            "alternative-time.json": "6월 1일 오후 8시 00분 대체 시간 제안받음",
            "ambiguous.json": "6월 1일 오후 7시 00분 예약 확인 필요",
            "staff-did-not-understand.json": "6월 1일 오후 7시 00분 예약 확인 필요",
            "connection-failed.json": "전화 예약 실패",
        }
        forbidden_fragments = (
            "AI 예약 도우미임을 밝히고",
            "예약자명과 연락처",
            "요청 조건을 전달했다",
        )
        for sample_name, expected_message in expectations.items():
            with self.subTest(sample=sample_name):
                event = map_reservation_result_to_spring_event(load_sample(sample_name), context())

                self.assertEqual(event["resultMessage"], expected_message)
                self.assertEqual(event["aiSummary"], expected_message)
                for fragment in forbidden_fragments:
                    self.assertNotIn(fragment, event["resultMessage"])
                    self.assertNotIn(fragment, event["aiSummary"])

    def test_needs_confirmation_never_maps_to_confirmed_event(self):
        for sample_name in ("alternative-time.json", "ambiguous.json", "staff-did-not-understand.json"):
            with self.subTest(sample=sample_name):
                event = map_reservation_result_to_spring_event(load_sample(sample_name), context())

                self.assertEqual(event["eventType"], "RESERVATION_NEEDS_CONFIRMATION")
                self.assertNotEqual(event["eventType"], "RESERVATION_CONFIRMED")

    def test_confirmed_datetime_conflict_maps_to_needs_confirmation(self):
        payload = load_sample("confirmed.json")
        payload["confirmedDateTime"] = "2026-06-01T20:00:00"

        event = map_reservation_result_to_spring_event(payload, context())

        self.assertEqual(event["eventType"], "RESERVATION_NEEDS_CONFIRMATION")
        self.assertEqual(event["providerStatus"], "AI_RESULT_CONFLICT")
        self.assertEqual(event["failureReason"], "AI_RESULT_CONFIRMED_DATETIME_CONFLICT")

    def test_confirmed_party_size_conflict_maps_to_needs_confirmation(self):
        payload = load_sample("confirmed.json")
        payload["partySize"] = 5

        event = map_reservation_result_to_spring_event(payload, context())

        self.assertEqual(event["eventType"], "RESERVATION_NEEDS_CONFIRMATION")
        self.assertEqual(event["providerStatus"], "AI_RESULT_CONFLICT")
        self.assertEqual(event["failureReason"], "AI_RESULT_CONFIRMED_PARTY_SIZE_CONFLICT")

    def test_missing_required_field_maps_to_ai_parse_failed(self):
        payload = load_sample("confirmed.json")
        payload.pop("summary")

        event = map_reservation_result_to_spring_event(payload, context())

        self.assertEqual(event["eventType"], "AI_PARSE_FAILED")
        self.assertEqual(event["providerStatus"], "AI_PARSE_FAILED")
        self.assertEqual(event["failureReason"], "AI_RESULT_SCHEMA_FIELDS_INVALID")
        self.assertEqual(event["aiSummary"], "전화 예약 결과 확인 실패")
        self.assertEqual(event["resultMessage"], "전화 예약 결과 확인 실패")

    def test_invalid_confirmed_payload_maps_to_ai_parse_failed(self):
        payload = load_sample("confirmed.json")
        payload["confirmedDateTime"] = None

        event = map_reservation_result_to_spring_event(payload, context())

        self.assertEqual(event["eventType"], "AI_PARSE_FAILED")
        self.assertEqual(event["failureReason"], "AI_RESULT_CONFIRMED_DATETIME_REQUIRED")

    def test_provider_fatal_error_prefix_maps_to_terminal_provider_fatal_event(self):
        payload = load_sample("connection-failed.json")
        payload["failureReason"] = "PROVIDER_FATAL_ERROR__AGENTERROR_HTTP_500"

        event = map_reservation_result_to_spring_event(payload, context())

        self.assertEqual(event["eventType"], "PROVIDER_FATAL_ERROR")
        self.assertEqual(event["providerStatus"], "AI_FAILED")
        self.assertFalse(event["retryable"])
        self.assertEqual(event["failureReason"], "PROVIDER_FATAL_ERROR__AGENTERROR_HTTP_500")

    def test_missing_result_tool_maps_to_parse_failed_not_retry(self):
        payload = load_sample("connection-failed.json")
        payload["failureReason"] = "AI_RESULT_TOOL_MISSING"

        event = map_reservation_result_to_spring_event(payload, context())

        self.assertEqual(event["eventType"], "AI_PARSE_FAILED")
        self.assertEqual(event["providerStatus"], "AI_FAILED")
        self.assertFalse(event["retryable"])
        self.assertEqual(event["failureReason"], "AI_RESULT_TOOL_MISSING")


def load_sample(name):
    with (SAMPLES_DIR / name).open(encoding="utf-8") as file:
        return json.load(file)


def context():
    return ReservationResultMappingContext(
        reservation_id=100,
        requested_date_time=REQUESTED_DATE_TIME,
        requested_party_size=REQUESTED_PARTY_SIZE,
        sidecar_call_id="dry-run-sidecar-100",
        provider_call_id=None,
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


if __name__ == "__main__":
    unittest.main()
