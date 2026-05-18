import tempfile
import unittest
from pathlib import Path

import duckdb

from analytics.load_backup import load_backup


class LoadBackupTest(unittest.TestCase):
    def test_loads_backup_into_duckdb_mart_and_views(self):
        fixture = Path(__file__).parent / "fixtures" / "sample_backup.json"
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "ecolift.duckdb"

            load_backup(fixture, output)

            con = duckdb.connect(str(output), read_only=True)
            try:
                self.assertEqual(con.execute("select count(*) from dim_exercise").fetchone()[0], 2)
                self.assertEqual(con.execute("select count(*) from fact_workout_set").fetchone()[0], 3)
                self.assertEqual(con.execute("select count(*) from fact_patch_audit").fetchone()[0], 2)
                self.assertEqual(con.execute("select count(*) from fact_agent_turn").fetchone()[0], 2)
                self.assertEqual(
                    con.execute("select round(sum(total_volume_lbs), 1) from weekly_volume").fetchone()[0],
                    3130.0,
                )
                self.assertEqual(
                    con.execute("select max_weight_lbs from exercise_prs where exercise_name = 'Bench Press'").fetchone()[0],
                    185.0,
                )
                self.assertEqual(
                    con.execute("select error_count from agent_reliability").fetchone()[0],
                    1,
                )
                self.assertEqual(
                    con.execute("select issue_count from data_quality_issues where issue_type = 'orphan_workout_sets'").fetchone()[0],
                    0,
                )
            finally:
                con.close()


if __name__ == "__main__":
    unittest.main()
