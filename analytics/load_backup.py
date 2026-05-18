import argparse
import json
from pathlib import Path
from typing import Iterable, Sequence

import duckdb


def load_backup(input_path: str | Path, output_path: str | Path) -> None:
    input_path = Path(input_path)
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    if output_path.exists():
        output_path.unlink()

    with input_path.open("r", encoding="utf-8") as handle:
        backup = json.load(handle)

    con = duckdb.connect(str(output_path))
    try:
        _create_tables(con)
        _load_tables(con, backup)
        _create_views(con)
    finally:
        con.close()


def _create_tables(con: duckdb.DuckDBPyConnection) -> None:
    con.execute(
        """
        create table dim_exercise (
            exercise_id bigint primary key,
            exercise_name varchar not null,
            muscle_groups varchar not null,
            is_bodyweight boolean not null,
            created_at_ms bigint not null
        )
        """
    )
    con.execute(
        """
        create table dim_date (
            date_key date primary key,
            year integer not null,
            month integer not null,
            day integer not null
        )
        """
    )
    con.execute(
        """
        create table fact_workout_set (
            set_id bigint primary key,
            exercise_id bigint not null,
            workout_date date not null,
            set_number integer not null,
            weight_storage integer,
            weight_lbs double,
            reps integer,
            is_bodyweight boolean not null,
            completed boolean not null,
            rest_time_seconds integer,
            volume_lbs double
        )
        """
    )
    con.execute(
        """
        create table fact_split_assignment (
            assignment_id bigint primary key,
            split_id bigint not null,
            exercise_id bigint not null,
            order_index integer not null
        )
        """
    )
    con.execute(
        """
        create table fact_agent_turn (
            turn_id bigint primary key,
            timestamp_ms bigint not null,
            user_text varchar not null,
            turn_kind varchar not null,
            latency_ms bigint not null,
            error_message varchar,
            audit_id bigint
        )
        """
    )
    con.execute(
        """
        create table fact_patch_audit (
            audit_id bigint primary key,
            request_id varchar not null,
            timestamp_ms bigint not null,
            patch_count integer not null,
            inverse_patch_count integer not null,
            user_confirmed boolean not null,
            is_undo boolean not null
        )
        """
    )


def _load_tables(con: duckdb.DuckDBPyConnection, backup: dict) -> None:
    exercises = backup.get("exercises", [])
    workout_days = backup.get("workoutDays", [])
    workout_sets = backup.get("workoutSets", [])
    split_exercises = backup.get("splitExercises", [])
    agent_turns = backup.get("agentTurns", [])
    audit_entries = backup.get("auditEntries", [])

    _insert_many(
        con,
        "dim_exercise",
        ["exercise_id", "exercise_name", "muscle_groups", "is_bodyweight", "created_at_ms"],
        [
            (
                row["id"],
                row["name"],
                row.get("muscleGroups", ""),
                bool(row.get("isBodyweight", False)),
                row.get("createdAt", 0),
            )
            for row in exercises
        ],
    )

    date_keys = {row["date"] for row in workout_days}
    date_keys.update(row["date"] for row in workout_sets)
    _insert_many(
        con,
        "dim_date",
        ["date_key", "year", "month", "day"],
        [(date, int(date[0:4]), int(date[5:7]), int(date[8:10])) for date in sorted(date_keys)],
    )

    _insert_many(
        con,
        "fact_workout_set",
        [
            "set_id",
            "exercise_id",
            "workout_date",
            "set_number",
            "weight_storage",
            "weight_lbs",
            "reps",
            "is_bodyweight",
            "completed",
            "rest_time_seconds",
            "volume_lbs",
        ],
        [_workout_set_row(row) for row in workout_sets],
    )

    _insert_many(
        con,
        "fact_split_assignment",
        ["assignment_id", "split_id", "exercise_id", "order_index"],
        [
            (
                row["id"],
                row["splitId"],
                row["exerciseId"],
                row.get("orderIndex", 0),
            )
            for row in split_exercises
        ],
    )

    _insert_many(
        con,
        "fact_agent_turn",
        ["turn_id", "timestamp_ms", "user_text", "turn_kind", "latency_ms", "error_message", "audit_id"],
        [
            (
                row["id"],
                row["timestamp"],
                row["userText"],
                row["turnKind"],
                row["latencyMs"],
                row.get("errorMessage"),
                row.get("auditId"),
            )
            for row in agent_turns
        ],
    )

    _insert_many(
        con,
        "fact_patch_audit",
        ["audit_id", "request_id", "timestamp_ms", "patch_count", "inverse_patch_count", "user_confirmed", "is_undo"],
        [
            (
                row["id"],
                row["requestId"],
                row["timestamp"],
                _count_json_array(row.get("serializedPatches")),
                _count_json_array(row.get("serializedInverse")),
                bool(row.get("userConfirmed", False)),
                bool(row.get("isUndo", False)),
            )
            for row in audit_entries
        ],
    )


def _workout_set_row(row: dict) -> tuple:
    weight_storage = row.get("weightLbs")
    weight_lbs = weight_storage / 10.0 if weight_storage is not None else None
    reps = row.get("reps")
    volume = weight_lbs * reps if weight_lbs is not None and reps is not None else None
    return (
        row["id"],
        row["exerciseId"],
        row["date"],
        row["setNumber"],
        weight_storage,
        weight_lbs,
        reps,
        bool(row.get("isBodyweight", False)),
        bool(row.get("completed", False)),
        row.get("restTimeSeconds"),
        volume,
    )


def _count_json_array(value: str | None) -> int:
    if not value:
        return 0
    try:
        parsed = json.loads(value)
    except json.JSONDecodeError:
        return 0
    return len(parsed) if isinstance(parsed, list) else 0


def _insert_many(
    con: duckdb.DuckDBPyConnection,
    table: str,
    columns: Sequence[str],
    rows: Iterable[tuple],
) -> None:
    rows = list(rows)
    if not rows:
        return
    placeholders = ", ".join(["?"] * len(columns))
    column_sql = ", ".join(columns)
    con.executemany(f"insert into {table} ({column_sql}) values ({placeholders})", rows)


def _create_views(con: duckdb.DuckDBPyConnection) -> None:
    con.execute(
        """
        create view weekly_volume as
        select
            date_trunc('week', workout_date) as week_start,
            exercise_id,
            sum(coalesce(volume_lbs, 0)) as total_volume_lbs,
            count(*) as set_count
        from fact_workout_set
        group by 1, 2
        """
    )
    con.execute(
        """
        create view exercise_prs as
        select
            e.exercise_name,
            s.exercise_id,
            max(s.weight_lbs) as max_weight_lbs,
            max(s.reps) as max_reps
        from fact_workout_set s
        join dim_exercise e using (exercise_id)
        group by 1, 2
        """
    )
    con.execute(
        """
        create view workout_adherence as
        select
            date_trunc('week', date_key) as week_start,
            count(distinct date_key) as workout_days
        from dim_date
        group by 1
        """
    )
    con.execute(
        """
        create view agent_reliability as
        select
            total_turns,
            error_count,
            case when total_turns = 0 then 0 else error_count::double / total_turns end as error_rate,
            avg_latency_ms
        from (
            select
                count(*) as total_turns,
                sum(case when turn_kind = 'Error' or error_message is not null then 1 else 0 end) as error_count,
                avg(latency_ms) as avg_latency_ms
            from fact_agent_turn
        )
        """
    )
    con.execute(
        """
        create view agent_undo_rate as
        select
            count(*) as audit_count,
            sum(case when is_undo then 1 else 0 end) as undo_count,
            case when count(*) = 0 then 0 else sum(case when is_undo then 1 else 0 end)::double / count(*) end as undo_rate
        from fact_patch_audit
        """
    )
    con.execute(
        """
        create view data_quality_issues as
        select 'orphan_workout_sets' as issue_type, count(*) as issue_count
        from fact_workout_set s
        left join dim_exercise e using (exercise_id)
        where e.exercise_id is null
        union all
        select 'invalid_reps', count(*)
        from fact_workout_set
        where reps is not null and reps <= 0
        union all
        select 'invalid_weight_lbs', count(*)
        from fact_workout_set
        where weight_lbs is not null and weight_lbs < 0
        union all
        select 'duplicate_split_assignments', count(*)
        from (
            select split_id, exercise_id
            from fact_split_assignment
            group by 1, 2
            having count(*) > 1
        )
        """
    )


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Load an EcoLift backup JSON into a DuckDB analytics mart.")
    parser.add_argument("--input", required=True, help="Path to EcoLift backup JSON")
    parser.add_argument("--out", required=True, help="Output .duckdb path")
    args = parser.parse_args(argv)

    load_backup(args.input, args.out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
