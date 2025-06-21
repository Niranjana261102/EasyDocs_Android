package com.easydocs.ai;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;


import com.itextpdf.kernel.pdf.PdfDocument;     // ✅ iText 7
import com.itextpdf.kernel.pdf.PdfReader;       // ✅ iText 7
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;


import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DocumentProcessor {
    private static final String TAG = "DocumentProcessor";
    private Context context;
    private ExecutorService executor;

    public interface ProcessingCallback {
        void onSuccess(DocumentItem document);
        void onError(String error);
        void onProgress(int progress);
    }

    public DocumentProcessor(Context context) {
        this.context = context;
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void processDocument(Uri uri, ProcessingCallback callback) {
        executor.execute(() -> {
            try {
                callback.onProgress(10);

                // Get document info
                DocumentInfo info = getDocumentInfo(uri);
                callback.onProgress(30);

                // Create DocumentItem
                DocumentItem document = new DocumentItem(info.name, uri);
                document.setMimeType(info.mimeType);
                document.setSize(info.size);

                callback.onProgress(50);

                if (isTextFile(info.mimeType)) {
                    String content = readTextContent(uri);
                    document.setContent(content);
                } else if (info.mimeType.equals("application/pdf")) {
                    String content = extractPdfContent(uri);  // <- Implement this
                    document.setContent(content);
                } else if (info.mimeType.contains("word")) {
                    String content = extractDocxContent(uri); // <- Implement this
                    document.setContent(content);
                }


                callback.onProgress(100);
                callback.onSuccess(document);

            } catch (Exception e) {
                Log.e(TAG, "Error processing document: " + e.getMessage(), e);
                callback.onError(e.getMessage());
            }
        });
    }

    private String extractDocxContent(Uri uri) {
        StringBuilder content = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            XWPFDocument document = new XWPFDocument(inputStream);
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                content.append(paragraph.getText()).append("\n");
            }
            document.close();
        } catch (Exception e) {
            Log.e("DocumentProcessor", "Error reading DOCX content: " + e.getMessage(), e);
        }
        return content.toString();
    }

    private String extractPdfContent(Uri uri) {
        StringBuilder content = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            PdfReader reader = new PdfReader(inputStream);
            PdfDocument pdfDoc = new PdfDocument(reader);
            int pageCount = pdfDoc.getNumberOfPages();

            for (int i = 1; i <= pageCount; i++) {
                String pageText = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(i));
                content.append(pageText).append("\n");
            }

            pdfDoc.close();
        } catch (Exception e) {
            Log.e("DocumentProcessor", "Error reading PDF content: " + e.getMessage(), e);
        }
        return content.toString();
    }

    private DocumentInfo getDocumentInfo(Uri uri) {
        DocumentInfo info = new DocumentInfo();

        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                // Get file name
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    info.name = cursor.getString(nameIndex);
                }

                // Get file size
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0) {
                    info.size = cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting document info: " + e.getMessage(), e);
        }

        // If name is still null, try to get it from URI
        if (info.name == null) {
            String path = uri.getPath();
            if (path != null && path.contains("/")) {
                info.name = path.substring(path.lastIndexOf("/") + 1);
            } else {
                info.name = "Document_" + System.currentTimeMillis();
            }
        }

        // Get MIME type
        info.mimeType = context.getContentResolver().getType(uri);
        if (info.mimeType == null) {
            info.mimeType = getMimeTypeFromFileName(info.name);
        }

        Log.d(TAG, "Document info: " + info.name + ", " + info.size + " bytes, " + info.mimeType);
        return info;
    }

    private String readTextContent(Uri uri) {
        StringBuilder content = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error reading text content: " + e.getMessage(), e);
            return null;
        }

        return content.toString();
    }

    private boolean isTextFile(String mimeType) {
        if (mimeType == null) return false;
        return mimeType.startsWith("text/") ||
                mimeType.equals("application/json") ||
                mimeType.equals("application/xml");
    }

    private String getMimeTypeFromFileName(String fileName) {
        if (fileName == null) return "application/octet-stream";

        String extension = "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            extension = fileName.substring(lastDot + 1).toLowerCase();
        }

        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mimeType != null ? mimeType : "application/octet-stream";
    }

    private static class DocumentInfo {
        String name;
        String mimeType;
        long size;
    }

    public void cleanup() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}