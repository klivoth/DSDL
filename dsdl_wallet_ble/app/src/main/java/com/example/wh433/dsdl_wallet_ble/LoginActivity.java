package com.example.wh433.dsdl_wallet_ble;

import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // setting hints
        final TextInputLayout privateKeyWrapper = findViewById(R.id.private_key_wrapper);
        privateKeyWrapper.setHint("Password");

        // login button
        Button button = findViewById(R.id.login_button);
        button.setOnClickListener(v -> login());
    }

    private void login() {
        finish();
    }
}
