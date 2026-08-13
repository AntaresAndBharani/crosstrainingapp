# ?? CrossTraining App Release & Build Rules

1. **Local Test APK Sync**:
   - Every time a new APK build (`assembleDebug`, `assembleRelease`, or release build) is generated, copy the resulting APK to `local_test\latest.apk`.
   - Ensure `local_test\` remains ignored in `.gitignore`.
