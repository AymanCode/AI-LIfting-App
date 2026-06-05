# Cardio OCR Evaluation Fixtures

Put real cardio machine screen images in this folder when validating the 70% OCR gate.

Use images that are legal to keep in the repo, or keep the images local and do not commit them. The test reads `manifest.json` from this same folder.

Example `manifest.json`:

```json
[
  {
    "file": "treadmill-01.jpg",
    "expectedMachineType": "treadmill",
    "requiredFields": ["duration", "distance", "calories"],
    "minRecognizedFields": 3
  }
]
```

Supported `requiredFields` values:

- `duration`
- `distance`
- `calories`
- `heartRate`
- `speed`
- `machineType`

The instrumentation test passes only when at least 70% of manifest cases are recognized.

To run a temporary image set without committing it, install the debug app/test APK, stage the
folder on the emulator/device, copy it into the app sandbox, then pass that app-owned path:

```bash
./gradlew installDebug installDebugAndroidTest
adb push /tmp/cardio_ocr_eval/. /data/local/tmp/cardio_ocr_eval/
adb shell run-as com.ayman.ecolift mkdir -p files/cardio_ocr_eval
adb shell run-as com.ayman.ecolift cp /data/local/tmp/cardio_ocr_eval/* files/cardio_ocr_eval/
adb shell am instrument -w -r \
  -e class com.ayman.ecolift.cardio.ocr.CardioOcrImageEvaluationInstrumentedTest \
  -e cardioOcrEvalDir /data/data/com.ayman.ecolift/files/cardio_ocr_eval \
  com.ayman.ecolift.test/androidx.test.runner.AndroidJUnitRunner
```
