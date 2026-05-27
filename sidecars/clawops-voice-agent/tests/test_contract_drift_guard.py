import json
import pathlib
import re
import unittest


CONTRACT_DIR = pathlib.Path(__file__).resolve().parents[1] / "contracts"
SPRING_EVENT_FIXTURES_DIR = CONTRACT_DIR / "spring-event-payloads"
EXPECTED_SPRING_EVENT_FIXTURES = {
    "confirmed.json": "RESERVATION_CONFIRMED",
    "unavailable.json": "RESERVATION_UNAVAILABLE",
    "needs-confirmation.json": "RESERVATION_NEEDS_CONFIRMATION",
    "failed.json": "CALL_CONNECTION_FAILED",
    "ai-parse-failed.json": "AI_PARSE_FAILED",
}
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
FORBIDDEN_PATTERNS = {
    "openai_secret_like": re.compile(r"sk-[A-Za-z0-9]"),
    "jwt_like": re.compile(r"\b[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b"),
    "bearer_token_like": re.compile(r"Bearer\s+[A-Za-z0-9._~+/=-]+"),
    "korean_phone_like": re.compile(
        r"\b(?:010|011|016|017|018|019|070|02|031|032|033|041|042|043|044|051|052|053|054|055|061|062|063|064)"
        r"[- ]?\d{3,4}[- ]?\d{4}\b"
    ),
}


class ContractDriftGuardTest(unittest.TestCase):
    def test_expected_spring_event_fixtures_exist_with_stable_shape(self):
        fixture_names = {path.name for path in SPRING_EVENT_FIXTURES_DIR.glob("*.json")}
        self.assertEqual(fixture_names, set(EXPECTED_SPRING_EVENT_FIXTURES))

        for fixture_name, expected_event_type in EXPECTED_SPRING_EVENT_FIXTURES.items():
            with self.subTest(fixture=fixture_name):
                payload = load_json(SPRING_EVENT_FIXTURES_DIR / fixture_name)

                self.assertEqual(set(payload), SPRING_EVENT_FIELDS)
                self.assertEqual(payload["provider"], "CLAWOPS_SIDECAR")
                self.assertEqual(payload["eventType"], expected_event_type)
                self.assertEqual(payload["reservationId"], 100)
                self.assertIsInstance(payload["retryable"], bool)
                self.assertIsInstance(payload["idempotencyKey"], str)
                self.assertIsInstance(payload["rawPayloadHash"], str)

    def test_contract_fixtures_do_not_contain_forbidden_values(self):
        for path in CONTRACT_DIR.rglob("*.json"):
            with self.subTest(path=path.name):
                text = path.read_text(encoding="utf-8")
                for name, pattern in FORBIDDEN_PATTERNS.items():
                    self.assertIsNone(pattern.search(text), name)


def load_json(path):
    with path.open(encoding="utf-8") as file:
        return json.load(file)


if __name__ == "__main__":
    unittest.main()
