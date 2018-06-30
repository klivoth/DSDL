package com.example.wh433.dsdl_wallet_ble;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class PasswordActivity extends AppCompatActivity {
    public static final int SET_PASSWORD = 0;
    public static final int CHANGE_PASSWORD = 1;
    public static final int REMOVE_PASSWORD = 2;
    private int mode = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password);

        Bundle b = getIntent().getExtras();
        if (b != null)
            mode = b.getInt("key");

        final TextInputLayout newPasswordWrapper = findViewById(R.id.new_password_wrapper);
        newPasswordWrapper.setHint("New Password");
        final TextInputLayout curPasswordWrapper = findViewById(R.id.cur_password_wrapper);
        curPasswordWrapper.setHint("Current Password");
        final TextInputLayout confirmPasswordWrapper = findViewById(R.id.confirm_password_wrapper);
        confirmPasswordWrapper.setHint("Confirm Password");
        Button button = findViewById(R.id.mod_password_button);
        LinearLayout linearLayout = findViewById(R.id.linear_layout_password);
        setSupportActionBar(findViewById(R.id.toolbar_password));
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        switch (mode) {
            case SET_PASSWORD:
                linearLayout.removeViewAt(0);
                actionBar.setTitle("Set Password");
                button.setText("Set Password");
                break;
            case REMOVE_PASSWORD:
                linearLayout.removeViews(1, 2);
                actionBar.setTitle("Remove Password");
                button.setText("Remove Password");
                break;
            case CHANGE_PASSWORD: default:
                actionBar.setTitle("Change Password");
        }

        button.setOnClickListener(v -> {
            v.setEnabled(false);
            if (checkInput())
                updatePassword();
            v.setEnabled(true);
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        menu.getItem(0).setVisible(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                this.finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public static boolean checkPasswordExist(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).contains("password_hash");
    }

    private boolean checkInput () {
        if (mode != SET_PASSWORD) {
            String passwordHash = getPasswordHashString(getApplicationContext());
            if (passwordHash.equals("no_password"))
                mode = SET_PASSWORD;
            else {
                if (mode == REMOVE_PASSWORD)
                    return true;
                EditText curPasswordInput = findViewById(R.id.cur_password_input);
                String password = curPasswordInput.getText().toString();
                if (password.isEmpty()) {
                    curPasswordInput.setError("Please enter password");
                    return false;
                }
                else if (!hashSHA256(password).equals(passwordHash)) {
                    curPasswordInput.setError("Wrong Password");
                    return false;
                }
                else
                    curPasswordInput.setError(null);
            }
        }
        EditText newPasswordInput = findViewById(R.id.new_password_input);
        EditText confirmPasswordInput = findViewById(R.id.confirm_password_input);
        String newPassword = newPasswordInput.getText().toString();
        if (newPassword.isEmpty()) {
            newPasswordInput.setError("Please enter new password");
            return false;
        }
        else if (!newPassword.equals(confirmPasswordInput.getText().toString())) {
            confirmPasswordInput.setError("Does not match with new password");
            return false;
        }
        newPasswordInput.setError(null);
        return true;
    }

    private void updatePassword() {
        String updateStatus = "Successfully updated password";
        SharedPreferences.Editor editor = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext()).edit();
        if (mode == REMOVE_PASSWORD) {
            editor.remove("password_hash");
            editor.apply();
            updateStatus = "Successfully removed password";
        }
        else {
            EditText newPasswordInput = findViewById(R.id.new_password_input);
            String hashString = hashSHA256(newPasswordInput.getText().toString());
            if (!hashString.equals("")) {
                editor.putString("password_hash", hashString);
                editor.apply();
            } else
                updateStatus = "Failed to update password";
        }
        Toast.makeText(getApplicationContext(), updateStatus,
                Toast.LENGTH_LONG).show();
        finish();
    }

    public static String hashSHA256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytesHash = md.digest(content.getBytes("UTF-8"));
            StringBuilder builder = new StringBuilder();
            for (byte aBytesHash : bytesHash)
                builder.append(Integer.toString((aBytesHash & 0xff) + 0x100, 16).substring(1));
            return builder.toString();
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            return "";
        }
    }

    public static String getPasswordHashString(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString("password_hash", "no_password");
    }
}
