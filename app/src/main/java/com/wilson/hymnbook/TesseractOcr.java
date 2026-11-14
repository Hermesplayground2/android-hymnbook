package com.wilson.hymnbook;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

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

    private static final int INIT_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 600L;

    private TessBaseAPI api;
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

        // Native init occasionally fails on the first attempt (memory pressure / cold start),
        // so retry with a fresh API instance each time instead of giving up immediately.
        for (int attempt = 1; attempt <= INIT_ATTEMPTS; attempt++) {
            TessBaseAPI candidate = new TessBaseAPI();
            if (candidate.init(dataDir.getAbsolutePath(), "eng")) {
                api = candidate;
                inited = true;
                Log.i("Hymbook", "Tesseract initialized (" + tessdataDir.getAbsolutePath()
                        + ") on attempt " + attempt);
                return;
            }
            candidate.recycle();
            Log.w("Hymbook", "Tesseract init attempt " + attempt + "/" + INIT_ATTEMPTS
                    + " failed for " + tessdataDir.getAbsolutePath());
            if (attempt < INIT_ATTEMPTS) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new IllegalStateException("Tesseract failed to initialize ("
                + tessdataDir.getAbsolutePath() + ") after " + INIT_ATTEMPTS + " attempts");
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
        if (!inited || api == null) throw new IllegalStateException("TesseractOcr.init(Context) not called");
        api.setImage(b);
        String t = api.getUTF8Text();
        return t == null ? "" : t;
    }
}
