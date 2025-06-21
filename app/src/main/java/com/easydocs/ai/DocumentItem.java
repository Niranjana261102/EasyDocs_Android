package com.easydocs.ai;

import android.net.Uri;
import java.io.Serializable;

public class DocumentItem implements Serializable {
    private String name;
    private String path;
    private String mimeType;
    private long size;
    private long dateAdded;
    private Uri uri;
    private String content; // For processed text content

    public DocumentItem(String name, Uri uri) {
        this.name = name;
        this.uri = uri;
        this.dateAdded = System.currentTimeMillis();
    }

    public DocumentItem(String name, String path, String mimeType, long size) {
        this.name = name;
        this.path = path;
        this.mimeType = mimeType;
        this.size = size;
        this.dateAdded = System.currentTimeMillis();
    }

    // Getters
    public String getName() {
        return name != null ? name : "Unknown Document";
    }

    // Added method to get filename (alias for getName for VectorStore compatibility)
    public String getFileName() {
        return getName();
    }

    public String getPath() {
        return path;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSize() {
        return size;
    }

    public long getDateAdded() {
        return dateAdded;
    }

    public Uri getUri() {
        return uri;
    }

    public String getContent() {
        return content;
    }

    // Setters
    public void setName(String name) {
        this.name = name;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setUri(Uri uri) {
        this.uri = uri;
    }

    public void setContent(String content) {
        this.content = content;
    }

    // Helper methods
    public String getFormattedSize() {
        if (size <= 0) return "Unknown size";

        String[] units = {"B", "KB", "MB", "GB"};
        int unitIndex = 0;
        double sizeInUnit = size;

        while (sizeInUnit >= 1024 && unitIndex < units.length - 1) {
            sizeInUnit /= 1024;
            unitIndex++;
        }

        return String.format("%.1f %s", sizeInUnit, units[unitIndex]);
    }

    public String getFileExtension() {
        if (name == null) return "";

        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0 && lastDot < name.length() - 1) {
            return name.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    // Added getFileType method for VectorStore compatibility
    public String getFileType() {
        // First try to get from file extension
        String extension = getFileExtension();
        if (!extension.isEmpty()) {
            return extension;
        }

        // Fallback to mime type mapping
        if (mimeType != null) {
            return mimeTypeToFileType(mimeType);
        }

        return "txt"; // Default fallback
    }

    private String mimeTypeToFileType(String mimeType) {
        switch (mimeType.toLowerCase()) {
            case "application/pdf":
                return "pdf";
            case "application/msword":
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                return "docx";
            case "text/html":
                return "html";
            case "application/xml":
            case "text/xml":
                return "xml";
            case "text/plain":
                return "txt";
            default:
                return "txt";
        }
    }

    @Override
    public String toString() {
        return "DocumentItem{" +
                "name='" + name + '\'' +
                ", path='" + path + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", size=" + size +
                '}';
    }
}