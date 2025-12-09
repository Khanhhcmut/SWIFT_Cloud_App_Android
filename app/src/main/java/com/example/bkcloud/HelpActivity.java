package com.example.bkcloud;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class HelpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean openManual = getIntent().getBooleanExtra("open_manual", false);

        if (openManual) {
            setContentView(R.layout.activity_help);

            findViewById(R.id.btnExitManual).setOnClickListener(v -> finish());
        }
        else {
            setContentView(R.layout.help_menu);

            findViewById(R.id.btnHelp1).setOnClickListener(v -> {
                getIntent().putExtra("open_manual", true);
                recreate();
            });

            findViewById(R.id.btnHelp2).setOnClickListener(v -> {});
            findViewById(R.id.btnHelp3).setOnClickListener(v -> {});
            findViewById(R.id.btnExitHelp).setOnClickListener(v -> finish());
        }
    }
}
