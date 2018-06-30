package com.example.wh433.dsdl_wallet_ble;

import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // setting hints
        final TextInputLayout passwordWrapper = findViewById(R.id.login_wrapper);
        passwordWrapper.setHint("Password");

        // login button
        Button button = findViewById(R.id.login_button);
        button.setOnClickListener(v -> {
            v.setEnabled(false);
            login();
            v.setEnabled(true);
        });
    }

    private void login() {
        EditText loginInput = findViewById(R.id.login_input);
        String input = loginInput.getText().toString();
        if (input.isEmpty()) {
            loginInput.setError("Please enter password");
            return;
        }
        else if (!PasswordActivity.getPasswordHashString(getApplicationContext())
                .equals(PasswordActivity.hashSHA256(input))) {
            loginInput.setError("Invalid password");
            return;
        }
        loginInput.setError(null);
        finish();
    }
}
