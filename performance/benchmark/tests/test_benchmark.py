import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import benchmark as b

FIXTURE = Path(__file__).parent / 'fixtures' / 'wrk2-output.txt'


class BenchmarkLogicTest(unittest.TestCase):
    def test_proc_stat_and_status(self):
        fields = ['S'] + ['0'] * 49
        fields[11], fields[12], fields[19] = '120', '30', '777'
        self.assertEqual(b.parse_proc_stat('123 (java worker) x) ' + ' '.join(fields)), (120, 30, 777))
        self.assertEqual(b.parse_proc_status('VmRSS:\t2048 kB\nVmHWM:\t4096 kB\n'),
                         {'VmRSS': 2.0, 'VmHWM': 4.0})
        with self.assertRaises(ValueError):
            b.parse_proc_stat('bad')

    def test_cpu_and_same_window_denominator(self):
        self.assertEqual(b.cpu_seconds((100, 50, 9), (350, 200, 9), 100), 4.0)
        self.assertEqual(b.cpu_ms_per_request(4.0, 200), 20.0)
        self.assertIsNone(b.cpu_ms_per_request(4.0, 0))
        good = {'valid_comparison_point': True, 'validated_completed_requests': 200}
        self.assertEqual(b.validated_cpu_ms(4.0, good), 20.0)
        good['valid_comparison_point'] = False
        self.assertIsNone(b.validated_cpu_ms(4.0, good))
        with self.assertRaisesRegex(ValueError, 'reused'):
            b.cpu_seconds((1, 1, 9), (2, 2, 10), 100)

    def test_wrk2_command(self):
        env = {'wrk2_binary': '/nix/store/qualified/bin/wrk2'}
        command = b.wrk2_command(env, 'light', 800, 52, 128, 4, 8080, [6, 18])
        self.assertEqual(command[:6], ['taskset', '-c', '6,18', 'stdbuf', '-oL', '-eL'])
        self.assertEqual(command[6], env['wrk2_binary'])
        self.assertIn('-L', command)
        self.assertIn('-U', command)
        self.assertEqual(command[command.index('-R') + 1], '800')
        self.assertEqual(command[command.index('-d') + 1], '52s')
        self.assertEqual(b.wrk2_total_duration(30), 41)
        self.assertEqual(b.wrk2_total_duration(7), 18)

    def test_deterministic_request_body_and_input_rotation(self):
        for route, body in [('light', '{"customerId":7}'), ('aggregate', '{"customerId":7}'),
                            ('batching', '{"catalogId":7}'), ('compute', '{"seed":7}')]:
            self.assertEqual(b.request_body(route, 7), body)
        self.assertEqual(b.INPUTS, (1, 7, 42, 99))
        with self.assertRaises(ValueError):
            b.request_body('unknown', 7)

    def test_calibration_and_corrected_uncorrected_histograms(self):
        text = FIXTURE.read_text()
        result = b.parse_wrk2(text, 2)
        self.assertEqual(result['steady_completed_requests'], 800)
        self.assertEqual(result['full_run_requests'], 1824)
        self.assertEqual(result['corrected_p50_ms'], 0.95)
        self.assertEqual(result['corrected_p95_ms'], 1.703)
        self.assertEqual(result['uncorrected_p95_ms'], 0.33)
        self.assertGreater(result['corrected_p99_ms'], result['uncorrected_p99_ms'])
        self.assertEqual(result['socket_errors'], 0)
        self.assertEqual(result['non_2xx_responses'], 0)
        self.assertEqual(sum(result['lua_100ms_bins'].values()), result['lua_window_requests'])
        with self.assertRaisesRegex(ValueError, 'calibrations'):
            b.parse_wrk2(text.replace('Thread calibration:', 'Calibration:', 1), 2)

    def test_wrk2_socket_and_http_error_parsing(self):
        text = FIXTURE.read_text().replace('MOSAIC_NON2XX thread=1 count=0',
                                           'MOSAIC_NON2XX thread=1 count=3')
        text += '\nSocket errors: connect 1, read 2, write 0, timeout 4\n'
        result = b.parse_wrk2(text, 2)
        self.assertEqual((result['socket_errors'], result['non_2xx_responses']), (7, 3))
        checked = b.validate_steady_window(result, 100, 7, 8, 8, 1)
        self.assertFalse(checked['valid_comparison_point'])
        self.assertIn('socket or non-2xx errors', checked['integrity_warnings'])

    def test_steady_window_rate_and_pacing(self):
        metric = b.parse_wrk2(FIXTURE.read_text(), 2)
        good = b.validate_steady_window(metric, 100, 7, 8.01, 8, 1)
        self.assertTrue(good['valid_comparison_point'])
        self.assertAlmostEqual(good['steady_expected_requests'], 801)
        short = b.validate_steady_window(metric, 100, 7, 7, 8, 1)
        self.assertFalse(short['valid_comparison_point'])
        self.assertIn('completed count differs', short['integrity_warnings'][0])
        self.assertFalse(b.validate_steady_window(metric, 100, 7, 10, 8, 1)['valid_comparison_point'])
        self.assertFalse(b.validate_steady_window({**metric, 'lua_window_requests': 650},
                                                100, 7, 8, 8, 1)['valid_comparison_point'])
        wave = dict(metric)
        wave['lua_100ms_bins'] = {**metric['lua_100ms_bins'], **{i: 0 for i in range(10, 20)}}
        self.assertFalse(b.validate_steady_window(wave, 100, 7, 8, 8, 1)['valid_comparison_point'])
        high = {**metric, 'steady_completed_requests': 25600, 'lua_window_requests': 24000,
                'lua_100ms_bins': {i: 320 for i in range(75)}}
        self.assertTrue(b.validate_steady_window(high, 3200, 7, 8, 7.5, 1)['valid_comparison_point'])
        high['lua_100ms_bins'][20] = 0
        high['lua_100ms_bins'][21] = 640
        self.assertIn('100 ms pacing wave', ' '.join(
            b.validate_steady_window(high, 3200, 7, 8, 7.5, 1)['integrity_warnings']))

    def test_qualified_ceiling_and_exploratory_override(self):
        cfg = {'cases': {'light': {'zero': [3200]}}}
        env = {'qualified_max_rps': 3200}
        self.assertTrue(b.enforce_qualified_rates(cfg, env, False))
        cfg['cases']['light']['zero'] = [6400]
        with self.assertRaisesRegex(ValueError, 'qualified ceiling'):
            b.enforce_qualified_rates(cfg, env, False)
        self.assertFalse(b.enforce_qualified_rates(cfg, env, True))

    def test_environment_version_identity(self):
        env = b.load_environment()
        self.assertEqual(env['wrk2_package'], 'wrk2-4.0.0-e0109df')
        self.assertEqual(env['wrk2_reported_version'], 'wrk 4.0.0')
        self.assertEqual(env['qualified_max_rps'], 3200)
        args = SimpleNamespace(cpu_work=20000, skip_build=True, exploratory=True)
        with patch.object(b, 'command_output', return_value='test'):
            metadata = b.metadata({}, args, None, None, list(b.DEFAULT_JVM),
                                  {'commit': 'abc', 'status': ''}, env)
        self.assertEqual(metadata['generator']['package'], env['wrk2_package'])
        self.assertEqual(metadata['generator']['reported_version'], 'wrk 4.0.0')

    def test_invalid_pair_excluded_from_aggregation(self):
        base = {'route': 'light', 'latency_profile': 'zero', 'offered_rps': 100,
                'repetition': 1, 'steady_completed_requests': 800, 'corrected_p99_ms': 1,
                'cpu_ms_per_successful_request': 0.1, 'cpu_core_equivalents': 0.1,
                'generator_cpu_core_equivalents': 0.1, 'socket_errors': 0,
                'non_2xx_responses': 0, 'integrity_warnings': []}
        with tempfile.TemporaryDirectory() as temporary:
            rows = [{**base, 'variant': 'direct', 'valid_comparison_point': True},
                    {**base, 'variant': 'mosaic', 'valid_comparison_point': False}]
            b.write_load_summary(Path(temporary), rows)
            self.assertEqual(len((Path(temporary) / 'summary.csv').read_text().splitlines()), 1)
            self.assertIn('**INVALID**', (Path(temporary) / 'summary.md').read_text())

    def test_paired_aggregation_median_and_range(self):
        rows = []
        for variant, values in [('direct', [10, 20, 100, 200]),
                                ('mosaic', [11, 24, 90, 240])]:
            for repetition, value in enumerate(values, 1):
                rows.append({'route': 'compute', 'latency_profile': 'zero', 'offered_rps': 100,
                             'variant': variant, 'repetition': repetition,
                             'cpu_ms_per_successful_request': value})
        result = next(x for x in b.aggregate(rows) if x['metric'] == 'cpu_ms_per_successful_request')
        self.assertEqual(result['paired_repetitions'], 4)
        self.assertEqual(result['min_paired_relative_percent'], -10)
        self.assertEqual(result['max_paired_relative_percent'], 20)
        self.assertAlmostEqual(result['median_paired_relative_percent'], 15)

    def test_config_and_common_controls(self):
        cfg = {'cases': {'light': {'zero': [100, 400]}}, 'repetitions': 4,
               'measurement_seconds': 30, 'calibration_timeout_seconds': 15,
               'tail_seconds': 10, 'wrk2_threads': 4, 'wrk2_connections': 128}
        self.assertIs(b.validate_config(cfg), cfg)
        for change in ({'wrk2_connections': 130}, {'measurement_seconds': 1.5},
                       {'calibration_timeout_seconds': 10}, {'cases': {'compute': {'service': [100]}}},
                       {'arrival_fidelity_tolerance_percent': -1}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                b.validate_config({**cfg, **change})
        self.assertEqual(b.paired_order(1), ('direct', 'mosaic'))
        self.assertEqual(b.paired_order(2), ('mosaic', 'direct'))
        self.assertEqual(b.parse_cpu_set('0-2,4'), [0, 1, 2, 4])
        with self.assertRaisesRegex(ValueError, 'disjoint'):
            b.validate_affinity('0-2', '2-3')
        b.verify_jvm_options(list(b.DEFAULT_JVM))
        with self.assertRaises(ValueError):
            b.verify_jvm_options(['-XX:+AlwaysPreTouch'])

    def test_process_sampler(self):
        with tempfile.TemporaryDirectory() as temporary:
            sampler = b.ProcessSampler(os.getpid(), Path(temporary) / 'samples.csv')
            sampler.start()
            sampler.stop()
            self.assertTrue(sampler.rows)
            self.assertGreater(sampler.rows[0]['rss_mib'], 0)

    def test_startup_aggregation(self):
        rows = [{'variant': 'direct', 'repetition': 1, 'readiness_seconds': 0.1},
                {'variant': 'mosaic', 'repetition': 1, 'readiness_seconds': 0.12},
                {'variant': 'mosaic', 'repetition': 2, 'readiness_seconds': 0.24},
                {'variant': 'direct', 'repetition': 2, 'readiness_seconds': 0.2}]
        summary, pairs = b.aggregate_startup(rows)
        self.assertEqual(len(pairs), 2)
        self.assertAlmostEqual(summary['paired']['median_mosaic_minus_direct_ms'], 30)


if __name__ == '__main__':
    unittest.main()
