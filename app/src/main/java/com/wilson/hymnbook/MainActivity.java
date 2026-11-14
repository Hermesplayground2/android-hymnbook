package com.wilson.hymnbook;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.Manifest;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.app.ActivityCompat;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_TAKE_PHOTO = 1001;
    private static final int REQUEST_PICK_PHOTO = 1002;
    private static final int PERM_CODE = 5000;

    private TesseractOcr ocr;
    private boolean camGranted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Log any uncaught exception to logcat (tag "Hymbook") before the system handles it,
        // so a crash is diagnosable without a debugger attached.
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Log.e("Hymbook", "UNCAUGHT on thread " + t.getName(), e);
            if (prev != null) prev.uncaughtException(t, e);
        });

        final Button go = findViewById(R.id.go);
        final TextView result = findViewById(R.id.result);

        camGranted = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;

        if (go != null) go.setOnClickListener(v -> ensurePermissions());

        // Loading the 23 MB English model can block for seconds; do it off the main
        // thread so the UI never freezes (ANR) and a failure can never crash the app.
        if (result != null) result.setText("Starting OCR engine…");
        if (go != null) go.setEnabled(false);
        new Thread(() -> {
            try {
                TesseractOcr t = new TesseractOcr();
                t.init(this);
                ocr = t;
                runOnUiThread(() -> {
                    if (result != null) result.setText("OCR ready.");
                    if (go != null) go.setEnabled(true);
                });
            } catch (Throwable e) {
                Log.e("Hymbook", "OCR engine init failed", e);
                String msg = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
                runOnUiThread(() -> {
                    if (result != null) result.setText("OCR engine failed:\n" + msg);
                    Toast.makeText(this, "OCR unavailable: " + msg, Toast.LENGTH_LONG).show();
                });
            }
        }, "hymbook-ocr-init").start();
    }

    private void ensurePermissions() {
        if (camGranted) {
            chooseImage();
            return;
        }
        // Only CAMERA is required: the gallery uses ACTION_GET_CONTENT (system picker, no
        // storage permission) and camera photos are written through FileProvider.
        // READ_EXTERNAL_STORAGE does not apply on Android 13+ (API 33+) and requesting it
        // made the previous build report "permission required" even after the user had
        // granted every dialog that was actually shown.
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERM_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERM_CODE) {
            camGranted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (camGranted) {
                chooseImage();
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void chooseImage() {
        String[] items = {"Take photo", "Choose from gallery"};
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("Hymn card")
                .setItems(items, (d, which) -> {
                    if (which == 0) dispatchCamera();
                    else dispatchGallery();
                });
        b.show();
    }

    private void dispatchCamera() {
        try {
            android.content.Intent take = new android.content.Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            File photoFile = new File(getCacheDir(), "hymn_card.jpg");
            Uri photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider",
                    photoFile);
            take.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            // The camera app runs in another process and needs write access to our
            // FileProvider URI, otherwise saving the shot fails (and can take the caller
            // down with a SecurityException). ClipData makes the grant discoverable.
            take.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            take.setClipData(android.content.ClipData.newRawUri(MediaStore.EXTRA_OUTPUT, photoUri));
            startActivityForResult(take, REQUEST_TAKE_PHOTO);
        } catch (Throwable e) {
            Log.e("Hymbook", "camera launch failed", e);
            Toast.makeText(this, "Camera not available: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void dispatchGallery() {
        android.content.Intent pick = new android.content.Intent(Intent.ACTION_GET_CONTENT);
        pick.setType("image/*");
        startActivityForResult(pick, REQUEST_PICK_PHOTO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK) {
            Uri photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider",
                    new File(getCacheDir(), "hymn_card.jpg"));
            handleImage(photoUri);
        } else if (requestCode == REQUEST_PICK_PHOTO && resultCode == RESULT_OK && data != null) {
            handleImage(data.getData());
        }
    }

    private void handleImage(Uri src) {
        if (ocr == null) {
            Toast.makeText(this, "OCR engine is not ready yet", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Bitmap b = decodeBitmap(src);
            String text = ocr.recognize(b);
            List<String> cands = HymnbookOcr.lines(text);
            String title = cands.isEmpty() ? "" : cands.get(0);

            File imgFile = copyToLocal(src);
            File ext = getExternalFilesDir(null);
            File out = ext == null ? new File(getFilesDir(), "hymns_import.csv")
                    : new File(ext, "hymns_import.csv");
            HymnbookOcr.Record rec = HymnbookOcr.record(title, imgFile);
            HymnbookOcr.writeCsv(out, rec);

            TextView tv = findViewById(R.id.result);
            tv.setText("Title: " + title + "\n" +
                    "OCR lines: " + cands.size() + "\n" +
                    "CSV -> " + out.getAbsolutePath());
            Toast.makeText(this, "Indexed: " + title + "  ->  " + out.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Throwable e) {
            // OutOfMemoryError and other Errors must not kill the app either.
            Log.e("Hymbook", "OCR recognize failed", e);
            Toast.makeText(this, "OCR failed: " + e, Toast.LENGTH_LONG).show();
        }
    }

    private File copyToLocal(Uri src) throws IOException {
        File ext = getExternalFilesDir(null);
        File out = ext == null ? new File(getFilesDir(), "hymn_card.jpg")
                : new File(ext, "hymn_card.jpg");
        try (InputStream in = getContentResolver().openInputStream(src);
                FileOutputStream f = new FileOutputStream(out)) {
            if (in == null) throw new IOException("cannot open " + src);
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) f.write(buf, 0, n);
        }
        return out;
    }

    /**
     * Decode with a capped longest side (~2000 px). A 12 MP camera photo is ~48 MB as an
     * ARGB bitmap, which previously threw OutOfMemoryError (an Error, not caught by the
     * old `catch (Exception)`) and crashed the app. 2000 px is ample for title OCR.
     */
    private Bitmap decodeBitmap(Uri src) throws IOException {
        InputStream in = getContentResolver().openInputStream(src);
        if (in == null) throw new IOException("cannot open image");
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(in, null, bounds);
        in.close();

        int maxDim = Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (maxDim / sample > 2000) sample *= 2;

        InputStream in2 = getContentResolver().openInputStream(src);
        if (in2 == null) throw new IOException("cannot reopen image");
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeStream(in2, null, opts);
            if (b == null) {
                throw new IOException("image could not be decoded ("
                        + bounds.outWidth + "x" + bounds.outHeight + ")");
            }
            return b;
        } finally {
            in2.close();
        }
    }
}
