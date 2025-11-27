package com.example.bkcloud;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.ArrayList;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class HomeActivity extends AppCompatActivity {

    DrawerLayout drawerLayout;
    NavigationView navigationView;
    ActionBarDrawerToggle toggle;
    RecyclerView recyclerFolders, recyclerFiles;
    FolderAdapter folderAdapter;
    FileAdapter fileAdapter;
    TextView txtCurrentUser;
    Spinner userSpinner;
    boolean isSpinnerInitialized = false;

    public static String token = "";
    public static String storageUrl = "";
    public static String username = "";
    public static String project = "";
    public static String userId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navView);

        View headerView = getLayoutInflater().inflate(R.layout.nav_header_user, navigationView, false);
        navigationView.addHeaderView(headerView);
        txtCurrentUser = headerView.findViewById(R.id.txtCurrentUser);


        Spinner userSpinner = headerView.findViewById(R.id.userSpinner);
        txtCurrentUser.setText("User: " + username + "\nProject: " + project);

        List<UserItem> userList = UserManager.loadUsers(this);

        List<String> names = new ArrayList<>();
        for (UserItem u : userList) {
            names.add(u.username + " (" + u.project + ")");
        }

        ArrayAdapter<String> spinnerAdapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);

        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        userSpinner.setAdapter(spinnerAdapter);

        int currentIndex = 0;
        for (int i = 0; i < userList.size(); i++) {
            if (userList.get(i).username.equals(username) &&
                    userList.get(i).project.equals(project)) {
                currentIndex = i;
                break;
            }
        }

        isSpinnerInitialized = false;
        userSpinner.setSelection(currentIndex);

        userSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {

                if (!isSpinnerInitialized) {
                    isSpinnerInitialized = true;
                    return;
                }

                UserItem selectedUser = userList.get(pos);

                if (selectedUser.username.equals(username) &&
                        selectedUser.project.equals(project)) {
                    return;
                }

                confirmSwitchUser(selectedUser);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_logout) {
                confirmLogout();
            }

            drawerLayout.closeDrawers();
            return true;
        });

        recyclerFolders = findViewById(R.id.recyclerFolders);
        recyclerFiles = findViewById(R.id.recyclerFiles);

        recyclerFolders.setLayoutManager(new LinearLayoutManager(this));
        recyclerFiles.setLayoutManager(new LinearLayoutManager(this));

        loadFolders();

        toggle = new ActionBarDrawerToggle(
                this,
                drawerLayout,
                toolbar,
                R.string.open,
                R.string.close
        );

        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (toggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    private void loadFolders() {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();

                // storageUrl = http://ip:port/v1/AUTH_xxx
                Request request = new Request.Builder()
                        .url(storageUrl) // this lists containers
                        .addHeader("X-Auth-Token", token)
                        .get()
                        .build();

                Response response = client.newCall(request).execute();

                if (response.isSuccessful()) {
                    String body = response.body().string();
                    String[] lines = body.split("\n");

                    List<String> folders = new ArrayList<>();
                    for (String line : lines) {
                        if (!line.trim().isEmpty()) {
                            folders.add(line.trim());
                        }
                    }

                    runOnUiThread(() -> {
                        folderAdapter = new FolderAdapter(folders, folderName -> {
                            loadFiles(folderName);
                        });
                        recyclerFolders.setAdapter(folderAdapter);
                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void loadFiles(String containerName) {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();

                String url = storageUrl + "/" + containerName + "?format=json";

                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("X-Auth-Token", token)
                        .get()
                        .build();

                Response response = client.newCall(request).execute();

                if (response.isSuccessful()) {
                    String json = response.body().string();

                    JSONArray arr = new JSONArray(json);
                    List<FileAdapter.FileItem> files = new ArrayList<>();

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String name = obj.getString("name");
                        long size = obj.getLong("bytes");

                        files.add(new FileAdapter.FileItem(name, size));
                    }

                    runOnUiThread(() -> {
                        fileAdapter = new FileAdapter(files);
                        recyclerFiles.setAdapter(fileAdapter);
                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void doLogout() {
        token = "";
        storageUrl = "";
        username = "";
        project = "";
        userId = "";

        Intent intent = new Intent(HomeActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void confirmLogout() {

        View view = getLayoutInflater().inflate(R.layout.dialog_logout, null);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnYes = view.findViewById(R.id.btnYes);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnYes.setOnClickListener(v -> {
            doLogout();
            dialog.dismiss();
        });

        dialog.show();
    }


    private void confirmSwitchUser(UserItem user) {
        EditText edt = new EditText(this);
        edt.setHint("Enter password");

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Switch User")
                .setMessage("Enter password for user: " + user.username)
                .setView(edt)
                .setPositiveButton("OK", (dialog, which) -> {
                    String pass = edt.getText().toString().trim();
                    validateUserPassword(user, pass);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void validateUserPassword(UserItem user, String password) {

        LoginHelper.login(HomeActivity.this, user.username, password, user.project, new LoginHelper.LoginResult() {
            @Override
            public void onSuccess(String newToken, String newStorageUrl, String userId, String projectId) {

                runOnUiThread(() -> {
                    token = newToken;
                    storageUrl = newStorageUrl;
                    username = user.username;
                    project = user.project;

                    txtCurrentUser.setText("User: " + username + "\nProject: " + project);

                    loadFolders();

                    Toast.makeText(HomeActivity.this, "Switched to " + username, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() ->
                        Toast.makeText(HomeActivity.this, message, Toast.LENGTH_SHORT).show()
                );
            }
        });

    }


}

