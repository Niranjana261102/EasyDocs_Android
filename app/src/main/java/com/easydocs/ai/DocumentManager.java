package com.easydocs.ai;

import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public class DocumentManager {
    private static final String TAG = "DocumentManager";
    private static DocumentManager instance;
    private List<DocumentItem> documents;
    private VectorStore vectorStore;

    private DocumentManager() {
        documents = new ArrayList<>();
        vectorStore = new VectorStore();
        Log.d(TAG, "DocumentManager initialized");
    }

    public static synchronized DocumentManager getInstance() {
        if (instance == null) {
            instance = new DocumentManager();
        }
        return instance;
    }

    public List<DocumentItem> getDocuments() {
        return new ArrayList<>(documents); // Return copy to prevent external modification
    }

    public void addDocument(DocumentItem document) {
        if (document != null) {
            documents.add(document);
            // Add document to vector store for search functionality
            vectorStore.addDocument(document);
            Log.d(TAG, "Document added: " + document.getName() + ". Total documents: " + documents.size());
        } else {
            Log.w(TAG, "Attempted to add null document");
        }
    }

    public void removeDocument(int position) {
        if (position >= 0 && position < documents.size()) {
            DocumentItem removed = documents.remove(position);
            // Rebuild vector store after removal
            rebuildVectorStore();
            Log.d(TAG, "Document removed: " + removed.getName() + ". Remaining documents: " + documents.size());
        } else {
            Log.w(TAG, "Invalid position for document removal: " + position);
        }
    }

    public DocumentItem getDocument(int position) {
        if (position >= 0 && position < documents.size()) {
            return documents.get(position);
        }
        return null;
    }

    public int getDocumentCount() {
        return documents.size();
    }

    public boolean isEmpty() {
        return documents.isEmpty();
    }

    // Added method to check if documents exist
    public boolean hasDocuments() {
        return !documents.isEmpty();
    }

    // Added method to get vector store
    public VectorStore getVectorStore() {
        return vectorStore;
    }

    public void clearAllDocuments() {
        int count = documents.size();
        documents.clear();
        vectorStore.clearChunks();
        Log.d(TAG, "All documents cleared. Removed " + count + " documents");
    }

    public DocumentItem findDocumentByName(String name) {
        if (name == null) return null;

        for (DocumentItem doc : documents) {
            if (name.equals(doc.getName())) {
                return doc;
            }
        }
        return null;
    }

    // Helper method to rebuild vector store after document removal
    private void rebuildVectorStore() {
        vectorStore.clearChunks();
        for (DocumentItem document : documents) {
            vectorStore.addDocument(document);
        }
    }
}