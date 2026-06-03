import pathlib
import sys
import tempfile
import unittest


sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "scripts"))

import run_dev_ai_call_smoke as smoke  # noqa: E402


class RunDevAiCallSmokeTest(unittest.TestCase):
    def test_load_env_file_unquotes_values(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "sidecar.env"
            path.write_text(
                "\n".join(
                    [
                        "SIDECAR_PORT=8091",
                        "SIDECAR_BIND_HOST='127.0.0.1'",
                        'SIDECAR_INTERNAL_SIGNING_KEY="fake-signing-key"',
                    ]
                ),
                encoding="utf-8",
            )

            values = smoke.load_env_file(path)

        self.assertEqual(values["SIDECAR_PORT"], "8091")
        self.assertEqual(values["SIDECAR_BIND_HOST"], "127.0.0.1")
        self.assertEqual(values["SIDECAR_INTERNAL_SIGNING_KEY"], "fake-signing-key")

    def test_nested_yaml_value_reads_jwt_secret_without_logging(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "application-key.yml"
            path.write_text(
                """
jwt:
  secret: "fake-jwt-secret-for-test-only-1234567890"
  access-token-expiration: 3600000
""".strip(),
                encoding="utf-8",
            )

            value = smoke.get_nested_yaml_value(path, "jwt.secret")

        self.assertEqual(value, "fake-jwt-secret-for-test-only-1234567890")

    def test_create_jwt_uses_hs256_shape_without_exposing_secret(self):
        token = smoke.create_jwt("fake-jwt-secret-for-test-only-1234567890", "24", "USER")

        self.assertEqual(len(token.split(".")), 3)
        self.assertNotIn("fake-jwt-secret", token)

    def test_sidecar_url_from_env_defaults_to_local_port(self):
        self.assertEqual(
            smoke.sidecar_url_from_env({"SIDECAR_BIND_HOST": "127.0.0.1", "SIDECAR_PORT": "8091"}),
            "http://127.0.0.1:8091",
        )


if __name__ == "__main__":
    unittest.main()
