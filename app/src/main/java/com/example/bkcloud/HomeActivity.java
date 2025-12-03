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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;


import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

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
    EditText edtSearchAll;

    boolean isSpinnerInitialized = false;
    int lastValidSelection = 0;

    public static String token = "";
    public static String storageUrl = "";
    public static String username = "";
    public static String project = "";
    public static String userId = "";
    List<FileAdapter.FileItem> allFiles = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        edtSearchAll = findViewById(R.id.edtSearchAll);

        edtSearchAll.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {

                int drawableRight = 2;

                if (edtSearchAll.getCompoundDrawables()[drawableRight] != null) {

                    int iconStart =
                            edtSearchAll.getWidth()
                                    - edtSearchAll.getPaddingEnd()
                                    - edtSearchAll.getCompoundDrawables()[drawableRight].getIntrinsicWidth();

                    if (event.getX() >= iconStart) {

                        edtSearchAll.setText("");

                        v.performClick();
                        return true;
                    }
                }
            }

            v.performClick();
            return false;
        });


        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navView);

        View headerView = getLayoutInflater().inflate(R.layout.nav_header_user, navigationView, false);
        navigationView.addHeaderView(headerView);
        txtCurrentUser = headerView.findViewById(R.id.txtCurrentUser);


        userSpinner = headerView.findViewById(R.id.userSpinner);
        txtCurrentUser.setText("User: " + username + "\nProject: " + project);

        List<UserItem> originalList = UserManager.loadUsers(this);
        List<UserItem> userList = new ArrayList<>();

        for (UserItem u : originalList) {
            if (u.username.equals(username) &&
                    u.project.equals(project)) {
                userList.add(0, u);
            } else {
                userList.add(u);
            }
        }

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

        lastValidSelection = currentIndex;// rollback cancel switch

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
            else if (id == R.id.nav_delete_user) {
                showConfirmPasswordDialog();
            }

            drawerLayout.closeDrawers();
            return true;
        });


        recyclerFolders = findViewById(R.id.recyclerFolders);
        recyclerFiles = findViewById(R.id.recyclerFiles);

        recyclerFolders.setLayoutManager(new LinearLayoutManager(this));
        recyclerFiles.setLayoutManager(new LinearLayoutManager(this));

        edtSearchAll.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String key = stripAccent(s.toString()).toLowerCase();

                if (key.isEmpty()) {

                    if (fileAdapter != null) {
                        fileAdapter = new FileAdapter(new ArrayList<>());
                        recyclerFiles.setAdapter(fileAdapter);
                    }

                    if (folderAdapter != null) {
                        folderAdapter.resetAll();
                    }

                } else {
                    List<FileAdapter.FileItem> matchedFiles = new ArrayList<>();
                    Set<String> matchedFolders = new HashSet<>();

                    for (FileAdapter.FileItem f : allFiles) {
                        String nameNorm = stripAccent(f.name).toLowerCase();
                        if (nameNorm.contains(key)) {
                            matchedFiles.add(f);
                            matchedFolders.add(f.folder);
                        }
                    }

                    fileAdapter = new FileAdapter(matchedFiles);
                    recyclerFiles.setAdapter(fileAdapter);

                    if (folderAdapter != null) {
                        folderAdapter.filterByFolderSet(new ArrayList<>(matchedFolders));
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

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

        LinearLayout btnDashboard = findViewById(R.id.btnDashboard);
        LinearLayout btnBackup = findViewById(R.id.btnBackup);
        FloatingActionButton fabCenter = findViewById(R.id.fabCenter);

        btnDashboard.setOnClickListener(v -> {
            Toast.makeText(this, "Open Dashboard", Toast.LENGTH_SHORT).show();

        });

        fabCenter.setOnClickListener(v -> {
            Toast.makeText(this, "Main Action", Toast.LENGTH_SHORT).show();

        });

        btnBackup.setOnClickListener(v -> {
            Toast.makeText(this, "Open Backup", Toast.LENGTH_SHORT).show();

        });

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (toggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    private void loadFolders() {
        allFiles.clear();
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();

                Request listReq = new Request.Builder()
                        .url(storageUrl) // vd: http://ip:port/v1/AUTH_xxx
                        .addHeader("X-Auth-Token", token)
                        .get()
                        .build();

                Response listResp = client.newCall(listReq).execute();

                if (listResp.isSuccessful()) {
                    String body = listResp.body().string();
                    String[] lines = body.split("\n");

                    List<FolderAdapter.FolderItem> folders = new ArrayList<>();

                    for (String line : lines) {
                        String container = line.trim();
                        if (container.isEmpty()) continue;

                        long totalSize = 0L;
                        try {
                            String url = storageUrl + "/" + container + "?format=json";
                            Request cReq = new Request.Builder()
                                    .url(url)
                                    .addHeader("X-Auth-Token", token)
                                    .get()
                                    .build();

                            Response cResp = client.newCall(cReq).execute();
                            if (cResp.isSuccessful()) {
                                String json = cResp.body().string();
                                JSONArray arr = new JSONArray(json);

                                for (int i = 0; i < arr.length(); i++) {
                                    JSONObject obj = arr.getJSONObject(i);
                                    long size = obj.optLong("bytes", 0);
                                    totalSize += size;
                                }
                            }
                        } catch (Exception ignored) {}

                        if (container.equalsIgnoreCase("config")) continue;

                        folders.add(new FolderAdapter.FolderItem(container, totalSize));
                        loadAllFilesOfFolderForSearchOnly(container);
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

                        files.add(new FileAdapter.FileItem(name, size, containerName));
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

        View view = getLayoutInflater().inflate(R.layout.dialog_switch_user, null);

        EditText edt = view.findViewById(R.id.edtSwitchPassword);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnSave = view.findViewById(R.id.btnSave);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        btnCancel.setOnClickListener(v -> {
            userSpinner.setSelection(lastValidSelection); // rollback dropdown
            dialog.dismiss();
        });

        btnSave.setOnClickListener(v -> {
            String pass = edt.getText().toString().trim();

            if (pass.isEmpty()) {
                Toast.makeText(this, "Password required!", Toast.LENGTH_SHORT).show();
                return;
            }

            validateUserPassword(user, pass);
            dialog.dismiss();
        });

        dialog.show();
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
                    lastValidSelection = userSpinner.getSelectedItemPosition();
                    refreshUserSpinnerInNav();

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

    private void showConfirmPasswordDialog() {

        View view = getLayoutInflater().inflate(R.layout.dialog_delete_user, null);

        Spinner spinner = view.findViewById(R.id.spinnerUsers);
        EditText edtPassword = view.findViewById(R.id.edtDeletePassword);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnYes = view.findViewById(R.id.btnYes);

        // ===== LOAD + ĐƯA USER ĐANG LOGIN LÊN ĐẦU =====
        List<UserItem> userList = new ArrayList<>();
        List<UserItem> originalList = UserManager.loadUsers(this);

        for (UserItem u : originalList) {
            if (u.username.equals(username) &&
                    u.project.equals(project)) {
                userList.add(0, u);
            } else {
                userList.add(u);
            }
        }

        // ===== TẠO ADAPTER =====
        List<String> names = new ArrayList<>();
        for (UserItem u : userList) {
            names.add(u.username + " (" + u.project + ")");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                names
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnYes.setOnClickListener(v -> {

            int pos = spinner.getSelectedItemPosition();
            UserItem selectedUser = userList.get(pos);
            String inputPass = edtPassword.getText().toString().trim();

            if (inputPass.isEmpty()) {
                Toast.makeText(this, "Password required!", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean ok = UserManager.verifyAndDeleteUser(
                    HomeActivity.this,
                    selectedUser.username,
                    selectedUser.project,
                    inputPass
            );

            if (!ok) {
                Toast.makeText(this, "Wrong password!", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this, "User deleted!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();

            // ===== REFRESH DỮ LIỆU NGAY SAU KHI XÓA =====
            userList.clear();
            names.clear();

            List<UserItem> freshList = UserManager.loadUsers(this);

            for (UserItem u : freshList) {
                userList.add(u);
                names.add(u.username + " (" + u.project + ")");
            }

            adapter.notifyDataSetChanged();

            edtPassword.setText("");

            // ===== NẾU XÓA USER ĐANG LOGIN → LOGOUT =====
            if (selectedUser.username.equals(username) &&
                    selectedUser.project.equals(project)) {

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
        });

        dialog.show();
    }

    private void refreshUserSpinnerInNav() {
        View headerView = navigationView.getHeaderView(0);
        Spinner navSpinner = headerView.findViewById(R.id.userSpinner);

        List<UserItem> userList = UserManager.loadUsers(this);
        List<String> names = new ArrayList<>();

        for (UserItem u : userList) {
            names.add(u.username + " (" + u.project + ")");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                names
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        navSpinner.setAdapter(adapter);
        for (int i = 0; i < userList.size(); i++) {
            if (userList.get(i).username.equals(username) &&
                    userList.get(i).project.equals(project)) {
                navSpinner.setSelection(i);
                break;
            }
        }
    }

    private String stripAccent(String s) {
        if (s == null) return "";
        String temp = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        return temp.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private void loadAllFilesOfFolderForSearchOnly(String containerName) {
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

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String name = obj.getString("name");
                        long size = obj.getLong("bytes");

                        allFiles.add(new FileAdapter.FileItem(name, size, containerName));
                    }
                }

            } catch (Exception ignored) {}
        }).start();
    }

    private void doSearch(String text) {
        String key = stripAccent(text).toLowerCase();

        if (key.isEmpty()) {

            if (fileAdapter != null) {
                fileAdapter = new FileAdapter(new ArrayList<>());
                recyclerFiles.setAdapter(fileAdapter);
            }

            if (folderAdapter != null) {
                folderAdapter.resetAll();
            }

        } else {

            List<FileAdapter.FileItem> matchedFiles = new ArrayList<>();
            Set<String> matchedFolders = new HashSet<>();

            for (FileAdapter.FileItem f : allFiles) {
                String nameNorm = stripAccent(f.name).toLowerCase();
                if (nameNorm.contains(key)) {
                    matchedFiles.add(f);
                    matchedFolders.add(f.folder);
                }
            }

            fileAdapter = new FileAdapter(matchedFiles);
            recyclerFiles.setAdapter(fileAdapter);

            if (folderAdapter != null) {
                folderAdapter.filterByFolderSet(new ArrayList<>(matchedFolders));
            }
        }
    }


}

