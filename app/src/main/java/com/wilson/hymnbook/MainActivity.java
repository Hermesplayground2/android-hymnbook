package com.wilson.hymnbook;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_TAKE_PHOTO = 1001;
    private static final int REQUEST_PICK_PHOTO = 1002;
    private static final int PERM_CODE = 5000;

    private TesseractOcr ocr = new TesseractOcr();
    private boolean camGranted;
    private boolean storageGranted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            ocr.init(this);
        } catch (RuntimeException e) {
            Toast.makeText(this, "OCR engine not available", Toast.LENGTH_LONG).show();
            throw e;
        }
        camGranted = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        storageGranted = checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

        Button go = findViewById(R.id.go);
        go.setOnClickListener(v -> ensurePermissions());
    }

    private void ensurePermissions() {
        if (camGranted && storageGranted) {
            chooseImage();
            return;
        }
        String[] perms = {Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE};
        ActivityCompat.requestPermissions(this, perms, PERM_CODE, (r, p, g) -> {
            camGranted = g[0] == PackageManager.PERMISSION_GRANTED;
            storageGranted = g[1] == PackageManager.PERMISSION_GRANTED;
            if (camGranted && storageGranted) {
                chooseImage();
            } else {
                Toast.makeText(MainActivity.this, "Camera/gallery permission is required", Toast.LENGTH_SHORT).show();
            }
        });
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
        android.content.Intent take = new android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = new File(getCacheDir(), "hymn_card.jpg");
        Uri photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider",
                photoFile);
        take.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoUri);
        try {
            startActivityForResult(take, REQUEST_TAKE_PHOTO);
        } catch (Exception e) {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void dispatchGallery() {
        android.content.Intent pick = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
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
        try {
            Bitmap b = decodeBitmap(src);
            String text = ocr.recognize(b);
            List<String> cands = HymnbookOcr.lines(text);
            String title = cands.isEmpty() ? "" : cands.get(0);

            File imgFile = copyToLocal(src);
            File out = new File(getExternalFilesDir(null), "hymns_import.csv");
            HymnbookOcr.Record rec = HymnbookOcr.record(title, imgFile);
            HymnbookOcr.writeCsv(out, rec);

            TextView tv = findViewById(R.id.result);
            tv.setText("Title: " + title + "\n" +
                    "OCR lines: " + cands.size() + "\n" +
                    "CSV -> " + out.getAbsolutePath());
            Toast.makeText(this, "Indexed: " + title + "  ->  " + out.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "OCR failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private File copyToLocal(Uri src) throws IOException {
        File out = new File(getExternalFilesDir(null), "hymn_card.jpg");
        try (InputStream in = getContentResolver().openInputStream(src);
                FileOutputStream f = new FileOutputStream(out)) {
            if (in == null) throw new IOException("cannot open " + src);
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) f.write(buf, 0, n);
        }
        return out;
    }

    private Bitmap decodeBitmap(Uri src) throws IOException {
        InputStream in = getContentResolver().openInputStream(src);
        if (in == null) throw new IOException("cannot open image");
        Bitmap b = BitmapFactory.decodeStream(in);
        in.close();
        return b;
    }
}
