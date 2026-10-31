# Build My Laundry Manager APK using only an Android phone

This project includes a GitHub Actions workflow at `.github/workflows/android-apk.yml`.

1. Extract this ZIP on your phone.
2. In a browser, sign in to GitHub and create a **public** or **private** repository named `My-Laundry-Manager` (a private repository is fine).
3. Open the repository, choose **Add file → Upload files**, and upload the project contents (the root `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `app` folder, and `.github` folder). Do not upload the ZIP as one file.
4. Commit the upload to the `main` branch.
5. Open the repository's **Actions** tab. Select **Build My Laundry Manager APK**. If it has not started automatically, choose **Run workflow → Run workflow**.
6. Wait for the workflow to finish with a green check mark. Open that run, scroll to **Artifacts**, and download `My-Laundry-Manager-debug-apk`.
7. Extract the downloaded artifact ZIP. Tap `app-debug.apk` to install it. If Android asks, allow your browser or file manager to install unknown apps for this installation, then install.

The APK is a debug build for personal testing, not a Play Store release. Keep your GitHub repository private if you do not want the source code public. This project has not yet been verified on a physical phone.
