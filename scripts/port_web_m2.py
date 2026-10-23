"""Copy the web snapshot's pure Kotlin into the Android tree and split Room rows
from the web domain models.

One-shot for milestone M2. Running it again will refuse if data/room already exists.
The web snapshot and the ecolift-web checkout are read-only.
"""

from __future__ import annotations

import os
import re
import shutil
import sys

SNAPSHOT = r"C:\Users\ayman\Documents\ecolift-web-snapshot-57eda2a\composeApp\src\wasmJsMain\kotlin\com\ayman\ecolift"
DEST_ROOT = r"C:\Users\ayman\Documents\ecolift-android-parity\src\main\java\com\ayman\ecolift"
ANDROID = r"C:\Users\ayman\Documents\ecolift-android-parity"

ENTITY_FILES = [
    "ArchivedCycle.kt",
    "CardioSession.kt",
    "Cycle.kt",
    "CycleSlot.kt",
    "Exercise.kt",
    "PendingReview.kt",
    "SplitExercise.kt",
    "TempSessionSwap.kt",
    "UserSettings.kt",
    "WorkoutDay.kt",
    "WorkoutSet.kt",
]

# Web wins on these trees. These Android files stay.
SKIP_REL = {
    "data/AppDatabase.kt",
    "data/Migrations.kt",
    "data/DataBackupManager.kt",
    "data/DebugDataHelper.kt",
    "data/WorkoutDataGenerator.kt",
    "data/CardioRepository.kt",
    "data/ExerciseRepository.kt",
    "data/PendingReviewRepository.kt",
    "data/SetRepository.kt",
    "data/TempSessionSwapRepository.kt",
    "data/WorkoutRepository.kt",
    "cardio/health/CardioHealthConnectReader.kt",
    "cardio/ocr/CardioOcrParser.kt",
    "cardio/ocr/MlKitCardioOcrEngine.kt",
    "ai/GroqCloudAgent.kt",
    "ai/AiModelAgent.kt",
    "ai/AiModels.kt",
    "ai/AiToolExecutor.kt",
    "ai/GymContextManifestRepository.kt",
    "ai/IronMindPromptBuilder.kt",
    "agent/engine/GeminiNanoEngine.kt",
    "agent/engine/GroqEdgeEngine.kt",
    "agent/model/AgentTurnLog.kt",
    "agent/model/AuditEntity.kt",
    "agent/model/AgentTurnLogDao.kt",
    "agent/patches/AuditDao.kt",
    "agent/patches/PatchService.kt",
    "agent/patches/InverseComputer.kt",
    "agent/tools/AgentToolsImpl.kt",
}

COPY_TOPS = ("data", "cardio", "ai", "agent")
BROWSER_UTILS = {
    "Console.kt",
    "BrowserChrome.kt",
    "WebHaptics.kt",
    "KeyboardOverlap.kt",
    "ProgressShare.kt",
}

DOMAIN_ENTITIES = [
    "ArchivedCycle",
    "CardioSession",
    "Cycle",
    "CycleSlot",
    "Exercise",
    "PendingReview",
    "SplitExercise",
    "UserSettings",
    "WorkoutDay",
    "WorkoutSet",
]

DAO_FOR = {
    "ArchivedCycle": "archivedCycleDao",
    "CardioSession": "cardioSessionDao",
    "Cycle": "cycleDao",
    "CycleSlot": "cycleSlotDao",
    "Exercise": "exerciseDao",
    "PendingReview": "pendingReviewDao",
    "SplitExercise": "splitExerciseDao",
    "UserSettings": "userSettingsDao",
    "WorkoutDay": "workoutDayDao",
    "WorkoutSet": "workoutSetDao",
}

# These stay on the Room DAO. Web replacements need AppRepos, which is M3.
ROOM_DIRECT_FILES = {
    "data/DataBackupManager.kt",
    "agent/patches/PatchService.kt",
    "agent/patches/InverseComputer.kt",
    "agent/tools/AgentToolsImpl.kt",
}


def read(path: str) -> str:
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def write(path: str, text: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)


def move_entities() -> None:
    room_dir = os.path.join(DEST_ROOT, "data", "room")
    if os.path.isfile(os.path.join(room_dir, "WorkoutSet.kt")) and not os.path.isfile(os.path.join(DEST_ROOT, "data", "WorkoutSet.kt")):
        print("entities already in data/room")
        return
    os.makedirs(room_dir, exist_ok=True)
    for name in ENTITY_FILES:
        src = os.path.join(DEST_ROOT, "data", name)
        dst = os.path.join(room_dir, name)
        if not os.path.isfile(src):
            sys.exit(f"missing entity file: {src}")
        text = read(src).replace(
            "package com.ayman.ecolift.data\n",
            "package com.ayman.ecolift.data.room\n",
            1,
        )
        if name == "CardioSession.kt":
            text = text.split("\nenum class CardioActivityType")[0].rstrip() + "\n"
        write(dst, text)
        os.remove(src)
        print("moved", name)


def should_skip(rel: str) -> bool:
    rel = rel.replace("\\", "/")
    if rel in SKIP_REL:
        return True
    if rel.startswith("data/") and rel.endswith("Dao.kt"):
        return True
    return False


def copy_tree(top: str, copied: list[str]) -> None:
    src_root = os.path.join(SNAPSHOT, top)
    for dirpath, _, filenames in os.walk(src_root):
        for name in filenames:
            if not name.endswith(".kt"):
                continue
            src = os.path.join(dirpath, name)
            rel = os.path.relpath(src, SNAPSHOT).replace("\\", "/")
            if should_skip(rel):
                print("skip", rel)
                continue
            if top == "agent" and rel == "agent/engine/GroqEdgeEngine.kt":
                print("skip", rel)
                continue
            dst = os.path.join(DEST_ROOT, rel.replace("/", os.sep))
            write(dst, read(src))
            copied.append(rel)


def copy_utils(copied: list[str]) -> None:
    src_root = os.path.join(SNAPSHOT, "web", "util")
    for name in os.listdir(src_root):
        if not name.endswith(".kt") or name in BROWSER_UTILS:
            continue
        rel = f"web/util/{name}"
        write(os.path.join(DEST_ROOT, "web", "util", name), read(os.path.join(src_root, name)))
        copied.append(rel)


def write_shims() -> None:
    util = os.path.join(DEST_ROOT, "web", "util")
    write(
        os.path.join(util, "Console.kt"),
        """package com.ayman.ecolift.web.util

import android.util.Log

/** Phone stand-in for the web console breadcrumb. Warnings stay out of the error channel. */
fun consoleWarn(message: String) {
    Log.w("EcoLift", message)
}
""",
    )
    print("wrote Console.kt")


def patch_models() -> None:
    path = os.path.join(DEST_ROOT, "data", "Models.kt")
    text = read(path)
    old = """object CardioSessionSource {
    const val MANUAL = "manual"
    const val OCR = "ocr"
}
"""
    new = """object CardioSessionSource {
    const val MANUAL = "manual"
    const val OCR = "ocr"
    const val HEALTH_CONNECT = "health_connect"
}
"""
    if "const val HEALTH_CONNECT" in text:
        print("Models.kt already patched")
        return
    if old not in text:
        sys.exit("CardioSessionSource block not found in Models.kt")
    text = text.replace(old, new, 1)
    old_end = """    val createdAt: Long,
    val updatedAt: Long,
)
"""
    new_end = """    val createdAt: Long,
    val updatedAt: Long,
    val hcUid: String? = null,
    val hcDataOriginPackage: String? = null,
    val hcLastModifiedTime: Long? = null,
    val ocrEngineVersion: String? = null,
)
"""
    # Only the CardioSession class ends with createdAt/updatedAt in this shape once.
    count = text.count(old_end)
    if count != 1:
        sys.exit(f"expected one CardioSession tail, found {count}")
    text = text.replace(old_end, new_end, 1)
    write(path, text)
    print("patched Models.kt with Health Connect fields")


def patch_archive_card() -> None:
    path = os.path.join(DEST_ROOT, "ui", "viewmodel", "CycleArchiveModels.kt")
    text = read(path)
    old = """data class ArchiveCardUi(
    val id: Long,
    val name: String,
    val dateRangeLabel: String,
    val splitCount: Int,
    val sessionCount: Int,
    val totalVolumeLbs: Long,
)
"""
    new = """data class ArchiveCardUi(
    val id: Long,
    val name: String,
    val dateRangeLabel: String,
    val splitCount: Int,
    val sessionCount: Int,
    val totalVolumeLbs: Long,
    val startIso: String = "",
    val endIso: String = "",
    val weeklySessions: List<Int> = emptyList(),
)
"""
    if "val startIso" in text:
        print("ArchiveCardUi already patched")
        return
    if old not in text:
        sys.exit("ArchiveCardUi block not found")
    write(path, text.replace(old, new, 1))
    print("patched ArchiveCardUi")


MAPPERS = r'''package com.ayman.ecolift.data.room

import com.ayman.ecolift.data.ArchivedCycle as DomainArchivedCycle
import com.ayman.ecolift.data.CardioSession as DomainCardioSession
import com.ayman.ecolift.data.Cycle as DomainCycle
import com.ayman.ecolift.data.CycleSlot as DomainCycleSlot
import com.ayman.ecolift.data.Exercise as DomainExercise
import com.ayman.ecolift.data.PendingReview as DomainPendingReview
import com.ayman.ecolift.data.SplitExercise as DomainSplitExercise
import com.ayman.ecolift.data.UserSettings as DomainUserSettings
import com.ayman.ecolift.data.WorkoutDay as DomainWorkoutDay
import com.ayman.ecolift.data.WorkoutSet as DomainWorkoutSet

fun Exercise.toDomain() = DomainExercise(
    id = id,
    name = name,
    muscleGroups = muscleGroups,
    isBodyweight = isBodyweight,
    createdAt = createdAt,
)

fun DomainExercise.toRoom() = Exercise(
    id = id,
    name = name,
    muscleGroups = muscleGroups,
    isBodyweight = isBodyweight,
    createdAt = if (createdAt == 0L) System.currentTimeMillis() else createdAt,
)

fun WorkoutSet.toDomain() = DomainWorkoutSet(
    id = id,
    exerciseId = exerciseId,
    date = date,
    setNumber = setNumber,
    weightLbs = weightLbs,
    reps = reps,
    isBodyweight = isBodyweight,
    completed = completed,
    restTimeSeconds = restTimeSeconds,
)

fun DomainWorkoutSet.toRoom() = WorkoutSet(
    id = id,
    exerciseId = exerciseId,
    date = date,
    setNumber = setNumber,
    weightLbs = weightLbs,
    reps = reps,
    isBodyweight = isBodyweight,
    completed = completed,
    restTimeSeconds = restTimeSeconds,
)

fun WorkoutDay.toDomain() = DomainWorkoutDay(
    date = date,
    cycleSlotType = cycleSlotType,
    cycleSlotOccurrence = cycleSlotOccurrence,
    alternativeForDate = alternativeForDate,
    cycleSlotId = cycleSlotId,
)

fun DomainWorkoutDay.toRoom() = WorkoutDay(
    date = date,
    cycleSlotType = cycleSlotType,
    cycleSlotOccurrence = cycleSlotOccurrence,
    alternativeForDate = alternativeForDate,
    cycleSlotId = cycleSlotId,
)

fun Cycle.toDomain() = DomainCycle(
    id = id,
    numTypes = numTypes,
    isActive = isActive,
    nextSessionType = nextSessionType,
    startDate = startDate,
    name = name,
)

fun DomainCycle.toRoom() = Cycle(
    id = id,
    numTypes = numTypes,
    isActive = isActive,
    nextSessionType = nextSessionType,
    startDate = startDate,
    name = name,
)

fun CycleSlot.toDomain() = DomainCycleSlot(
    id = id,
    name = name,
    orderIndex = orderIndex,
)

fun DomainCycleSlot.toRoom() = CycleSlot(
    id = id,
    name = name,
    orderIndex = orderIndex,
)

fun SplitExercise.toDomain() = DomainSplitExercise(
    id = id,
    splitId = splitId,
    exerciseId = exerciseId,
    orderIndex = orderIndex,
)

fun DomainSplitExercise.toRoom() = SplitExercise(
    id = id,
    splitId = splitId,
    exerciseId = exerciseId,
    orderIndex = orderIndex,
)

fun PendingReview.toDomain() = DomainPendingReview(
    id = id,
    rawInput = rawInput,
    dateLogged = dateLogged,
    resolved = resolved,
)

fun DomainPendingReview.toRoom() = PendingReview(
    id = id,
    rawInput = rawInput,
    dateLogged = dateLogged,
    resolved = resolved,
)

fun ArchivedCycle.toDomain() = DomainArchivedCycle(
    id = id,
    name = name,
    startDate = startDate,
    endDate = endDate,
    splitCount = splitCount,
    totalVolumeLbs = totalVolumeLbs,
    totalSessions = totalSessions,
    archivedAt = archivedAt,
    schemaVersion = schemaVersion,
    snapshotJson = snapshotJson,
)

fun DomainArchivedCycle.toRoom() = ArchivedCycle(
    id = id,
    name = name,
    startDate = startDate,
    endDate = endDate,
    splitCount = splitCount,
    totalVolumeLbs = totalVolumeLbs,
    totalSessions = totalSessions,
    archivedAt = archivedAt,
    schemaVersion = schemaVersion,
    snapshotJson = snapshotJson,
)

fun UserSettings.toDomain() = DomainUserSettings(
    userBodyweightLbs = userBodyweightLbs,
    glassPaletteChoice = glassPaletteChoice,
)

fun DomainUserSettings.toRoom() = UserSettings(
    id = USER_SETTINGS_ID,
    userBodyweightLbs = userBodyweightLbs,
    glassPaletteChoice = glassPaletteChoice,
)

fun CardioSession.toDomain() = DomainCardioSession(
    id = id,
    localUuid = localUuid,
    date = date,
    activityType = activityType,
    activityLabel = activityLabel,
    durationSec = durationSec,
    distanceM = distanceM,
    calories = calories,
    avgHeartRate = avgHeartRate,
    maxHeartRate = maxHeartRate,
    avgSpeed = avgSpeed,
    avgInclinePercent = avgInclinePercent,
    cadenceRpm = cadenceRpm,
    resistanceLevel = resistanceLevel,
    avgPowerWatts = avgPowerWatts,
    strokeRateSpm = strokeRateSpm,
    paceSecPer500m = paceSecPer500m,
    floors = floors,
    steps = steps,
    source = source,
    machineType = machineType,
    ocrConfidence = ocrConfidence,
    notes = notes,
    startTime = startTime,
    endTime = endTime,
    zoneOffsetSeconds = zoneOffsetSeconds,
    createdAt = createdAt,
    updatedAt = updatedAt,
    hcUid = hcUid,
    hcDataOriginPackage = hcDataOriginPackage,
    hcLastModifiedTime = hcLastModifiedTime,
    ocrEngineVersion = ocrEngineVersion,
)

fun DomainCardioSession.toRoom() = CardioSession(
    id = id,
    localUuid = localUuid,
    date = date,
    activityType = activityType,
    activityLabel = activityLabel,
    durationSec = durationSec,
    distanceM = distanceM,
    calories = calories,
    avgHeartRate = avgHeartRate,
    maxHeartRate = maxHeartRate,
    avgSpeed = avgSpeed,
    avgInclinePercent = avgInclinePercent,
    cadenceRpm = cadenceRpm,
    resistanceLevel = resistanceLevel,
    avgPowerWatts = avgPowerWatts,
    strokeRateSpm = strokeRateSpm,
    paceSecPer500m = paceSecPer500m,
    floors = floors,
    steps = steps,
    source = source,
    hcUid = hcUid,
    hcDataOriginPackage = hcDataOriginPackage,
    hcLastModifiedTime = hcLastModifiedTime,
    startTime = startTime,
    endTime = endTime,
    zoneOffsetSeconds = zoneOffsetSeconds,
    machineType = machineType,
    ocrConfidence = ocrConfidence,
    ocrEngineVersion = ocrEngineVersion,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
'''


def dao_functions(text: str) -> list[str]:
    lines = text.splitlines()
    funcs = []
    index = 0
    while index < len(lines):
        line = lines[index]
        stripped = line.strip()
        if re.search(r"\bfun\s+\w+", stripped) and not stripped.startswith("//") and not stripped.startswith("*"):
            buf = stripped
            while buf.count("(") > buf.count(")"):
                index += 1
                buf += " " + lines[index].strip()
            funcs.append(buf)
        index += 1
    return funcs


def parse_fun(sig: str) -> tuple[str, str, str, str] | None:
    sig = sig.strip()
    if sig.endswith("{"):
        sig = sig[:-1].strip()
    match = re.search(
        r"^(?P<prefix>(?:public |internal |private |suspend |operator |infix )*)fun\s+(?P<name>\w+)\s*\((?P<params>.*)\)\s*(?::\s*(?P<ret>.+))?$",
        sig,
    )
    if not match:
        return None
    prefix = match.group("prefix") or ""
    ret = (match.group("ret") or "").strip()
    return prefix, match.group("name"), match.group("params"), ret


def split_params(params: str) -> list[tuple[str, str, str]]:
    if not params.strip():
        return []
    parts = []
    depth = 0
    current = []
    for char in params:
        if char == "<" or char == "(":
            depth += 1
        elif char == ">" or char == ")":
            depth -= 1
        if char == "," and depth == 0:
            parts.append("".join(current).strip())
            current = []
        else:
            current.append(char)
    if current:
        parts.append("".join(current).strip())
    parsed = []
    for part in parts:
        if not part:
            continue
        # name: Type = default
        match = re.match(r"^(\w+)\s*:\s*([^=]+?)(?:\s*=\s*(.+))?$", part)
        if not match:
            raise SystemExit(f"unparsed param: {part}")
        parsed.append((match.group(1), match.group(2).strip(), match.group(3)))
    return parsed


def entity_in(type_name: str) -> str | None:
    for entity in sorted(DOMAIN_ENTITIES, key=len, reverse=True):
        if re.search(rf"\b{entity}\b", type_name):
            return entity
    return None


def call_arg(name: str, type_name: str) -> str:
    entity = entity_in(type_name)
    if entity is None:
        return name
    if type_name.startswith("List<"):
        return f"{name}.map {{ it.toRoom() }}"
    return f"{name}.toRoom()"


def map_return(call: str, type_name: str) -> str:
    if not type_name:
        return call
    entity = entity_in(type_name)
    if entity is None:
        return call
    if type_name.startswith("Flow<List<"):
        return f"{call}.mapFlow {{ rows -> rows.map {{ it.toDomain() }} }}"
    if type_name.startswith("Flow<") and type_name.endswith("?>"):
        return f"{call}.mapFlow {{ row -> row?.toDomain() }}"
    if type_name.startswith("Flow<"):
        return f"{call}.mapFlow {{ row -> row.toDomain() }}"
    if type_name.startswith("List<"):
        return f"{call}.map {{ it.toDomain() }}"
    if type_name.endswith("?"):
        return f"{call}?.toDomain()"
    return f"{call}.toDomain()"


def generate_domain_daos() -> None:
    chunks = [
        "package com.ayman.ecolift.data",
        "",
        "import com.ayman.ecolift.data.room.toDomain",
        "import com.ayman.ecolift.data.room.toRoom",
        "import kotlinx.coroutines.flow.Flow",
        "import kotlinx.coroutines.flow.map as mapFlow",
        "",
    ]
    for entity, dao_method in DAO_FOR.items():
        dao_name = entity + "Dao"
        path = os.path.join(DEST_ROOT, "data", dao_name + ".kt")
        functions = dao_functions(read(path))
        class_name = "Domain" + dao_name
        chunks.append(f"class {class_name}(private val dao: {dao_name}) {{")
        for sig in functions:
            parsed = parse_fun(sig)
            if parsed is None:
                raise SystemExit(f"unparsed fun in {dao_name}: {sig}")
            prefix, name, params, ret = parsed
            param_list = split_params(params)
            wrapper_params = []
            call_args = []
            for param_name, param_type, default in param_list:
                rendered = f"{param_name}: {param_type}"
                if default is not None:
                    rendered += f" = {default}"
                wrapper_params.append(rendered)
                call_args.append(call_arg(param_name, param_type))
            ret_clause = f": {ret}" if ret else ""
            call = f"dao.{name}({', '.join(call_args)})"
            body = map_return(call, ret)
            chunks.append(
                f"    {prefix}fun {name}({', '.join(wrapper_params)}){ret_clause} = {body}"
            )
        chunks.append("}")
        chunks.append("")
        chunks.append(f"fun {dao_name}.domain() = {class_name}(this)")
        chunks.append("")
    write(os.path.join(DEST_ROOT, "data", "DomainDaos.kt"), "\n".join(chunks))
    print("wrote DomainDaos.kt")


def rename_dao_entities() -> None:
    for entity in DOMAIN_ENTITIES:
        path = os.path.join(DEST_ROOT, "data", entity + "Dao.kt")
        if not os.path.isfile(path):
            raise SystemExit(f"missing dao {path}")
        text = read(path)
        text = re.sub(rf"\b{entity}\b", f"Room{entity}", text)
        import_line = f"import com.ayman.ecolift.data.room.{entity} as Room{entity}\n"
        text = text.replace("package com.ayman.ecolift.data\n", "package com.ayman.ecolift.data\n\n" + import_line, 1)
        write(path, text)
        print("renamed types in", entity + "Dao.kt")
    # Temp session has no domain twin. Point the DAO at the room class.
    path = os.path.join(DEST_ROOT, "data", "TempSessionSwapDao.kt")
    text = read(path)
    text = re.sub(r"\bTempSessionSwap\b", "RoomTempSessionSwap", text)
    import_line = "import com.ayman.ecolift.data.room.TempSessionSwap as RoomTempSessionSwap\n"
    text = text.replace("package com.ayman.ecolift.data\n", "package com.ayman.ecolift.data\n\n" + import_line, 1)
    write(path, text)


def patch_app_database() -> None:
    path = os.path.join(DEST_ROOT, "data", "AppDatabase.kt")
    text = read(path)
    entities = """entities = [
        com.ayman.ecolift.data.room.Exercise::class,
        com.ayman.ecolift.data.room.WorkoutDay::class,
        com.ayman.ecolift.data.room.WorkoutSet::class,
        com.ayman.ecolift.data.room.Cycle::class,
        com.ayman.ecolift.data.room.PendingReview::class,
        com.ayman.ecolift.data.room.CycleSlot::class,
        com.ayman.ecolift.data.room.SplitExercise::class,
        com.ayman.ecolift.data.room.TempSessionSwap::class,
        AuditEntity::class,
        AgentTurnLog::class,
        com.ayman.ecolift.data.room.ArchivedCycle::class,
        com.ayman.ecolift.data.room.UserSettings::class,
        com.ayman.ecolift.data.room.CardioSession::class,
    ],"""
    text2, count = re.subn(r"entities = \[[\s\S]*?\],", entities, text, count=1)
    if count != 1:
        raise SystemExit("AppDatabase entities block not found")
    write(path, text2)
    print("patched AppDatabase")


def rewrite_backup() -> None:
    path = os.path.join(DEST_ROOT, "data", "DataBackupManager.kt")
    text = read(path)
    imports = []
    for entity in DOMAIN_ENTITIES + ["TempSessionSwap"]:
        text = re.sub(rf"\b{entity}\b", f"Room{entity}", text)
        imports.append(f"import com.ayman.ecolift.data.room.{entity} as Room{entity}\n")
    text = text.replace(
        "package com.ayman.ecolift.data\n",
        "package com.ayman.ecolift.data\n\n" + "".join(imports),
        1,
    )
    write(path, text)
    print("rewrote DataBackupManager types")


def add_temp_import() -> None:
    path = os.path.join(DEST_ROOT, "data", "TempSessionSwapRepository.kt")
    text = read(path)
    needle = "package com.ayman.ecolift.data\n"
    imp = "import com.ayman.ecolift.data.room.TempSessionSwap\n"
    if imp not in text:
        text = text.replace(needle, needle + "\n" + imp, 1)
        write(path, text)
        print("imported TempSessionSwap")


def use_domain_daos() -> None:
    methods = list(DAO_FOR.values())
    pattern = re.compile(r"\.(%s)\(\)" % "|".join(methods))
    changed = 0
    for dirpath, _, filenames in os.walk(os.path.join(ANDROID, "src")):
        for name in filenames:
            if not name.endswith(".kt"):
                continue
            path = os.path.join(dirpath, name)
            rel = os.path.relpath(path, os.path.join(ANDROID, "src", "main", "java", "com", "ayman", "ecolift"))
            rel = rel.replace("\\", "/")
            if rel in ROOM_DIRECT_FILES or rel.endswith("Dao.kt") or rel.endswith("AppDatabase.kt") or rel.endswith("DomainDaos.kt"):
                continue
            if "src\\main\\" not in path and "src/main/" not in path.replace("\\", "/"):
                continue
            text = read(path)
            if not pattern.search(text):
                continue
            updated = pattern.sub(lambda match: f".{match.group(1)}().domain()", text)
            if "package com.ayman.ecolift.data\n" not in updated and "import com.ayman.ecolift.data.domain\n" not in updated:
                updated = updated.replace("\n", "\nimport com.ayman.ecolift.data.domain\n", 1)
            write(path, updated)
            changed += 1
            print("domain dao", rel)
    print("domain dao files", changed)


def point_room_agents_at_rows() -> None:
    roots = [
        os.path.join(ANDROID, "src", "main", "java", "com", "ayman", "ecolift", "agent", "patches", "PatchService.kt"),
        os.path.join(ANDROID, "src", "main", "java", "com", "ayman", "ecolift", "agent", "patches", "InverseComputer.kt"),
        os.path.join(ANDROID, "src", "main", "java", "com", "ayman", "ecolift", "agent", "tools", "AgentToolsImpl.kt"),
        os.path.join(ANDROID, "src", "test", "java", "com", "ayman", "ecolift", "agent", "AgentToolsImplTest.kt"),
        os.path.join(ANDROID, "src", "test", "java", "com", "ayman", "ecolift", "agent", "PatchServiceTest.kt"),
    ]
    entity_re = re.compile(
        r"com\.ayman\.ecolift\.data\.(%s)\b" % "|".join(DOMAIN_ENTITIES)
    )
    for path in roots:
        if not os.path.isfile(path):
            print("missing agent file", path)
            continue
        text = read(path)
        updated = entity_re.sub(lambda match: f"com.ayman.ecolift.data.room.{match.group(1)}", text)
        if updated != text:
            write(path, updated)
            print("room types", os.path.basename(path))


def report_clashes() -> None:
    classes: dict[str, list[str]] = {}
    for dirpath, _, filenames in os.walk(DEST_ROOT):
        for name in filenames:
            if not name.endswith(".kt"):
                continue
            path = os.path.join(dirpath, name)
            text = read(path)
            package = re.search(r"^package ([^\n]+)", text, re.M)
            if not package:
                continue
            for found in re.findall(
                r"^(?:public |internal |private |data |sealed |enum |open |abstract |value )*(?:class|interface|object) (\w+)",
                text,
                re.M,
            ):
                key = package.group(1) + "." + found
                classes.setdefault(key, []).append(os.path.relpath(path, DEST_ROOT))
    clashes = {key: paths for key, paths in classes.items() if len(paths) > 1}
    if clashes:
        print("CLASHES", len(clashes))
        for key, paths in sorted(clashes.items()):
            print(key, "->", paths)
    else:
        print("no class clashes")


def main() -> None:
    copied: list[str] = []
    move_entities()
    for top in COPY_TOPS:
        copy_tree(top, copied)
    copy_utils(copied)
    write_shims()
    patch_models()
    patch_archive_card()
    write(os.path.join(DEST_ROOT, "data", "room", "RoomMappers.kt"), MAPPERS)
    generate_domain_daos()
    rename_dao_entities()
    patch_app_database()
    rewrite_backup()
    add_temp_import()
    use_domain_daos()
    point_room_agents_at_rows()
    report_clashes()
    print("copied", len(copied))


if __name__ == "__main__":
    main()
