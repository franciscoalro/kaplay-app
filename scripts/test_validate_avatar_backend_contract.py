import io
import json
import os
import unittest
from unittest.mock import patch

import validate_avatar_backend_contract as validator


class AvatarBackendContractTest(unittest.TestCase):
    def response(self, status, payload):
        body = json.dumps(payload).encode()
        response = io.BytesIO(body)
        response.status = status
        response.__enter__ = lambda: response
        response.__exit__ = lambda *_: False
        return response

    def test_accepts_successful_rpc(self):
        with patch.object(validator.urllib.request, "urlopen", return_value=self.response(200, [])):
            self.assertEqual(validator.check_contract("https://isolated.example", "publishable"), None)

    def test_rejects_missing_rpc(self):
        with patch.object(
            validator.urllib.request,
            "urlopen",
            return_value=self.response(404, {"code": "PGRST202"}),
        ):
            with self.assertRaisesRegex(validator.ContractError, "PGRST202"):
                validator.check_contract("https://isolated.example", "publishable")

    def test_rejects_production_host(self):
        with self.assertRaisesRegex(validator.ContractError, "production"):
            validator.check_contract("https://iibureqvpurmbgqupnvl.supabase.co", "publishable")

    def test_rejects_non_https(self):
        with self.assertRaisesRegex(validator.ContractError, "HTTPS"):
            validator.check_contract("http://isolated.example", "publishable")


if __name__ == "__main__":
    unittest.main()
