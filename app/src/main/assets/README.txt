# AndroidHymnbook

Run on an Android phone: photo of a hymn card -> on-device OCR -> title proposal -> index CSV
that loads straight into the Access `Hymnal` table.

## Build (Android Studio)

1. `File ▸ Open` this folder.
2. AGP 8.3 + Gradle 8.7 sync. Accept the Android SDK (API 34) + NDK (r26) if asked.
3. `Build ▸ Make Project`, then `Build ▸ Generate Signed Bundle / APK` (APK).
4. On first build Android Studio creates a debug keystore at `~/.android/debug.keystore`.

## Run on a phone

- Enable **Usb Debugging**; plug the phone and click `Run`.
- Or deploy the APK to the device.

## The `eng` traineddata

`tesseract-android-lib` resolves `eng.traineddata` from `assets/tessdata/`. To pre-bundle it:

`curl -L https://raw.githubusercontent.com/tesseract-ocr/tessdata/main/eng.traineddata \
  -o app/src/main/assets/tessdata/eng.traineddata`

## OCR + Access index logic

The pure-Java core (`hymnbook-ocr/`) is the same verified pipeline as the desktop CLI
(photo -> grayscale+upscale -> tesseract --psm 6 -> longest multi-word line).
On Android it runs in-device with `tesseract-android-lib` instead of the CLI (secrets never
leave the phone).

## Access

Emitted CSV columns match the Access `Hymnal` table (`title, author, composer, meter,
topic_code, source, source_file`; `id` is autonumber, never emitted). Append the CSV in
Access: `External Data ▸ Text File ▸ Append`.
