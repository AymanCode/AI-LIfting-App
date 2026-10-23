import argparse
import hashlib
import json
import sqlite3
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('database', type=Path)
parser.add_argument('output', type=Path)
args = parser.parse_args()
with sqlite3.connect(args.database.resolve().as_uri() + '?mode=ro', uri=True) as connection:
    connection.row_factory = sqlite3.Row
    result = {table: [dict(row) for row in connection.execute('select * from ' + table)]
              for table in ['exercise', 'workout_set', 'workout_day', 'cycle', 'cycle_slot', 'split_exercise', 'user_settings']}
result['source_sha256'] = hashlib.sha256(args.database.read_bytes()).hexdigest()
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(result, separators=(',', ':')), encoding='utf-8')
print(json.dumps({'sets': len(result['workout_set']), 'exercises': len(result['exercise']), 'source_sha256': result['source_sha256']}))
