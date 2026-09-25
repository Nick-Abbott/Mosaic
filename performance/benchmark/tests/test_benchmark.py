import sys
import unittest
from pathlib import Path

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

    def test_alternating_order(self):
        self.assertEqual(b.paired_order(1), ("direct", "mosaic"))
        self.assertEqual(b.paired_order(2), ("mosaic", "direct"))
        self.assertEqual(b.paired_order(3), ("direct", "mosaic"))

    def test_config_validation(self):
        config = {"cases": {"light": {"zero": [10, 20]}, "compute": {"service": [5]}},
                  "repetitions": 3, "warmup_seconds": 15, "settle_seconds": 3,
                  "measurement_seconds": 30, "preallocated_vus": 20, "max_vus": 20}
        self.assertIs(b.validate_config(config), config)
        for change in ({"cases": {"unknown": {"zero": [10]}}},
                       {"cases": {"light": {"unknown": [10]}}},
                       {"cases": {"light": {"zero": [0]}}},
                       {"cases": {"light": {"zero": [10, 10]}}},
                       {"max_vus": 30}, {"repetitions": 0}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                b.validate_config({**config, **change})

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
            for value in values:
                rows.append({"route": "light", "latency_profile": "zero", "offered_rps": 50,
                             "variant": variant, "successful_requests": value,
                             "http_failures": 0})
        result = b.aggregate(rows)
        successes = next(row for row in result if row["metric"] == "successful_requests")
        self.assertEqual((successes["direct_median"], successes["mosaic_median"],
                          successes["mosaic_vs_direct_percent"]), (20, 25, 25))
        failures = next(row for row in result if row["metric"] == "http_failures")
        self.assertIsNone(failures["mosaic_vs_direct_percent"])


if __name__ == "__main__":
    unittest.main()
