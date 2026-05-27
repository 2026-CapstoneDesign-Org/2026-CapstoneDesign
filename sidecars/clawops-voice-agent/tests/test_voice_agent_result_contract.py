import json
import pathlib
import unittest


CONTRACT_DIR = pathlib.Path(__file__).resolve().parents[1] / "contracts"
SCHEMA_PATH = CONTRACT_DIR / "reservation_result_schema.json"
SAMPLES_DIR = CONTRACT_DIR / "samples"
RESULT_STATUSES = {"CONFIRMED", "UNAVAILABLE", "NEEDS_CONFIRMATION", "FAILED"}


class VoiceAgentResultContractTest(unittest.TestCase):
    def setUp(self):
        self.schema = load_json(SCHEMA_PATH)
        self.required = set(self.schema["required"])
        self.properties = set(self.schema["properties"])

    def test_schema_defines_expected_fields(self):
        self.assertEqual(
            self.required,
            {
                "resultStatus",
                "summary",
                "confirmedDateTime",
                "partySize",
                "reservationNameProvided",
                "phoneNumberProvided",
                "restaurantRequestedNameOrPhone",
                "alternativeTimeSuggested",
                "alternativeDateTime",
                "failureReason",
                "transcriptSummary",
            },
        )
        self.assertEqual(self.required, self.properties)
        self.assertFalse(self.schema["additionalProperties"])

    def test_samples_match_contract_and_decision_rules(self):
        samples = sorted(SAMPLES_DIR.glob("*.json"))
        self.assertGreaterEqual(len(samples), 7)
        for sample_path in samples:
            with self.subTest(sample=sample_path.name):
                payload = load_json(sample_path)
                validate_contract(payload, self.required)
                validate_decision_rules(payload)
                assert_no_raw_phone_value(self, payload)


def load_json(path):
    with path.open(encoding="utf-8") as file:
        return json.load(file)


def validate_contract(payload, required):
    keys = set(payload)
    if keys != required:
        missing = required - keys
        extra = keys - required
        raise AssertionError(f"schema keys mismatch missing={missing} extra={extra}")
    if payload["resultStatus"] not in RESULT_STATUSES:
        raise AssertionError("invalid resultStatus")
    if not isinstance(payload["summary"], str) or not payload["summary"].strip():
        raise AssertionError("summary must be a non-empty string")
    for key in ("reservationNameProvided", "phoneNumberProvided",
                "restaurantRequestedNameOrPhone", "alternativeTimeSuggested"):
        if not isinstance(payload[key], bool):
            raise AssertionError(f"{key} must be boolean")
    if payload["partySize"] is not None:
        if not isinstance(payload["partySize"], int) or not 1 <= payload["partySize"] <= 20:
            raise AssertionError("partySize must be null or 1..20")


def validate_decision_rules(payload):
    status = payload["resultStatus"]
    if status == "CONFIRMED":
        if payload["confirmedDateTime"] is None:
            raise AssertionError("CONFIRMED requires confirmedDateTime")
        if payload["alternativeTimeSuggested"]:
            raise AssertionError("alternative suggestion requires NEEDS_CONFIRMATION")
        if payload["failureReason"] is not None:
            raise AssertionError("CONFIRMED must not include failureReason")
    if status == "UNAVAILABLE":
        if payload["confirmedDateTime"] is not None:
            raise AssertionError("UNAVAILABLE must not include confirmedDateTime")
        if payload["alternativeTimeSuggested"]:
            raise AssertionError("alternative suggestion requires NEEDS_CONFIRMATION")
    if status == "NEEDS_CONFIRMATION":
        if payload["confirmedDateTime"] is not None:
            raise AssertionError("NEEDS_CONFIRMATION must not include confirmedDateTime")
        if payload["alternativeTimeSuggested"] and payload["alternativeDateTime"] is None:
            raise AssertionError("alternativeTimeSuggested requires alternativeDateTime")
    if status == "FAILED":
        if not payload["failureReason"]:
            raise AssertionError("FAILED requires failureReason")
        if payload["confirmedDateTime"] is not None:
            raise AssertionError("FAILED must not include confirmedDateTime")


def assert_no_raw_phone_value(testcase, value):
    if isinstance(value, dict):
        for child in value.values():
            assert_no_raw_phone_value(testcase, child)
        return
    if isinstance(value, list):
        for child in value:
            assert_no_raw_phone_value(testcase, child)
        return
    if isinstance(value, str):
        testcase.assertNotIn("+1555", value)
        testcase.assertNotIn("010", value)
        testcase.assertNotIn("070", value)


if __name__ == "__main__":
    unittest.main()
