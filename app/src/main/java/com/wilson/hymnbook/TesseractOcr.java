package com.wilson.hymnbook;

import android.graphics.Bitmap;

import com.rmthes.tesseract.TessBaseAPI;
import com.rmthes.tesseract.TessBaseAPI.PageSegMode;

/**
 * On-device OCR: tesseract-run via tesseract-android-lib (`com.rmthes:tesseract:5.3.2`).
 * <p>
 * The native `libtesseract` is resolved from the library AAR's `jni` directory by Gradle; the
 * English model (`eng.traineddata`) is loaded from `assets/tessdata/` (see README).
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
        api.init(ctx, "eng", TessBaseAPI.OEM_LSTM_ONLY);
        api.setPageSegMode(PageSegMode.PSM_AUTO);
        inited = true;
    }

    @Override
    public String recognize(Bitmap b) {
        if (!inited) throw new IllegalStateException("TesseractOcr.init(Context) not called");
        api.setImage(b);
        String t = api.getUTF8Text();
        api.reset();
        return t == null ? "" : t;
    }
}
