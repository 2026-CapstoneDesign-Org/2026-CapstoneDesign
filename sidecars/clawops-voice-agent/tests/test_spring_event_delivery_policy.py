import json
import pathlib
import sys
import unittest


sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

from reservation_result_mapper import ReservationResultMappingContext  # noqa: E402
from spring_event_delivery_policy import (  # noqa: E402
    ACKED,
    DUPLICATE_ACKED,
    NETWORK_ERROR,
    NON_RETRYABLE_FAILURE,
    RETRYABLE_FAILURE,
    TIMEOUT,
    FakeSpringEventTransportResult,
    build_fake_delivery_preview,
    classify_fake_delivery_result,
)
from spring_event_dispatch_candidate import (  # noqa: E402
    build_spring_event_dispatch_candidate,
    load_sample_result,
)


SIGNING_KEY = "fake-spring-internal-signing-key"
TIMESTAMP = "2026-06-01T10:00:00Z"


class SpringEventDeliveryPolicyTest(unittest.TestCase):
    def test_success_status_is_acked_without_retry(self):
        decision = classify_fake_delivery_result(FakeSpringEventTransportResult(http_status=202))

        self.assertTrue(decision.delivered)
        self.assertEqual(decision.ack_status, ACKED)
        self.assertFalse(decision.retryable)

    def test_duplicate_is_acked_without_retry(self):
        decision = classify_fake_delivery_result(FakeSpringEventTransportResult(http_status=200, duplicate=True))

        self.assertTrue(decision.delivered)
        self.assertEqual(decision.ack_status, DUPLICATE_ACKED)
        self.assertFalse(decision.retryable)

    def test_network_and_timeout_are_retryable(self):
        for error_type in (NETWORK_ERROR, TIMEOUT):
            with self.subTest(error_type=error_type):
                decision = classify_fake_delivery_result(FakeSpringEventTransportResult(error_type=error_type))

                self.assertFalse(decision.delivered)
                self.assertEqual(decision.ack_status, RETRYABLE_FAILURE)
                self.assertTrue(decision.retryable)
                self.assertEqual(decision.failure_reason, error_type)

    def test_4xx_is_non_retryable_and_5xx_is_retryable(self):
        client_error = classify_fake_delivery_result(FakeSpringEventTransportResult(http_status=400))
        server_error = classify_fake_delivery_result(FakeSpringEventTransportResult(http_status=503))

        self.assertEqual(client_error.ack_status, NON_RETRYABLE_FAILURE)
        self.assertFalse(client_error.retryable)
        self.assertEqual(client_error.failure_reason, "HTTP_400")
        self.assertEqual(server_error.ack_status, RETRYABLE_FAILURE)
        self.assertTrue(server_error.retryable)
        self.assertEqual(server_error.failure_reason, "HTTP_503")

    def test_fake_delivery_preview_uses_dispatch_candidate_without_raw_body_or_headers(self):
        candidate = build_spring_event_dispatch_candidate(
            load_sample_result("confirmed.json"),
            context(),
            SIGNING_KEY,
            TIMESTAMP,
        )

        preview = build_fake_delivery_preview(candidate, FakeSpringEventTransportResult(http_status=202))
        serialized = json.dumps(preview, ensure_ascii=False)

        self.assertEqual(preview["endpointPath"], "/internal/reservations/provider-events/clawops-agent")
        self.assertEqual(preview["eventType"], "RESERVATION_CONFIRMED")
        self.assertEqual(preview["ackStatus"], ACKED)
        self.assertTrue(preview["delivered"])
        self.assertFalse(preview["retryable"])
        self.assertIn("idempotencyKey", preview)
        self.assertNotIn(candidate.raw_body, serialized)
        self.assertNotIn(candidate.headers["X-Internal-Signature"], serialized)
        self.assertNotIn(SIGNING_KEY, serialized)


def context():
    return ReservationResultMappingContext(
        reservation_id=100,
        requested_date_time="2026-06-01T19:00:00",
        requested_party_size=4,
        sidecar_call_id="dry-run-sidecar-100",
        provider_call_id=None,
        occurred_at="2026-06-01T10:00:00",
    )


if __name__ == "__main__":
    unittest.main()
