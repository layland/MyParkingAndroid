import unittest
from allocate_version import next_version


class VersionTest(unittest.TestCase):
    def test_first_version_updates_original_apk(self):
        self.assertEqual(next_version([], "1.0"), ("1.0.1", 2))

    def test_ignores_parking_tags_and_uses_numeric_order(self):
        self.assertEqual(next_version(["v1.28", "myexercise-v1.0.9", "myexercise-v1.0.10",
                                       "myexercise-v1.0.2", "myexercise-v2.0.99"], "1.0"), ("1.0.11", 12))


if __name__ == "__main__":
    unittest.main()
