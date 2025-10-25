package com.yourapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LoginActivity extends AppCompatActivity {
    EditText usernameInput, passwordInput;
    Button loginBtn;

    private static final String TAG = "LoginActivity";
    private static final String BASE_URL = "https://www.aaagrowers.co.ke/inventory/";

    private ApiClient apiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        usernameInput = findViewById(R.id.username);
        passwordInput = findViewById(R.id.password);
        loginBtn = findViewById(R.id.loginBtn);

        apiClient = ApiClient.getInstance();

        loginBtn.setOnClickListener(v -> loginUser());
    }

    private void loginUser() {
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter username and password", Toast.LENGTH_SHORT).show();
            return;
        }

        RequestBody formBody = new FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "login.php")
                .post(formBody)
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .build();

        apiClient.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Network error", e);
                runOnUiThread(() ->
                        Toast.makeText(LoginActivity.this, "Network Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        throw new IOException("Unexpected response: " + response);
                    }

                    String resp = response.body().string();
                    JSONObject json = new JSONObject(resp);

                    String status = json.optString("status", "error");
                    String message = json.optString("message", "Unknown error");

                    runOnUiThread(() -> {
                        if ("success".equalsIgnoreCase(status)) {
                            String uname = json.optString("username", "");
                            int userId = json.optInt("user_id", -1);

                            Toast.makeText(LoginActivity.this, "Login Success", Toast.LENGTH_SHORT).show();

                            Intent intent = new Intent(LoginActivity.this, MainDashboardActivity.class);
                            intent.putExtra("username", uname);
                            intent.putExtra("user_id", userId);
                            startActivity(intent);
                            finish();
                        } else {
                            Toast.makeText(LoginActivity.this, "Login Failed: " + message, Toast.LENGTH_SHORT).show();
                        }
                    });

                } catch (IOException | JSONException e) {
                    Log.e(TAG, "Response parsing error", e);
                    runOnUiThread(() ->
                            Toast.makeText(LoginActivity.this, "Invalid server response", Toast.LENGTH_SHORT).show()
                    );
                } finally {
                    response.close();
                }
            }
        });
    }
}
