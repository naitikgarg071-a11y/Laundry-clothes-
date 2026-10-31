# My Laundry Manager — Android source project (v2)

## Features
- Weekly schedule: Sunday afternoon and Wednesday evening, with same-day return workflow.
- Handover quantities and category-level return checks for T-shirts, Shirts, Jeans/trousers, Shorts, Jackets, Bedsheets, Towels and Pillow covers.
- Individual clothing register: add a name, category and optional photo from the Android photo picker.
- Laundry payment log with date, note and running total.
- Missing wardrobe tracker initialized with 1 T-shirt and 1 lower (not marked as given to the laundry person).
- Monthly overview of recorded rounds and quantities.
- Export/share a JSON backup of records, clothing register, payments and missing-item counts.
- Weekly notification reminders: Sunday 2:00 PM and Wednesday 7:00 PM. These are sensible default times because the schedule was given as afternoon/evening, not exact times.
- Local storage on the phone.

## Build and install
1. Install Android Studio and Android SDK 35 on a computer.
2. Open this project folder in Android Studio and allow Gradle sync.
3. Connect an Android phone or start an emulator.
4. Press Run, or choose Build > Build Bundle(s) / APK(s) > Build APK(s).

## Notes / limitations
- This is source code, not a prebuilt APK. It has not been compiled or tested on a physical Android device in this environment.
- Reminders are enabled from More > Enable weekly reminders. Android 13+ asks for notification permission.
- Reminder defaults are Sunday 2 PM and Wednesday 7 PM. They can be adjusted in ReminderScheduler.
- Export creates a shareable JSON backup. Import/restore is not yet implemented; keep the exported file safe.
- All data is stored locally; no laundry person or cloud account is contacted automatically.
