package com.wilson.hymnbook;

import android.graphics.Bitmap;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Core OCR + indexing logic. Proposed title, staging, and `hymns_import.csv` (matches the Access
 * `Hymnal` table: `id` is autonumbered, so it is never emitted). */
public class HymnbookOcr {

    public static final String SOURCE = "photo_ocr";
    private static final String LINE_SEP = "\n";

    public static class Record {
        public final String addedDate, title, author, composer, meter, topicCode, source, sourceFile;

        public Record(String addedDate, String title, String author, String composer,
                      String meter, String topicCode, String source, String sourceFile) {
            this.addedDate = addedDate;
            this.title = title;
            this.author = author;
            this.composer = composer;
            this.meter = meter;
            this.topicCode = topicCode;
            this.source = source;
            this.sourceFile = sourceFile;
        }
    }

    /** Clean one OCR line: alphanumeric + spaces/apostrophes/hyphens/dots/ampersands. */
    public static String clean(String s) {
        if (s == null) return null;
        s = s.replaceAll("[^A-Za-z0-9 &''\\-/.]", " ");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    /** Pick the title: longest clean line that is at least 4 characters long. */
    public static String pickTitle(String ocrText) {
        String best = null;
        for (String raw : ocrText.split(LINE_SEP)) {
            String c = clean(raw);
            if (c == null || c.length() < 4) continue;
            if (best == null || c.length() > best.length()) best = c;
        }
        return best;
    }

    /** All clean, title-worthy lines (kept, not thrown away). */
    public static List<String> lines(String ocrText) {
        List<String> out = new ArrayList<>();
        for (String raw : ocrText.split(LINE_SEP)) {
            String c = clean(raw);
            if (c != null && c.length() >= 4) out.add(c);
        }
        return out;
    }

    public static Record record(String title, File imageFile) {
        return new Record(addedDate(), title, "UNKNOWN", "UNKNOWN", "UNKNOWN",
                "UNKNOWN", SOURCE, imageFile.getAbsolutePath());
    }

    static String addedDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** Write `hymns_import.csv` exactly matching the Access `Hymnal` columns; no `id`. */
    public static void writeCsv(File csvFile, Record r) throws IOException {
        File parent = csvFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (OutputStream o = new FileOutputStream(csvFile)) {
            o.write("added_date,title,author,composer,meter,topic_code,source,source_file\n"
                    .getBytes(StandardCharsets.UTF_8));
            String row = String.format(Locale.US, "%s,%s,%s,%s,%s,%s,%s,%s\n",
                    r.addedDate, csvField(r.title), csvField(r.author), csvField(r.composer),
                    csvField(r.meter), csvField(r.topicCode), r.source, csvField(r.sourceFile));
            o.write(row.getBytes(StandardCharsets.UTF_8));
        }
    }

    static String csvField(String v) {
        String s = v == null ? "" : v.replace("\"", "\"\"");
        return "\"" + s + "\"";
    }

    public static Uri toFileUri(File f) {
        return android.net.Uri.fromFile(f);
    }
}
