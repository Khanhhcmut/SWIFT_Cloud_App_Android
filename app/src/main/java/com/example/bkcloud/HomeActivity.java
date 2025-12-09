package com.example.bkcloud;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
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

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.io.InputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.RequestBody;
import okhttp3.MediaType;
import okio.BufferedSink;

import androidx.documentfile.provider.DocumentFile;
import android.util.Pair;


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
    String currentSelectedFolder = null;
    int pendingUpload = 0;
    boolean batchUploadMode = false;
    boolean deleteMode = false;
    Set<String> selectedDeleteItems = new HashSet<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                DrawerLayout drawer = findViewById(R.id.drawerLayout);

                if (drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START);
                } else {
                    confirmLogout();
                }
            }
        });

        if (android.os.Build.VERSION.SDK_INT < 33) {
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 2001);
        }

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

        findViewById(R.id.btnMyFiles).setOnClickListener(v -> {
            loadFolders();
            if (currentSelectedFolder != null)
                loadFiles(currentSelectedFolder);
        });

        fabCenter.setOnClickListener(v -> {

            View view = getLayoutInflater().inflate(R.layout.dialog_upload_select, null);
            AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();

            TextView txtTitle = view.findViewById(R.id.txtTitle);
            Button btnUploadFile = view.findViewById(R.id.btnUploadFile);
            Button btnUploadFolder = view.findViewById(R.id.btnUploadFolder);
            Button btnCancel = view.findViewById(R.id.btnCancel);

            if (currentSelectedFolder == null) {

                txtTitle.setText("Folder options");

                btnUploadFile.setText("Create New Folder");
                btnUploadFolder.setText("Upload Folder");

                btnUploadFile.setOnClickListener(x -> {
                    dialog.dismiss();
                    showCreateFolderDialog();
                });

                btnUploadFolder.setOnClickListener(x -> {
                    dialog.dismiss();
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    startActivityForResult(i, 1002);
                });

            } else {

                txtTitle.setText("Upload options");

                btnUploadFile.setText("Upload File");
                btnUploadFolder.setText("Upload Folder");

                btnUploadFile.setOnClickListener(x -> {
                    dialog.dismiss();
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.setType("*/*");
                    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    startActivityForResult(i, 1001);
                });

                btnUploadFolder.setOnClickListener(x -> {
                    dialog.dismiss();
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    startActivityForResult(i, 1002);
                });
            }

            btnCancel.setOnClickListener(x -> dialog.dismiss());

            dialog.show();
        });

        findViewById(R.id.btnRefresh).setOnClickListener(v -> {
            currentSelectedFolder = null;
            fileAdapter = new FileAdapter(new ArrayList<>());
            fileAdapter.setListener(new FileAdapter.FileListener() {
                @Override
                public void onLongPress(String key) {
                    HomeActivity.this.onItemLongPress(key);
                }
                @Override
                public void onToggleSelect(String key) {
                    HomeActivity.this.onItemToggle(key);
                }
                @Override
                public void onClickDeleteIcon() {
                    HomeActivity.this.onDeleteIconClick();
                }
            });
            recyclerFiles.setAdapter(fileAdapter);
            loadFolders();
            Toast.makeText(this, "Refresh", Toast.LENGTH_SHORT).show();
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
                        folderAdapter = new FolderAdapter(folders, clickedName -> {
                            if (clickedName == null) {
                                currentSelectedFolder = null;
                                fileAdapter = new FileAdapter(new ArrayList<>());
                                recyclerFiles.setAdapter(fileAdapter);
                            } else {
                                currentSelectedFolder = clickedName;
                                loadFiles(clickedName);
                            }
                        });
                        recyclerFolders.setAdapter(folderAdapter);
                        folderAdapter.setSelectedFolder(currentSelectedFolder);

                        folderAdapter.deleteListener = new FolderAdapter.FolderListener() {
                            @Override
                            public void onLongPress(String name) {
                                HomeActivity.this.onItemLongPress(name);
                            }

                            @Override
                            public void onToggleSelect(String name) {
                                HomeActivity.this.onItemToggle(name);
                            }

                            @Override
                            public void onDeleteIcon() {
                                HomeActivity.this.onDeleteIconClick();
                            }
                        };
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

                        String last = obj.optString("last_modified", "");
                        files.add(new FileAdapter.FileItem(name, size, containerName, last));
                    }

                    runOnUiThread(() -> {
                        fileAdapter = new FileAdapter(files);
                        recyclerFiles.setAdapter(fileAdapter);

                        fileAdapter.setListener(new FileAdapter.FileListener() {
                            @Override
                            public void onLongPress(String key) {
                                HomeActivity.this.onItemLongPress(key);
                            }

                            @Override
                            public void onToggleSelect(String key) {
                                HomeActivity.this.onItemToggle(key);
                            }

                            @Override
                            public void onClickDeleteIcon() {
                                HomeActivity.this.onDeleteIconClick();
                            }
                        });

                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void refreshAdaptersToShowDeleteMode() {
        if (fileAdapter != null) fileAdapter.setDeleteMode(deleteMode, selectedDeleteItems);
        if (folderAdapter != null) folderAdapter.setDeleteMode(deleteMode, selectedDeleteItems);
    }

    public void onItemLongPress(String key) {
        if (!deleteMode) {
            deleteMode = true;
            selectedDeleteItems.clear();
            selectedDeleteItems.add(key);
        } else {
            deleteMode = false;
            selectedDeleteItems.clear();
        }
        refreshAdaptersToShowDeleteMode();
    }

    public void onItemToggle(String key) {
        if (!deleteMode) return;

        if (selectedDeleteItems.contains(key))
            selectedDeleteItems.remove(key);
        else
            selectedDeleteItems.add(key);

        refreshAdaptersToShowDeleteMode();
    }

    public void onDeleteIconClick() {
        if (selectedDeleteItems.isEmpty()) return;

        showDeleteConfirmDialog();
    }

    private void showDeleteConfirmDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_delete_confirm);

        Button btnCancel = dialog.findViewById(R.id.btnCancel);
        Button btnYes = dialog.findViewById(R.id.btnYes);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnYes.setOnClickListener(v -> {
            dialog.dismiss();
            deleteSelectedItems();
        });

        dialog.show();
    }

    private void deleteSelectedItems() {
        new Thread(() -> {
            OkHttpClient client = new OkHttpClient();

            for (String key : selectedDeleteItems) {
                try {
                    String low = key.toLowerCase();
                    if (!key.contains("/") && (low.equals("backup") || low.equals("dicom") || low.equals("config"))) {
                        continue;
                    }

                    if (key.contains("/")) {
                        String folder = key.substring(0, key.indexOf("/"));
                        String name = key.substring(key.indexOf("/") + 1);

                        String url = storageUrl + "/" + folder + "/" + name;

                        Request req = new Request.Builder()
                                .url(url)
                                .delete()
                                .addHeader("X-Auth-Token", token)
                                .build();

                        client.newCall(req).execute();

                    } else {
                        String listUrl = storageUrl + "/" + key + "?format=json";

                        Request listReq = new Request.Builder()
                                .url(listUrl)
                                .get()
                                .addHeader("X-Auth-Token", token)
                                .build();

                        Response resp = client.newCall(listReq).execute();
                        String body = resp.body().string();
                        JSONArray arr = new JSONArray(body);

                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            String fname = obj.getString("name");

                            String fUrl = storageUrl + "/" + key + "/" + fname;

                            Request delFile = new Request.Builder()
                                    .url(fUrl)
                                    .delete()
                                    .addHeader("X-Auth-Token", token)
                                    .build();

                            client.newCall(delFile).execute();
                        }

                        String delFolderUrl = storageUrl + "/" + key;

                        Request delFolder = new Request.Builder()
                                .url(delFolderUrl)
                                .delete()
                                .addHeader("X-Auth-Token", token)
                                .build();

                        client.newCall(delFolder).execute();
                    }

                } catch (Exception ignored) {}
            }

            deleteMode = false;
            selectedDeleteItems.clear();

            runOnUiThread(() -> {
                loadFolders();
                if (currentSelectedFolder != null) loadFiles(currentSelectedFolder);
                Toast.makeText(this, "Delete complete", Toast.LENGTH_SHORT).show();
            });

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
                        String last = obj.optString("last_modified", "");

                        allFiles.add(new FileAdapter.FileItem(name, size, containerName, last));
                    }
                }

            } catch (Exception ignored) {}
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == 1001) {  // Upload FILE vào folder đang chọn

            if (currentSelectedFolder == null) {
                Toast.makeText(this, "Please select folder to upload", Toast.LENGTH_SHORT).show();
                return;
            }

            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                pendingUpload = count;
                batchUploadMode = true;

                for (int i = 0; i < count; i++) {
                    Uri fileUri = data.getClipData().getItemAt(i).getUri();
                    String fileName = getFileName(fileUri);

                    uploadToSwift(currentSelectedFolder, fileUri, fileName, () -> {
                        pendingUpload--;
                        if (pendingUpload == 0) {
                            batchUploadMode = false;
                            loadFiles(currentSelectedFolder);
                            loadFolders();
                            Toast.makeText(this, "Upload completed", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

            } else if (data.getData() != null) {
                Uri fileUri = data.getData();
                String fileName = getFileName(fileUri);

                pendingUpload = 1;
                batchUploadMode = true;

                uploadToSwift(currentSelectedFolder, fileUri, fileName, () -> {
                    pendingUpload = 0;
                    batchUploadMode = false;
                    loadFiles(currentSelectedFolder);
                    loadFolders();
                    Toast.makeText(this, "Upload completed", Toast.LENGTH_SHORT).show();
                });
            }

        } else if (requestCode == 1002) {  // Upload FOLDER

            Uri treeUri = data.getData();
            if (treeUri == null) return;

            try {
                getContentResolver().takePersistableUriPermission(
                        treeUri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            } catch (Exception ignored) {}

            uploadDirectory(treeUri);
        }
    }

    private void uploadToSwift(String container, Uri uri, String objectName, Runnable onDone) {
        new Thread(() -> {
            try {
                InputStream in = getContentResolver().openInputStream(uri);

                RequestBody body = new RequestBody() {
                    @Override
                    public MediaType contentType() {
                        return MediaType.parse("application/octet-stream");
                    }

                    @Override
                    public void writeTo(BufferedSink sink) throws IOException {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            sink.write(buffer, 0, read);
                        }
                    }
                };

                OkHttpClient client = new OkHttpClient.Builder().build();
                String url = storageUrl + "/" + container + "/" + objectName;

                Request req = new Request.Builder()
                        .url(url)
                        .put(body)
                        .addHeader("X-Auth-Token", token)
                        .build();

                client.newCall(req).execute();

                runOnUiThread(onDone);

            } catch (Exception ignored) {}
        }).start();
    }


    private String getFileName(Uri uri) {
        String result = null;

        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) result = cursor.getString(index);
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        }

        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) result = result.substring(cut + 1);
        }

        return result;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 2001) {
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void uploadDirectory(Uri treeUri) {
        new Thread(() -> {
            try {
                DocumentFile dir = DocumentFile.fromTreeUri(this, treeUri);
                if (dir == null) return;

                String rootName = dir.getName();
                if (rootName == null) rootName = "";

                String sys = rootName.toLowerCase();
                if (sys.equals("backup") || sys.equals("dicom") || sys.equals("config")) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "Can't upload, duplicate system folder name", Toast.LENGTH_SHORT).show()
                    );
                    return;
                }

                boolean uploadToNewContainer = (currentSelectedFolder == null);

                String containerName = uploadToNewContainer ? rootName : currentSelectedFolder;
                String basePath = uploadToNewContainer ? "" : rootName;

                if (uploadToNewContainer) {
                    OkHttpClient client = new OkHttpClient();
                    String url = storageUrl + "/" + containerName;

                    Request request = new Request.Builder()
                            .url(url)
                            .put(RequestBody.create(new byte[0], null))
                            .addHeader("X-Auth-Token", token)
                            .build();

                    Response resp = client.newCall(request).execute();
                    if (resp.code() != 201 && resp.code() != 202 && resp.code() != 204) {
                        return;
                    }
                }

                List<Pair<String, Uri>> files = new ArrayList<>();
                collectFilesRecursively(dir, basePath, files);

                int total = files.size();

                if (total == 0) {
                    runOnUiThread(() -> {
                        loadFolders();
                        Toast.makeText(this, "Upload completed", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                int[] done = {0};

                for (Pair<String, Uri> p : files) {
                    String objectPath = p.first;
                    Uri fileUri = p.second;

                    uploadToSwiftWithCallback(containerName, fileUri, objectPath, () -> {
                        done[0]++;
                        if (done[0] == total) {
                            runOnUiThread(() -> {
                                if (!uploadToNewContainer && currentSelectedFolder != null) {
                                    loadFiles(currentSelectedFolder);
                                }
                                loadFolders();
                                Toast.makeText(this, "Upload completed", Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
                }

            } catch (Exception ignored) {}
        }).start();
    }

    private void collectFilesRecursively(DocumentFile dir, String base, List<Pair<String, Uri>> out) {
        for (DocumentFile f : dir.listFiles()) {
            if (f.isDirectory()) {
                String newBase = base.isEmpty() ? f.getName() : base + "/" + f.getName();
                collectFilesRecursively(f, newBase, out);
            } else if (f.isFile()) {
                String rel = base.isEmpty() ? f.getName() : base + "/" + f.getName();
                out.add(new Pair<>(rel, f.getUri()));
            }
        }
    }

    private void uploadToSwiftWithCallback(String container, Uri uri, String objectName, Runnable onDone) {
        new Thread(() -> {
            try {
                InputStream in = getContentResolver().openInputStream(uri);

                RequestBody body = new RequestBody() {
                    @Override
                    public MediaType contentType() {
                        return MediaType.parse("application/octet-stream");
                    }

                    @Override
                    public void writeTo(BufferedSink sink) throws IOException {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            sink.write(buffer, 0, read);
                        }
                    }
                };

                OkHttpClient client = new OkHttpClient.Builder().build();

                String url = storageUrl + "/" + container + "/" + objectName;

                Request req = new Request.Builder()
                        .url(url)
                        .put(body)
                        .addHeader("X-Auth-Token", token)
                        .build();

                Response resp = client.newCall(req).execute();

                runOnUiThread(onDone);

            } catch (Exception ignored) {}
        }).start();
    }

    private void showCreateFolderDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_create_folder, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();

        EditText edtFolderName = view.findViewById(R.id.edtFolderName);
        Button btnCreate = view.findViewById(R.id.btnCreate);
        Button btnCancel = view.findViewById(R.id.btnCancel);

        btnCreate.setOnClickListener(v -> {
            String folderName = edtFolderName.getText().toString().trim();
            if (folderName.isEmpty()) return;
            String sys = folderName.toLowerCase();
            if (sys.equals("backup") || sys.equals("dicom") || sys.equals("config")) {
                Toast.makeText(this, "This is system folder name, please change another name", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            createEmptyFolder(folderName);
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void createEmptyFolder(String folderName) {
        new Thread(() -> {
            try {
                String url = storageUrl + "/" + folderName;

                OkHttpClient client = new OkHttpClient();

                Request headReq = new Request.Builder()
                        .url(url)
                        .head()
                        .addHeader("X-Auth-Token", token)
                        .build();

                Response headResp = client.newCall(headReq).execute();
                if (headResp.code() == 204) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "Folder already exists", Toast.LENGTH_SHORT).show()
                    );
                    return;
                }

                Request putReq = new Request.Builder()
                        .url(url)
                        .put(RequestBody.create(new byte[0], null))
                        .addHeader("X-Auth-Token", token)
                        .addHeader("Content-Length", "0")
                        .build();

                Response putResp = client.newCall(putReq).execute();

                if (putResp.code() == 201 || putResp.code() == 202 || putResp.code() == 204) {
                    runOnUiThread(() -> {
                        loadFolders();
                        Toast.makeText(this, "Folder created", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    runOnUiThread(() ->
                            Toast.makeText(this, "Create folder failed: HTTP " + putResp.code(), Toast.LENGTH_SHORT).show()
                    );
                }

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Create folder error", Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }

}

