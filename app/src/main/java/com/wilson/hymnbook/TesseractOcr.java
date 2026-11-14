package com.wilson.hymnbook;

import android.content.res.AssetManager;
import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.googlecode.tesseract.android.TessBaseAPI;

/**
 * On-device OCR: tesseract-android-lib via JitPack
 * ({@code cz.adaptech.tesseract4android:tesseract4android:4.9.0}).
 *
 * <p>
 * The native {@code libtesseract} is resolved from the library AAR's {@code jni} directory by
 * Gradle; the English model ({@code eng.traineddata}) is shipped in the APK's assets and copied
 * into the app's private files dir on first launch (the library requires the traineddata to be
 * readable on device storage, not inside the read-only APK). See README for the download.
 *
 * <p>
 * Reuses the same title-selection + CSV logic as the verified desktop CLI
 * (HymnbookOcr.pickTitle / lines / writeCsv).
 */
public class TesseractOcr extends HymnbookOcr {

    private final TessBaseAPI api = new TessBaseAPI();
    private boolean inited;

    /** Call once, from the Activity (onCreate), before recognizing any card. */
    public void init(android.content.Context ctx) {
        if (inited) return;
        if (ctx == null) throw new IllegalStateException("TesseractOcr.init(Context) not called");
        // The library requires <datapath>/tessdata/<lang>.traineddata (it checks that
        // "<datapath>/tessdata" exists and passes "<datapath>/tessdata" to nativeInitOem).
        File dataDir = new File(ctx.getFilesDir(), "tesseract");
        File tessdataDir = new File(dataDir, "tessdata");
        File model = new File(tessdataDir, "eng.traineddata");
        if (!model.exists() || model.length() < 1_000_000L) {
            copyFromAssets(ctx.getAssets(), "tesseract/eng.traineddata", model);
        }
        if (model.length() < 1_000_000L) {
            throw new IllegalStateException("eng.traineddata missing or truncated (" +
                    model.getAbsolutePath() + ", " + model.length() + " bytes)");
        }
        if (!api.init(dataDir.getAbsolutePath(), "eng")) {
            api.recycle();
            throw new IllegalStateException("Tesseract failed to initialize (" +
                    tessdataDir.getAbsolutePath() + ")");
        }
        inited = true;
    }

    private void copyFromAssets(AssetManager am, String path, File target) {
        try (InputStream in = am.open(path)) {
            if (in == null) throw new IllegalStateException("asset not found: " + path);
            if (!target.getParentFile().exists()) target.getParentFile().mkdirs();
            try (OutputStream o = new FileOutputStream(target)) {
                byte[] buf = new byte[8192];
                int c;
                while ((c = in.read(buf)) != -1) o.write(buf, 0, c);
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot copy " + path + " to " + target, e);
        }
    }

    public String recognize(Bitmap b) {
        if (!inited) throw new IllegalStateException("TesseractOcr.init(Context) not called");
        api.setImage(b);
        String t = api.getUTF8Text();
        return t == null ? "" : t;
    }
}
