#!/usr/bin/env python3
"""Read-only preflight for the isolated avatar catalog RPC contract."""

import json
import os
import sys
import urllib.error
import urllib.request
from urllib.parse import urlparse


class ContractError(RuntimeError):
    pass


def check_contract(base_url: str, anon_key: str, opener=None):
    parsed = urlparse(base_url)
    opener = opener or urllib.request.urlopen
    host = (parsed.hostname or "").lower()
    if parsed.scheme != "https":
        raise ContractError("isolated avatar backend must use HTTPS")
    if host == "iibureqvpurmbgqupnvl.supabase.co":
        raise ContractError("production backend is forbidden for isolated contract checks")
    if not host:
        raise ContractError("isolated avatar backend must include a host")
    if not anon_key:
        raise ContractError("publishable key is required")

    request = urllib.request.Request(
        f"{base_url.rstrip('/')}/rest/v1/rpc/get_avatar_catalog",
        data=b"{}",
        method="POST",
        headers={
            "apikey": anon_key,
            "Authorization": f"Bearer {anon_key}",
            "Content-Type": "application/json",
        },
    )
    try:
        with opener(request, timeout=15) as response:
            status = getattr(response, "status", None)
            if status is None:
                status = response.getcode()
            if status != 200:
                try:
                    payload = json.loads(response.read().decode("utf-8"))
                except (ValueError, UnicodeDecodeError):
                    payload = {}
                if payload.get("code") == "PGRST202":
                    raise ContractError("avatar RPC contract missing: PGRST202")
                raise ContractError(f"avatar RPC returned HTTP {status}")
            return None
    except urllib.error.HTTPError as error:
        try:
            payload = json.loads(error.read().decode("utf-8"))
        except (ValueError, UnicodeDecodeError):
            payload = {}
        code = payload.get("code")
        if code == "PGRST202":
            raise ContractError("avatar RPC contract missing: PGRST202") from error
        raise ContractError(f"avatar RPC returned HTTP {error.code}") from error
    except (urllib.error.URLError, TimeoutError) as error:
        raise ContractError("isolated avatar backend is unreachable") from error


def main() -> int:
    try:
        check_contract(
            os.environ.get("KAPLAY_ISOLATED_BASE_URL", ""),
            os.environ.get("KAPLAY_ISOLATED_ANON_KEY", ""),
        )
    except ContractError as error:
        print(f"avatar backend contract failed: {error}", file=sys.stderr)
        return 1
    print("avatar backend contract: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
