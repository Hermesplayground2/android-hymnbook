package com.wilson.hymnbook;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
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
    private TesseractOcr ocr = new TesseractOcr();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ocr.init(this);

        Button go = findViewById(R.id.go);
        go.setOnClickListener(v -> chooseImage());
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
        Intent take = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = new File(getCacheDir(), "hymn_card.jpg");
        Uri photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider",
                photoFile);
        take.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        try {
            startActivityForResult(take, REQUEST_TAKE_PHOTO);
        } catch (Exception e) {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void dispatchGallery() {
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.setType("image/*");
        startActivityForResult(pick, REQUEST_PICK_PHOTO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK) {
            handleImage(Uri.fromFile(new File(getCacheDir(), "hymn_card.jpg")));
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
