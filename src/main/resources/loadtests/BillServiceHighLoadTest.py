#!/usr/bin/env python3
"""
High-load test for the Bill service dashboard endpoints.

This script simulates N concurrent users each performing the same 4 dashboard requests
(simulating the frontend Promise.all behavior). It is configurable and intended to be
used against a staging or production environment with caution.

Defaults:
 - users: 1000
 - batches: 10 (so users are split in batches to ramp up)
 - batch-delay: 1s between batches

Notes:
 - You can provide a single TOKEN (Authorization header) or a --token-file with one token per line
   to simulate different authenticated users. If neither is provided, the requests will be
   executed without Authorization header.
 - Do NOT run against a production system you don't control. Be mindful of rate limits and
   potential side effects. This script only performs GET requests to read endpoints.

Outputs a summary with latency percentiles and status code distribution.
"""

import argparse
import concurrent.futures
import requests
import time
import statistics
import sys
from typing import List, Tuple, Dict

DEFAULT_USERS = 1000
DEFAULT_BATCHES = 10
DEFAULT_BATCH_DELAY = 1.0
DEFAULT_BILL_DATE = "Mar 2026"

ENDPOINT_PATHS = [
    "/load-main-table-data?billDate=",
    "/load-card-table-data?billDate=",
    "/load-assets-table-data?billDate=",
    "/load-payment-card-table-data?billDate="
]


def percentile(data: List[float], perc: float) -> float:
    if not data:
        return 0.0
    k = (len(data)-1) * (perc/100.0)
    f = int(k)
    c = min(f+1, len(data)-1)
    if f == c:
        return data[int(k)]
    d0 = data[f] * (c - k)
    d1 = data[c] * (k - f)
    return d0 + d1


def fetch_url(url: str, headers: Dict[str, str]) -> Tuple[int, float, str]:
    start = time.time()
    try:
        r = requests.get(url, headers=headers, timeout=30)
        elapsed = (time.time() - start) * 1000.0
        return r.status_code, elapsed, url
    except Exception as e:
        elapsed = (time.time() - start) * 1000.0
        return 0, elapsed, f"{url} | ERROR: {e}"


def run_batch(base_url: str, bill_date: str, user_tokens: List[str], users_in_batch: int, start_user_index: int) -> List[Tuple[int, float, str]]:
    results = []
    futures = []
    headers_template = {"Content-Type": "application/json"}

    # Use a global thread pool for endpoints in this batch (4 requests per user)
    max_workers = min(8000, max(32, users_in_batch * len(ENDPOINT_PATHS)))
    with concurrent.futures.ThreadPoolExecutor(max_workers=max_workers) as executor:
        for i in range(users_in_batch):
            user_idx = start_user_index + i
            # choose token if provided
            headers = dict(headers_template)
            if user_tokens:
                token = user_tokens[user_idx % len(user_tokens)]
                if token:
                    headers["Authorization"] = f"Bearer {token}"

            # Submit the 4 endpoint calls for this "user"
            for path in ENDPOINT_PATHS:
                url = f"{base_url}{path}{bill_date}"
                futures.append(executor.submit(fetch_url, url, headers))

        for fut in concurrent.futures.as_completed(futures):
            try:
                results.append(fut.result())
            except Exception as e:
                results.append((0, 0.0, f"FUTURE_ERROR: {e}"))

    return results


def summarize_results(all_results: List[Tuple[int, float, str]], total_requests_expected: int):
    latencies = [r[1] for r in all_results if r[0] != 0]
    errors = [r for r in all_results if r[0] == 0]
    status_counts = {}
    for s, _, _ in all_results:
        status_counts[s] = status_counts.get(s, 0) + 1

    lat_sorted = sorted(latencies)
    total = len(all_results)
    success = total - len(errors)

    print("\n=== SUMMARY ===")
    print(f"Total requests (attempted): {total}")
    print(f"Total requests expected      : {total_requests_expected}")
    print(f"Successful (non-zero status) : {success}")
    print(f"Failures (exceptions / timeout) : {len(errors)}")
    print("Status code distribution:")
    for code in sorted(status_counts.keys()):
        print(f"  {str(code).rjust(3)} : {status_counts[code]}")

    if lat_sorted:
        print("\nLatency (ms) statistics for successful requests:")
        print(f"  count: {len(lat_sorted)}")
        print(f"  min  : {min(lat_sorted):.2f}")
        print(f"  p50  : {percentile(lat_sorted, 50):.2f}")
        print(f"  p90  : {percentile(lat_sorted, 90):.2f}")
        print(f"  p95  : {percentile(lat_sorted, 95):.2f}")
        print(f"  p99  : {percentile(lat_sorted, 99):.2f}")
        print(f"  max  : {max(lat_sorted):.2f}")
        print(f"  avg  : {statistics.mean(lat_sorted):.2f}")
    else:
        print("No successful latencies to report.")


def load_tokens_from_file(path: str) -> List[str]:
    try:
        with open(path, "r", encoding="utf-8") as f:
            tokens = [line.strip() for line in f if line.strip()]
        return tokens
    except Exception as e:
        print(f"Failed to load token file: {e}")
        return []


def main(argv):
    parser = argparse.ArgumentParser(description="High-load test for bill dashboard endpoints")
    parser.add_argument("--users", type=int, default=DEFAULT_USERS, help="Total concurrent users to simulate (default: 1000)")
    parser.add_argument("--batches", type=int, default=DEFAULT_BATCHES, help="Split users into batches for ramp-up (default: 10)")
    parser.add_argument("--batch-delay", type=float, default=DEFAULT_BATCH_DELAY, help="Seconds to wait between batches (default: 1.0)")
    parser.add_argument("--token", type=str, default=None, help="Single Bearer token to use for all requests")
    parser.add_argument("--token-file", type=str, default=None, help="Path to file with one token per line to simulate multiple users")
    parser.add_argument("--base-url", type=str, default="http://localhost:8085/api/bill", help="Base URL for the bill endpoints")
    parser.add_argument("--bill-date", type=str, default=DEFAULT_BILL_DATE, help="Bill date parameter (e.g. 'Mar 2026' or '2026-03')")
    parser.add_argument("--no-warn", action="store_true", help="Skip the production warning prompt")

    args = parser.parse_args(argv)

    if not args.no_warn:
        print("WARNING: This is a high-load test script. Make sure you have permission to run this against the target environment.")
        print("You can pass --no-warn to skip this message.")
        try:
            input("Press Enter to continue or Ctrl+C to abort...")
        except KeyboardInterrupt:
            print("Aborted by user")
            sys.exit(1)

    users = max(1, args.users)
    batches = max(1, args.batches)
    batch_delay = max(0.0, args.batch_delay)
    base_url = args.base_url.rstrip("/")
    bill_date = args.bill_date

    # Load tokens
    user_tokens = []
    if args.token_file:
        user_tokens = load_tokens_from_file(args.token_file)
        if not user_tokens:
            print("No tokens loaded from token file. Exiting.")
            sys.exit(1)
    elif args.token:
        user_tokens = [args.token]

    users_per_batch = users // batches
    remainder = users % batches

    print(f"Starting high-load test with {users} users in {batches} batches (users per batch ~ {users_per_batch})")
    print(f"Base URL: {base_url}")
    print(f"Bill date: {bill_date}")
    print(f"Tokens used: {'file' if args.token_file else ('single' if args.token else 'none')}\n")

    all_results = []
    total_requests_expected = users * len(ENDPOINT_PATHS)
    start_time = time.time()

    current_start_user = 0
    for b in range(batches):
        # distribute the remainder among the first batches
        size = users_per_batch + (1 if b < remainder else 0)
        if size == 0:
            continue

        print(f"\nStarting batch {b+1}/{batches} with {size} users...")
        batch_results = run_batch(base_url, bill_date, user_tokens, size, current_start_user)
        all_results.extend(batch_results)
        current_start_user += size

        if b < batches - 1:
            print(f"Batch {b+1} finished. Sleeping {batch_delay}s before next batch...")
            time.sleep(batch_delay)

    total_time = time.time() - start_time
    print(f"\nAll batches completed in {total_time:.2f}s")

    summarize_results(all_results, total_requests_expected)


if __name__ == "__main__":
    main(sys.argv[1:])

