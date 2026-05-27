package com.yourapp;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.*;

import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;

public class ScanToDampActivity extends AppCompatActivity {

    private boolean isProcessing = false;

    private EditText qrInput;
    private Button scanBtn;
    private TableLayout tableLayout;

    private Button btnCaptureDump;
    private ImageView dumpPreview;

    private boolean dumpUploaded = false;
    private Uri imageUri;

    private int dumpSessionId = 0;


    private static final String BASE_URL = "https://www.aaagrowers.co.ke/inventory/";

    private ApiClient apiClient;
    private int userId; // ✅ Add this

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_to_damp);

        qrInput = findViewById(R.id.qr_code_out);
        scanBtn = findViewById(R.id.scanBtnOut);
        tableLayout = findViewById(R.id.scanoutTable);

        btnCaptureDump = findViewById(R.id.btnCaptureDump);
        dumpPreview = findViewById(R.id.dumpPreview);

        btnCaptureDump.setOnClickListener(v -> openCamera());

        setScanningEnabled(false);

        apiClient = ApiClient.getInstance();

        // ✅ Get user_id from intent (passed from LoginActivity)
        Intent intent = getIntent();
        userId = intent.getIntExtra("user_id", -1);

        // Camera scanner button
        scanBtn.setOnClickListener(v -> startQRScanner());

        // ✅ Honeywell EDA51 keyboard wedge support
        addHoneywellTextWatcher();

        // Optional: editor action + focus change
        qrInput.setOnEditorActionListener((v, actionId, event) -> {
            String scanned = qrInput.getText().toString().trim();
            if (!scanned.isEmpty()) {
                qrInput.setText("");
                processScan(scanned);
            }
            return true;
        });

        qrInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String scanned = qrInput.getText().toString().trim();
                if (!scanned.isEmpty()) {
                    qrInput.setText("");
                    processScan(scanned);
                }
            }
        });

        loadLastScans();
    }

    private void openCamera() {

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "DumpSheet");
        values.put(MediaStore.Images.Media.DESCRIPTION, "Dump Sheet");

        imageUri = getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
        );

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);

        cameraLauncher.launch(intent);
    }

    private void setScanningEnabled(boolean enabled) {
        qrInput.setEnabled(enabled);
        scanBtn.setEnabled(enabled);

        qrInput.setAlpha(enabled ? 1f : 0.5f);
        scanBtn.setAlpha(enabled ? 1f : 0.5f);
    }

    private final androidx.activity.result.ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && imageUri != null) {

                            dumpPreview.setImageURI(imageUri);

                            uploadDumpSheet(imageUri);
                        }
                    });

    private void addHoneywellTextWatcher() {
        final Handler handler = new Handler();
        final Runnable processScanRunnable = () -> {
            String code = qrInput.getText().toString().trim();
            if (!code.isEmpty()) {
                processScan(code);
                qrInput.setText("");
            }
        };

        qrInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                handler.removeCallbacks(processScanRunnable);
                if (s.length() > 6) {
                    handler.postDelayed(processScanRunnable, 300);
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void uploadDumpSheet(Uri uri) {

        try {

            android.graphics.Bitmap bitmap =
                    MediaStore.Images.Media.getBitmap(
                            getContentResolver(),
                            uri
                    );

            java.io.ByteArrayOutputStream baos =
                    new java.io.ByteArrayOutputStream();

            bitmap.compress(
                    android.graphics.Bitmap.CompressFormat.JPEG,
                    60,
                    baos
            );

            byte[] imageBytes = baos.toByteArray();

            RequestBody fileBody =
                    RequestBody.create(
                            imageBytes,
                            MediaType.parse("image/jpeg")
                    );

            MultipartBody requestBody =
                    new MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart(
                                    "dump_image",
                                    "dump.jpg",
                                    fileBody
                            )
                            .addFormDataPart(
                                    "user_id",
                                    String.valueOf(userId)
                            )
                            .build();

            Request request = new Request.Builder()
                    .url(BASE_URL + "api/upload_dump_sheet.php")
                    .post(requestBody)
                    .build();

            apiClient.getClient().newCall(request).enqueue(new Callback() {

                @Override
                public void onFailure(
                        @NonNull Call call,
                        @NonNull IOException e
                ) {

                    runOnUiThread(() -> Toast.makeText(
                            ScanToDampActivity.this,
                            "Upload failed",
                            Toast.LENGTH_LONG
                    ).show());
                }

                @Override
                public void onResponse(
                        @NonNull Call call,
                        @NonNull Response response
                ) throws IOException {

                    String resp = response.body().string();

                    runOnUiThread(() -> {

                        try {

                            JSONObject json =
                                    new JSONObject(resp);

                            if (json.getString("status")
                                    .equals("success")) {

                                dumpSessionId =
                                        json.getInt("session_id");

                                dumpUploaded = true;

                                setScanningEnabled(true);

                                Toast.makeText(
                                        ScanToDampActivity.this,
                                        "Dump Session Started",
                                        Toast.LENGTH_LONG
                                ).show();

                            } else {

                                Toast.makeText(
                                        ScanToDampActivity.this,
                                        json.getString("message"),
                                        Toast.LENGTH_LONG
                                ).show();
                            }

                        } catch (Exception e) {

                            Toast.makeText(
                                    ScanToDampActivity.this,
                                    "Invalid server response",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    });
                }
            });

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Image error: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private final androidx.activity.result.ActivityResultLauncher<ScanOptions> barcodeLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    String scanned = result.getContents();
                    qrInput.setText("");
                    processScan(scanned);
                }
            });

    private void startQRScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan QR Code");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        barcodeLauncher.launch(options);
    }

    private void processScan(String qrCode) {

        if (qrCode.isEmpty()) return;

        // 🚫 prevent multiple requests
        if (isProcessing) return;
        isProcessing = true;

        RequestBody body = new FormBody.Builder()
                .add("qr_code", qrCode)
                .add("user_id", String.valueOf(userId))
                .add("session_id", String.valueOf(dumpSessionId))
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "api/scanout_dump_process.php")
                .post(body)
                .build();

        apiClient.getClient().newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(ScanToDampActivity.this,
                            "Network error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();

                    isProcessing = false; // ✅ unlock
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {

                String resp = response.body().string();

                runOnUiThread(() -> {
                    try {
                        JSONObject json = new JSONObject(resp);
                        String message = json.optString("message");

                        Toast.makeText(ScanToDampActivity.this,
                                message,
                                Toast.LENGTH_LONG).show();

                    } catch (JSONException e) {
                        Toast.makeText(ScanToDampActivity.this,
                                "Invalid response",
                                Toast.LENGTH_SHORT).show();
                    }

                    qrInput.requestFocus();
                    isProcessing = false; // ✅ unlock after server reply
                });
            }
        });
    }


    private void loadLastScans() {
        Request request = new Request.Builder()
                .url(BASE_URL + "scanout_last10.php")
                .build();

        apiClient.getClient().newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String html = response.body().string();
                runOnUiThread(() -> {
                    tableLayout.removeAllViews();
                    // TODO: parse HTML or use WebView if needed
                });
            }
        });
    }
}
