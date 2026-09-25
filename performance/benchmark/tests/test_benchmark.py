import json
import sys
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import benchmark as b


class BenchmarkLogicTest(unittest.TestCase):
    def test_proc_stat_handles_spaces_and_parentheses_in_comm(self):
        fields = ["S"] + ["0"] * 49
        fields[11], fields[12], fields[19] = "120", "30", "777"
        self.assertEqual(b.parse_proc_stat("123 (java worker) x) " + " ".join(fields)), (120, 30, 777))
        with self.assertRaises(ValueError):
            b.parse_proc_stat("bad")

    def test_proc_status_rss_and_high_water_mark(self):
        self.assertEqual(b.parse_proc_status("Name:\tjava\nVmRSS:\t2048 kB\nVmHWM:\t4096 kB\n"),
                         {"VmRSS": 2.0, "VmHWM": 4.0})
        self.assertIsNone(b.parse_proc_status("Name:\tjava\n")["VmHWM"])

    def test_cpu_conversion_and_per_request(self):
        self.assertEqual(b.cpu_seconds((100, 50, 9), (350, 200, 9), 100), 4.0)
        self.assertEqual(b.cpu_ms_per_request(4.0, 200), 20.0)
        self.assertIsNone(b.cpu_ms_per_request(4.0, 0))
        with self.assertRaisesRegex(ValueError, "reused"):
            b.cpu_seconds((1, 1, 9), (2, 2, 10), 100)

    def test_percentile_and_k6_summary_extraction(self):
        self.assertEqual(b.percentile([1, 2, 3, 4, 5], 95), 4.8)
        summary = {"metrics": {
            "successful_requests": {"values": {"count": 28}},
            "http_failures": {"values": {"count": 2}},
            "http_reqs": {"values": {"count": 30}},
            "dropped_iterations": {"values": {"count": 1}},
            "successful_latency": {"values": {"avg": 4, "med": 3, "p(95)": 8, "p(99)": 10}},
        }}
        self.assertEqual(b.trend(summary, "successful_latency"),
                         {"avg": 4, "med": 3, "p(95)": 8, "p(99)": 10})
        result = b.normalized_k6(summary, 3)
        self.assertEqual((result["successful_requests"], result["http_failures"],
                          result["dropped_iterations"], result["completed_rps"],
                          result["latency_p99_ms"]), (28, 2, 1, 10, 10))
        summary["metrics"]["http_reqs"]["values"]["count"] = 29
        with self.assertRaisesRegex(ValueError, "accounting mismatch"):
            b.normalized_k6(summary, 3)

    def test_k6_v2_3_real_format_fixture_and_missing_metric_errors(self):
        # Shape from the v2.3.0 smoke export; counts and latencies are sanitized.
        fixture = Path(__file__).parent / "fixtures" / "k6-v2.3-summary.json"
        summary = json.loads(fixture.read_text())
        result = b.normalized_k6(summary, 2)
        self.assertEqual((result["successful_requests"], result["http_failures"],
                          result["dropped_iterations"], result["completed_rps"],
                          result["latency_mean_ms"], result["latency_p99_ms"]),
                         (4, 0, 0, 2, 2, 5))
        del summary["metrics"]["http_reqs"]
        with self.assertRaisesRegex(ValueError, "missing http_reqs.count"):
            b.normalized_k6(summary, 2)

    def test_alternating_order(self):
        self.assertEqual(b.paired_order(1), ("direct", "mosaic"))
        self.assertEqual(b.paired_order(2), ("mosaic", "direct"))
        self.assertEqual(b.paired_order(3), ("direct", "mosaic"))

    def test_config_validation(self):
        config = {"cases": {"light": {"zero": [10, 20]}, "compute": {"service": [5]}},
                  "repetitions": 3, "warmup_seconds": 15, "settle_seconds": 3,
                  "measurement_seconds": 30, "preallocated_vus": 20, "max_vus": 20}
        self.assertIs(b.validate_config(config), config)
        self.assertEqual(config["graceful_stop_seconds"], 30)
        for change in ({"cases": {"unknown": {"zero": [10]}}},
                       {"cases": {"light": {"unknown": [10]}}},
                       {"cases": {"light": {"zero": [0]}}},
                       {"cases": {"light": {"zero": [10, 10]}}},
                       {"max_vus": 30}, {"repetitions": 0},
                       {"graceful_stop_seconds": 0}, {"graceful_stop_seconds": -1}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                b.validate_config({**config, **change})

    def test_cpu_work_validation_and_precedence(self):
        self.assertEqual(b.effective_cpu_work(None, {}), 20_000)
        self.assertEqual(b.effective_cpu_work(None, {"cpu_work": 500}), 500)
        self.assertEqual(b.effective_cpu_work(600, {"cpu_work": 500}), 600)
        self.assertEqual(b.app_env(8080, "zero", list(b.DEFAULT_JVM), 600)["MOSAIC_PERFORMANCE_CPU_WORK"], "600")
        for value in (99, 2_000_001, 1.5, True):
            with self.subTest(value=value), self.assertRaises(ValueError):
                b.validate_cpu_work(value)

    def test_readiness_poll_interval_is_recorded(self):
        args = SimpleNamespace(cpu_work=1234, raw_k6=False, skip_build=True)
        with patch.object(b, "command_output", return_value="test"):
            result = b.metadata({}, args, None, None, list(b.DEFAULT_JVM), {"commit": "abc", "status": ""})
        self.assertEqual(result["readiness_poll_interval_seconds"], b.READINESS_POLL_INTERVAL_SECONDS)
        self.assertTrue(0.005 <= result["readiness_poll_interval_seconds"] <= 0.01)
        self.assertEqual(result["application_environment"]["MOSAIC_PERFORMANCE_CPU_WORK"], "1234")

    def test_disjoint_cpu_sets(self):
        self.assertEqual(b.parse_cpu_set("0-2,4"), [0, 1, 2, 4])
        with self.assertRaisesRegex(ValueError, "disjoint"):
            b.validate_affinity("0-2", "2-3")
        with self.assertRaises(ValueError):
            b.parse_cpu_set("4-2")

    def test_jvm_config_disallows_app_overrides_and_pretouch(self):
        b.verify_jvm_options(list(b.DEFAULT_JVM))
        for option in ("-Dmosaic.performance.tracing=true", "-XX:+AlwaysPreTouch"):
            with self.subTest(option=option), self.assertRaises(ValueError):
                b.verify_jvm_options([option])

    def test_aggregation_preserves_medians_and_zero_baseline(self):
        rows = []
        for variant, values in (("direct", [10, 20, 100]), ("mosaic", [15, 25, 30])):
            for repetition, value in enumerate(values, 1):
                rows.append({"route": "light", "latency_profile": "zero", "offered_rps": 50,
                             "variant": variant, "repetition": repetition, "successful_requests": value,
                             "http_failures": 0})
        result = b.aggregate(rows)
        successes = next(row for row in result if row["metric"] == "successful_requests")
        self.assertEqual((successes["direct_median"], successes["mosaic_median"],
                          successes["median_paired_absolute_difference"],
                          successes["median_paired_relative_percent"]), (20, 25, 5, 25))
        failures = next(row for row in result if row["metric"] == "http_failures")
        self.assertIsNone(failures["median_paired_relative_percent"])
        self.assertEqual(failures["median_paired_absolute_difference"], 0)

    def test_paired_aggregation_survives_temporal_drift(self):
        rows = []
        for variant, values in (("direct", [10, 100, 1000, 10000]),
                                ("mosaic", [11, 200, 1100, 20000])):
            for repetition, value in enumerate(values, 1):
                rows.append({"route": "compute", "latency_profile": "zero", "offered_rps": 50,
                             "variant": variant, "repetition": repetition, "latency_p95_ms": value})
        result = next(row for row in b.aggregate(rows) if row["metric"] == "latency_p95_ms")
        self.assertEqual((result["direct_median"], result["mosaic_median"]), (550, 650))
        self.assertEqual(result["median_paired_absolute_difference"], 100)
        self.assertAlmostEqual(result["median_paired_relative_percent"], 55)
        self.assertNotAlmostEqual(result["median_paired_relative_percent"], (650 - 550) / 550 * 100)

    def test_paired_startup_aggregation(self):
        rows = [
            {"variant": "mosaic", "repetition": 2, "readiness_seconds": 0.24},
            {"variant": "direct", "repetition": 1, "readiness_seconds": 0.1},
            {"variant": "mosaic", "repetition": 1, "readiness_seconds": 0.12},
            {"variant": "direct", "repetition": 2, "readiness_seconds": 0.2},
        ]
        summary, pairs = b.aggregate_startup(rows)
        self.assertEqual([pair["repetition"] for pair in pairs], [1, 2])
        self.assertAlmostEqual(summary["paired"]["median_mosaic_minus_direct_ms"], 30)
        self.assertAlmostEqual(summary["paired"]["median_mosaic_vs_direct_percent"], 20)
        self.assertEqual(summary["direct"]["samples"], 2)


if __name__ == "__main__":
    unittest.main()
