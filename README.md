# Manga Reader (offline)

1. Create a new GitHub repository.
2. Upload everything in this folder to it, including the hidden `.github` folder.
3. Open the repo's Actions tab and wait for "Build APK" to finish (about 3 to 5 minutes).
4. Open the repo's Releases page, download `manga-reader.apk`, and install it.
   (Android will ask you to allow installs from your browser or file manager.)

To change the reader later, edit `app/src/main/assets/index.html` and commit; a new APK is built automatically.

If the app opens to a blank screen, add this line inside `<manifest>` in `AndroidManifest.xml`
and rebuild: `<uses-permission android:name="android.permission.INTERNET" />`
