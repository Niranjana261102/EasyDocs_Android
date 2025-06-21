package com.easydocs.ai;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import androidx.viewpager2.widget.ViewPager2;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {
    private static final String TAG = "ChatActivity";

    // Chat components
    private RecyclerView chatRecyclerView;
    private EditText messageEditText;
    private Button sendButton;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> chatMessages;
    private AIEngine aiEngine;

    // Document components
    private RecyclerView documentsRecyclerView;
    private FloatingActionButton addDocumentFab;
    private DocumentsAdapter documentsAdapter;
    private List<DocumentItem> documentItems;
    private ActivityResultLauncher<Intent> documentPickerLauncher;

    // Tab components
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private ChatDocumentPagerAdapter pagerAdapter;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_chat, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            performLogout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void performLogout() {
        // FirebaseAuth.getInstance().signOut(); // Uncomment if using Firebase
        Intent intent = new Intent(ChatActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        Log.d(TAG, "ChatActivity onCreate started");

        initializeViews();
        setupTabLayout();
        setupActivityResultLauncher();

        aiEngine = new AIEngine(this);
        documentItems = new ArrayList<>();
        chatMessages = new ArrayList<>();

        Log.d(TAG, "ChatActivity created successfully");
    }

    private void initializeViews() {
        // Tab layout components
        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);

        // Chat components
        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        messageEditText = findViewById(R.id.messageEditText);
        sendButton = findViewById(R.id.sendButton);

        // Document components
        documentsRecyclerView = findViewById(R.id.documentsRecyclerView);
        addDocumentFab = findViewById(R.id.addDocumentFab);

        Log.d(TAG, "Views initialized");
    }

    private void setupTabLayout() {
        pagerAdapter = new ChatDocumentPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        // Setup tab titles
        new com.google.android.material.tabs.TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    switch (position) {
                        case 0:
                            tab.setText("Chat");
                            tab.setIcon(R.drawable.ic_ai_avatar);
                            break;
                        case 1:
                            tab.setText("Documents");
                            tab.setIcon(R.drawable.ic_document);
                            break;
                    }
                }).attach();
    }

    private void setupActivityResultLauncher() {
        documentPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        if (result.getData().getClipData() != null) {
                            // Multiple documents selected
                            int count = result.getData().getClipData().getItemCount();
                            for (int i = 0; i < count; i++) {
                                Uri uri = result.getData().getClipData().getItemAt(i).getUri();
                                handleSelectedDocument(uri);
                            }
                        } else if (result.getData().getData() != null) {
                            // Single document selected
                            Uri uri = result.getData().getData();
                            handleSelectedDocument(uri);
                        }
                    } else {
                        Log.d(TAG, "Document selection cancelled or failed");
                    }
                }
        );
    }

    // Chat Fragment
    public static class ChatFragment extends Fragment {
        private RecyclerView chatRecyclerView;
        private EditText messageEditText;
        private Button sendButton;
        private ChatAdapter chatAdapter;
        private List<ChatMessage> chatMessages;
        private AIEngine aiEngine;
        private ChatActivity parentActivity;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_chat, container, false);

            parentActivity = (ChatActivity) getActivity();
            chatMessages = new ArrayList<>();
            aiEngine = new AIEngine(getContext());

            initializeChatViews(view);
            setupChatRecyclerView();
            setupChatClickListeners();

            return view;
        }

        private void initializeChatViews(View view) {
            chatRecyclerView = view.findViewById(R.id.chatRecyclerView);
            messageEditText = view.findViewById(R.id.messageEditText);
            sendButton = view.findViewById(R.id.sendButton);
        }

        private void setupChatRecyclerView() {
            chatAdapter = new ChatAdapter(chatMessages);
            chatRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            chatRecyclerView.setAdapter(chatAdapter);
        }

        private void setupChatClickListeners() {
            sendButton.setOnClickListener(v -> sendMessage());

            messageEditText.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                    sendMessage();
                    return true;
                }
                return false;
            });
        }

        private void sendMessage() {
            String message = messageEditText.getText().toString().trim();
            if (message.isEmpty()) return;

            // Add user message
            chatMessages.add(new ChatMessage(message, true));
            chatAdapter.notifyItemInserted(chatMessages.size() - 1);
            chatRecyclerView.scrollToPosition(chatMessages.size() - 1);
            messageEditText.setText("");

            // Process with AI
            aiEngine.processQuery(message, new AIEngine.AICallback() {
                @Override
                public void onResponse(String response) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            chatMessages.add(new ChatMessage(response, false));
                            chatAdapter.notifyItemInserted(chatMessages.size() - 1);
                            chatRecyclerView.scrollToPosition(chatMessages.size() - 1);
                        });
                    }
                }

                @Override
                public void onError(String error) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            chatMessages.add(new ChatMessage("Sorry, I encountered an error: " + error, false));
                            chatAdapter.notifyItemInserted(chatMessages.size() - 1);
                            chatRecyclerView.scrollToPosition(chatMessages.size() - 1);
                        });
                    }
                }
            });
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (aiEngine != null) {
                aiEngine.shutdown();
            }
        }
    }

    // Documents Fragment
    public static class DocumentsFragment extends Fragment {
        private RecyclerView documentsRecyclerView;
        private FloatingActionButton addDocumentFab;
        private DocumentsAdapter documentsAdapter;
        private List<DocumentItem> documentItems;
        private ChatActivity parentActivity;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_documents, container, false);

            parentActivity = (ChatActivity) getActivity();
            documentItems = new ArrayList<>();

            initializeDocumentViews(view);
            setupDocumentsRecyclerView();
            setupDocumentClickListeners();
            loadExistingDocuments();

            return view;
        }

        private void initializeDocumentViews(View view) {
            documentsRecyclerView = view.findViewById(R.id.documentsRecyclerView);
            addDocumentFab = view.findViewById(R.id.addDocumentFab);
        }

        private void setupDocumentsRecyclerView() {
            documentsAdapter = new DocumentsAdapter(documentItems, position -> {
                // Remove document
                if (position >= 0 && position < documentItems.size()) {
                    DocumentItem removedDoc = documentItems.remove(position);
                    documentsAdapter.notifyItemRemoved(position);

                    // Remove from DocumentManager
                    DocumentManager.getInstance().removeDocument(position);

                    Toast.makeText(getContext(), "Document removed: " + removedDoc.getFileName(),
                            Toast.LENGTH_SHORT).show();
                }
            });

            documentsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            documentsRecyclerView.setAdapter(documentsAdapter);
        }

        private void setupDocumentClickListeners() {
            addDocumentFab.setOnClickListener(v -> {
                if (parentActivity != null) {
                    parentActivity.openDocumentPicker();
                }
            });
        }

        private void loadExistingDocuments() {
            documentItems.clear();
            documentItems.addAll(DocumentManager.getInstance().getDocuments());
            documentsAdapter.notifyDataSetChanged();
        }

        public void addDocument(DocumentItem document) {
            documentItems.add(document);
            documentsAdapter.notifyItemInserted(documentItems.size() - 1);
        }
    }

    // ViewPager Adapter
    private class ChatDocumentPagerAdapter extends FragmentStateAdapter {
        public ChatDocumentPagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new ChatFragment();
                case 1:
                    return new DocumentsFragment();
                default:
                    return new ChatFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    public void openDocumentPicker() {
        try {
            String[] mimeTypes = {
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "text/plain",
                    "image/*"
            };

            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            Log.d(TAG, "Launching document picker");
            documentPickerLauncher.launch(Intent.createChooser(intent, "Select Documents"));
        } catch (Exception e) {
            Log.e(TAG, "Error opening document picker: " + e.getMessage(), e);
            Toast.makeText(this, "Error opening document picker: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void handleSelectedDocument(Uri uri) {
        try {
            Log.d(TAG, "Handling selected document: " + uri.toString());
            DocumentProcessor processor = new DocumentProcessor(this);

            processor.processDocument(uri, new DocumentProcessor.ProcessingCallback() {
                @Override
                public void onSuccess(DocumentItem document) {
                    DocumentManager.getInstance().addDocument(document);

                    runOnUiThread(() -> {
                        // Update documents fragment if it exists
                        Fragment documentsFragment = getSupportFragmentManager()
                                .findFragmentByTag("f1"); // ViewPager2 uses "f" + position as tag
                        if (documentsFragment instanceof DocumentsFragment) {
                            ((DocumentsFragment) documentsFragment).addDocument(document);
                        }

                        Toast.makeText(ChatActivity.this,
                                "Document added: " + document.getFileName(), Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() ->
                            Toast.makeText(ChatActivity.this,
                                    "Error processing document: " + error, Toast.LENGTH_LONG).show()
                    );
                }

                @Override
                public void onProgress(int progress) {
                    // Optional: show progress bar
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error handling selected document: " + e.getMessage(), e);
            Toast.makeText(this, "Error processing document: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (aiEngine != null) {
            aiEngine.shutdown();
        }
        Log.d(TAG, "ChatActivity destroyed");
    }
}