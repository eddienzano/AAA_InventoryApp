package com.yourapp;

import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.yourapp.boxfill.FlowerDbHelper;

import org.json.JSONArray;
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
    private FlowerDbHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        usernameInput = findViewById(R.id.username);
        passwordInput = findViewById(R.id.password);
        loginBtn = findViewById(R.id.loginBtn);

        apiClient = ApiClient.getInstance();
        dbHelper = new FlowerDbHelper(this);

        loginBtn.setOnClickListener(v -> loginUser());

        MaterialCardView priorityButton = findViewById(R.id.priorityButton);
        priorityButton.setOnClickListener(v -> {
            Intent i = new Intent(LoginActivity.this, PriorityActivity.class);
            startActivity(i);
        });

        MaterialCardView rejectionReportButton = findViewById(R.id.rejectionReportButton);
        rejectionReportButton.setOnClickListener(v -> {
            Intent i = new Intent(LoginActivity.this, RejectionReportsActivity.class);
            startActivity(i);
        });

        MaterialCardView priorityConfirmationButton = findViewById(R.id.priorityConfirmationButton);
        priorityConfirmationButton.setOnClickListener(v -> {
            Intent i = new Intent(LoginActivity.this, PriorityConfirmationActivity.class);
            startActivity(i);
        });
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
                        Toast.makeText(LoginActivity.this,
                                "Network Error: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
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

                            Toast.makeText(LoginActivity.this,
                                    "Login Success. Syncing data...",
                                    Toast.LENGTH_SHORT).show();

                            syncFarmsAndVarieties(() -> {
                                Intent intent = new Intent(LoginActivity.this, MainDashboardActivity.class);
                                intent.putExtra("username", json.optString("username", ""));
                                intent.putExtra("user_id", json.optInt("user_id", -1));
                                startActivity(intent);
                                finish();
                            });

                        } else {
                            Toast.makeText(LoginActivity.this,
                                    "Login Failed: " + message,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });

                } catch (IOException | JSONException e) {
                    Log.e(TAG, "Response parsing error", e);
                    runOnUiThread(() ->
                            Toast.makeText(LoginActivity.this,
                                    "Invalid server response",
                                    Toast.LENGTH_SHORT).show()
                    );
                } finally {
                    response.close();
                }
            }
        });
    }

    // =====================================================
    // MASTER DATA SYNC (LOGIN TIME)
    // =====================================================
    private void syncFarmsAndVarieties(Runnable onDone) {

        Request farmsReq = new Request.Builder()
                .url(BASE_URL + "get_farms.php")
                .get()
                .build();

        apiClient.getClient().newCall(farmsReq).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Farm sync failed", e);
                runOnUiThread(onDone);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    JSONArray farms = new JSONArray(response.body().string());
                    SQLiteDatabase db = dbHelper.getWritableDatabase();
                    db.beginTransaction();

                    for (int i = 0; i < farms.length(); i++) {
                        JSONObject f = farms.getJSONObject(i);
                        db.execSQL(
                                "INSERT OR REPLACE INTO farms_local (id,name) VALUES (?,?)",
                                new Object[]{f.getInt("id"), f.getString("name")}
                        );
                    }
                    db.setTransactionSuccessful();
                    db.endTransaction();

                } catch (Exception e) {
                    Log.e(TAG, "Farm sync parse error", e);
                } finally {
                    response.close();
                    syncVarieties(onDone);
                }
            }
        });
    }

    private void syncVarieties(Runnable onDone) {

        Request req = new Request.Builder()
                .url(BASE_URL + "fetch_varieties.php")
                .get()
                .build();

        apiClient.getClient().newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Variety sync failed", e);
                runOnUiThread(onDone);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    JSONArray arr = new JSONArray(response.body().string());
                    SQLiteDatabase db = dbHelper.getWritableDatabase();
                    db.beginTransaction();

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject v = arr.getJSONObject(i);

                        int varietyId = v.getInt("VarietyId");
                        int farmId    = v.getInt("FarmId");
                        String name   = v.getString("VarietyName");

                        db.execSQL(
                                "INSERT OR REPLACE INTO varieties_local (id,farm_id,name) VALUES (?,?,?)",
                                new Object[]{ varietyId, farmId, name }
                        );
                    }

                    db.setTransactionSuccessful();
                    db.endTransaction();

                } catch (Exception e) {
                    Log.e(TAG, "Variety sync parse error", e);
                } finally {
                    response.close();
                    runOnUiThread(onDone);
                }
            }
        });
    }

}
