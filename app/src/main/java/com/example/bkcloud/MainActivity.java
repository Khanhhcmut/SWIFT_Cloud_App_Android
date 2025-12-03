package com.example.bkcloud;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;


public class MainActivity extends AppCompatActivity {

    //khai báo biến
    EditText edtuser,edtpass,edtproj;
    Button btnlogin,btnhelp;
    RadioGroup radioGroupHelp;
    RadioButton radio1,radio2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        //ánh xạ id
        edtuser = findViewById(R.id.edtuser);
        edtpass = findViewById(R.id.edtpass);
        edtproj = findViewById(R.id.editproj);

        btnlogin = findViewById(R.id.btnlogin);
        btnhelp = findViewById(R.id.btnhelp);

        radioGroupHelp = findViewById(R.id.radioGroupHelp);
        radio1 = findViewById(R.id.radio1);
        radio2 = findViewById(R.id.radio2);

        btnhelp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (radioGroupHelp.getVisibility() == View.GONE) {
                    radioGroupHelp.setVisibility(View.VISIBLE);
                } else {
                    radioGroupHelp.setVisibility(View.GONE);
                }
            }
        });

        radioGroupHelp.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.radio1) {
                    Intent help = new Intent(MainActivity.this, HelpActivity.class);
                    startActivity(help);

                    radioGroupHelp.clearCheck();
                    radioGroupHelp.setVisibility(View.GONE);
                }
                else if (checkedId == R.id.radio2) {
                    showSwiftURLDialog();

                    radioGroupHelp.clearCheck();
                    radioGroupHelp.setVisibility(View.GONE);
                }
            }
        });

        //login
        btnlogin.setOnClickListener(v -> {
            String username = edtuser.getText().toString().trim();
            String password = edtpass.getText().toString().trim();
            String project  = edtproj.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty() || project.isEmpty()) {
                Toast.makeText(MainActivity.this, "Please enter Username, Password and Project name", Toast.LENGTH_SHORT).show();
                return;
            }

            LoginHelper.login(MainActivity.this, username, password, project, new LoginHelper.LoginResult() {
                @Override
                public void onSuccess(String token, String storageUrl, String userId, String projectId) {

                    // giống code bạn có sẵn
                    HomeActivity.token = token;
                    HomeActivity.storageUrl = storageUrl;
                    HomeActivity.username = username;
                    HomeActivity.project = project;
                    HomeActivity.userId = userId;

                    UserManager.saveUser(
                            MainActivity.this,
                            new UserItem(username, project, token, storageUrl, password)
                    );

                    Intent intent = new Intent(MainActivity.this, HomeActivity.class);
                    startActivity(intent);
                    finish();
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });

        });


    }

    private void showSwiftURLDialog() {
        // Lấy giá trị đã lưu từ SharedPreferences
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String savedURL = prefs.getString("swift_url", "http://");

        // Tạo dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_swift_url, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        EditText edtSwiftURL = dialogView.findViewById(R.id.edtSwiftURL);
        Button btnSave = dialogView.findViewById(R.id.btnSave);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);

        edtSwiftURL.setText(savedURL);
        edtSwiftURL.setSelection(savedURL.length());

        if (!savedURL.isEmpty()) {
            edtSwiftURL.setText(savedURL);
        }

        btnSave.setOnClickListener(v -> {
            String newURL = edtSwiftURL.getText().toString().trim();
            if (!newURL.isEmpty()) {
                // Lưu vào SharedPreferences
                prefs.edit().putString("swift_url", newURL).apply();
                Toast.makeText(MainActivity.this, "Swift Auth URL Saved: " + newURL, Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
            radioGroupHelp.clearCheck();
            radioGroupHelp.setVisibility(View.VISIBLE); // quay lại RadioGroup
        });

        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
            radioGroupHelp.clearCheck();
            radioGroupHelp.setVisibility(View.VISIBLE); // quay lại RadioGroup
        });

        dialog.show();
    }
    public String getSavedSwiftURL() {
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        return prefs.getString("swift_url", "");
    }


}