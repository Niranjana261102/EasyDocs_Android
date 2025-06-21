package com.easydocs.ai;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";

    private EditText usernameEditText;
    private EditText passwordEditText;
    private Button loginButton;

    private TextView registerLink; // Declare at top with other views


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initializeViews();
        setupClickListeners();
    }

    private void initializeViews() {
        usernameEditText = findViewById(R.id.usernameEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        registerLink = findViewById(R.id.registerLink);

    }

    private void setupClickListeners() {
        loginButton.setOnClickListener(v -> performLogin());

        // Allow login on Enter key press
        passwordEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                performLogin();
                return true;
            }
            return false;
        });

        registerLink.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });


    }


    private void performLogin() {
        String username = usernameEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        // Simple validation
        if (username.isEmpty()) {
            usernameEditText.setError("Username is required");
            usernameEditText.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            passwordEditText.setError("Password is required");
            passwordEditText.requestFocus();
            return;
        }

        // Simple authentication (replace with your actual authentication logic)
        if (isValidCredentials(username, password)) {
            // Login successful - redirect to ChatActivity
            Intent intent = new Intent(LoginActivity.this, ChatActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else {
            Toast.makeText(this, "Invalid username or password", Toast.LENGTH_SHORT).show();
            passwordEditText.setText("");
            passwordEditText.requestFocus();
        }
    }

    private boolean isValidCredentials(String username, String password) {
        // Replace this with your actual authentication logic
        // For demo purposes, accepting any non-empty credentials
        // You can add specific username/password combinations here

        // Example: Accept specific credentials
        if ("admin".equals(username) && "password".equals(password)) {
            return true;
        }

        // Or accept any non-empty credentials for testing
        return !username.isEmpty() && !password.isEmpty() && password.length() >= 4;
    }
}