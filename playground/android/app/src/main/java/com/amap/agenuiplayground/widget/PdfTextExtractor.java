package com.amap.agenuiplayground.widget;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.InputStream;

/**
 * Extracts text from PDF files using PdfBox-Android.
 *
 * Initialization must happen once per process. The extractor limits
 * extraction to the first 50 pages to prevent OOM on large PDFs.
 */
public class PdfTextExtractor {

    private static final String TAG = "PdfTextExtractor";
    private static final int MAX_PAGES = 50;
    private static boolean initialized = false;

    public static synchronized void ensureInitialized(Context context) {
        if (!initialized) {
            try {
                PDFBoxResourceLoader.init(context.getApplicationContext());
                initialized = true;
                Log.d(TAG, "PdfBox initialized");
            } catch (Exception e) {
                Log.e(TAG, "PdfBox init failed", e);
            }
        }
    }

    /**
     * Extracts text from a PDF Uri.
     *
     * @param context Context for content resolver
     * @param uri     PDF file Uri
     * @return Extracted text (max 4000 chars), or null on failure
     */
    public static String extractText(Context context, Uri uri) {
        ensureInitialized(context);
        if (!initialized) {
            Log.e(TAG, "PdfBox not initialized");
            return null;
        }

        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                Log.e(TAG, "Cannot open input stream for: " + uri);
                return null;
            }

            PDDocument document = PDDocument.load(is);
            try {
                int pageCount = document.getNumberOfPages();
                Log.d(TAG, "PDF has " + pageCount + " pages");

                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                stripper.setStartPage(1);
                stripper.setEndPage(Math.min(pageCount, MAX_PAGES));

                String text = stripper.getText(document);
                // Truncate to 4000 chars for LLM
                if (text.length() > 4000) {
                    text = text.substring(0, 4000);
                    Log.d(TAG, "Truncated to 4000 chars");
                }
                return text.trim();
            } finally {
                document.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "PDF extraction failed", e);
            return null;
        }
    }
}
