package com.easydocs.ai;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DocumentsAdapter extends RecyclerView.Adapter<DocumentsAdapter.DocumentViewHolder> {

    private List<DocumentItem> documents;
    private OnDocumentRemoveListener removeListener;

    public interface OnDocumentRemoveListener {
        void onRemove(int position);
    }

    public DocumentsAdapter(List<DocumentItem> documents, OnDocumentRemoveListener removeListener) {
        this.documents = documents;
        this.removeListener = removeListener;
    }

    @NonNull
    @Override
    public DocumentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_document, parent, false);
        return new DocumentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DocumentViewHolder holder, int position) {
        DocumentItem document = documents.get(position);
        holder.bind(document, position);
    }

    @Override
    public int getItemCount() {
        return documents != null ? documents.size() : 0;
    }

    public void updateDocuments(List<DocumentItem> newDocuments) {
        this.documents = newDocuments;
        notifyDataSetChanged();
    }

    class DocumentViewHolder extends RecyclerView.ViewHolder {
        private TextView documentName;
        private TextView documentInfo;
        private TextView documentDate;
        private Button removeButton;

        public DocumentViewHolder(@NonNull View itemView) {
            super(itemView);
            documentName = itemView.findViewById(R.id.documentName);
            documentInfo = itemView.findViewById(R.id.documentInfo);
            documentDate = itemView.findViewById(R.id.documentDate);
            removeButton = itemView.findViewById(R.id.removeButton);
        }

        public void bind(DocumentItem document, int position) {
            // Set document name
            if (documentName != null) {
                documentName.setText(document.getName());
            }

            // Set document info (size and type)
            if (documentInfo != null) {
                String info = document.getFormattedSize();
                if (!document.getFileExtension().isEmpty()) {
                    info += " • " + document.getFileExtension().toUpperCase();
                }
                documentInfo.setText(info);
            }

            // Set document date
            if (documentDate != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                String dateText = sdf.format(new Date(document.getDateAdded()));
                documentDate.setText(dateText);
            }

            // Set remove button listener
            if (removeButton != null && removeListener != null) {
                removeButton.setOnClickListener(v -> removeListener.onRemove(position));
            }
        }
    }
}