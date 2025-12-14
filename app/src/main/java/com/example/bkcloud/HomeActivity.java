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
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
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
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;


import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import java.util.Calendar;
import java.util.Collections;
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

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;

import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TimePicker;


public class HomeActivity extends AppCompatActivity {

    int folderSortMode = 0;
    int fileSortMode = 0;
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
    List<FolderAdapter.FolderItem> currentFoldersList = new ArrayList<>();
    List<FileAdapter.FileItem> currentFileList = new ArrayList<>();
    static final byte[] SECRET_KEY = "bkcloud-secret-key".getBytes(StandardCharsets.UTF_8);

    TextView txtStorageUsage;
    ProgressBar progressStorage;
    long totalQuotaBytes = 0;
    long usedBytes = 0;

    TextView txtDocCount;
    TextView txtImageCount;
    TextView txtVideoCount;
    TextView txtAudioCount;
    TextView txtOtherCount;
    View layoutDashboard;
    LinearLayout layoutMyFiles, layoutBackup;
    PieChart pieChart;
    TextView txtPieUsage;

    Button btnSetBackupTime, btnChooseBackupFolder, btnBackupNow, btnClearBackup;
    TextView txtBackupStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        layoutDashboard = findViewById(R.id.layoutDashboard);
        layoutMyFiles = findViewById(R.id.layoutMyFiles);
        layoutBackup = findViewById(R.id.layoutBackup);

        txtDocCount = findViewById(R.id.txtDocCount);
        txtImageCount = findViewById(R.id.txtImageCount);
        txtVideoCount = findViewById(R.id.txtVideoCount);
        txtAudioCount = findViewById(R.id.txtAudioCount);
        txtOtherCount = findViewById(R.id.txtOtherCount);
        pieChart = findViewById(R.id.pieChart);
        txtPieUsage = findViewById(R.id.txtPieUsage);

        txtStorageUsage = findViewById(R.id.txtStorageUsage);
        progressStorage = findViewById(R.id.progressStorage);

        btnSetBackupTime = findViewById(R.id.btnSetBackupTime);
        btnChooseBackupFolder = findViewById(R.id.btnChooseBackupFolder);
        btnBackupNow = findViewById(R.id.btnBackupNow);
        btnClearBackup = findViewById(R.id.btnClearBackup);
        txtBackupStatus = findViewById(R.id.txtBackupStatus);


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
            else if (id == R.id.nav_help) {
                Intent intent = new Intent(this, HelpActivity.class);
                startActivity(intent);
            }
            else if (id == R.id.nav_myfile) {
                showPage(layoutMyFiles);
            }
            else if (id == R.id.nav_dashboard) {
                showPage(layoutDashboard);
            }
            else if (id == R.id.nav_backup) {
                showPage(layoutBackup);
            }

            drawerLayout.closeDrawers();
            return true;
        });

        recyclerFolders = findViewById(R.id.recyclerFolders);
        findViewById(R.id.btnSortFolders).setOnClickListener(v -> {
            folderSortMode = (folderSortMode + 1) % 6;
            applyFolderSort();
            showSortToast(folderSortMode);
        });

        recyclerFiles = findViewById(R.id.recyclerFiles);
        findViewById(R.id.btnSortFiles).setOnClickListener(v -> {
            fileSortMode = (fileSortMode + 1) % 6;
            applyFileSort();
            showSortToast(fileSortMode);
        });

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
            showPage(layoutDashboard);
        });

        findViewById(R.id.btnMyFiles).setOnClickListener(v -> {
            showPage(layoutMyFiles);
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
            doRefresh(true);
        });

        btnBackup.setOnClickListener(v -> {
            showPage(layoutBackup);
        });

        loadCloudQuotaFromConfig();

        if (getIntent().getBooleanExtra("backup_trigger", false)) {
            showBackupConfirmDialog();
        }

        btnSetBackupTime.setOnClickListener(v -> {
            showBackupTimeDialog();
        });

        btnChooseBackupFolder.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(i, 3001);
        });

        btnBackupNow.setOnClickListener(v -> {
            showBackupConfirmDialog();
        });

        btnClearBackup.setOnClickListener(v -> {

            Dialog dialog = new Dialog(this);
            dialog.setContentView(R.layout.dialog_clear_backup);
            dialog.setCancelable(true);

            dialog.findViewById(R.id.btnCancel)
                    .setOnClickListener(x -> dialog.dismiss());

            dialog.findViewById(R.id.btnOk)
                    .setOnClickListener(x -> {
                        BackupManager.clear(this);
                        dialog.dismiss();
                        Toast.makeText(this, "Backup settings cleared", Toast.LENGTH_SHORT).show();
                        txtBackupStatus.setText(
                                BackupManager.getStatusText(this)
                        );
                    });

            dialog.show();
        });

        txtBackupStatus.setText(
                BackupManager.getStatusText(this)
        );

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
                    currentFoldersList = folders;

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

                        applyFolderSort();
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
                    currentFileList = files;

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String name = obj.getString("name");
                        long size = obj.getLong("bytes");

                        String last = obj.optString("last_modified", "");
                        files.add(new FileAdapter.FileItem(name, size, containerName, last));
                    }

                    runOnUiThread(() -> {
                        fileAdapter = new FileAdapter(files);
                        applyFileSort();
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
        showActionDialog();
    }

    private void showActionDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_confirm_download_delete);

        Button btnDownload = dialog.findViewById(R.id.btnDownload);
        Button btnDelete = dialog.findViewById(R.id.btnDelete);
        Button btnCancel = dialog.findViewById(R.id.btnCancel);

        btnDownload.setOnClickListener(v -> {
            dialog.dismiss();

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, 9001);
        });

        btnDelete.setOnClickListener(v -> {
            dialog.dismiss();
            showDeleteConfirmDialog();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
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
                    if (!key.contains("/") && (low.equals("config"))) {
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
                Toast.makeText(this, "Delete completed", Toast.LENGTH_SHORT).show();
                loadCloudQuotaFromConfig();
                doRefresh(false);
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

                    doRefresh(false);
                    loadCloudQuotaFromConfig();
                    Toast.makeText(HomeActivity.this, "Switched to " + username, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(HomeActivity.this, "Wrong password", Toast.LENGTH_SHORT).show();
                    userSpinner.setSelection(lastValidSelection);
                });
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

                    boolean exists = false;
                    for (FileAdapter.FileItem f : allFiles) {
                        if (f.folder.equals(containerName) && f.name.equals(name)) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        allFiles.add(new FileAdapter.FileItem(name, size, containerName, last));
                    }
                }
            }

        } catch (Exception ignored) {}
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == 1001) {

            if (currentSelectedFolder == null) {
                Toast.makeText(this, "Please select folder to upload", Toast.LENGTH_SHORT).show();
                return;
            }

            long totalSize = 0;

            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    totalSize += getFileSize(uri);
                }
            } else if (data.getData() != null) {
                totalSize = getFileSize(data.getData());
            }

            if (!canUpload(totalSize)) {
                Toast.makeText(this, "Not enough storage quota", Toast.LENGTH_SHORT).show();
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
                            loadCloudQuotaFromConfig();
                            doRefresh(false);
                        }
                    });
                }

            } else if (data.getData() != null) {
                Uri fileUri = data.getData();
                String fileName = getFileName(fileUri);

                long size = getFileSize(fileUri);
                if (!canUpload(size)) {
                    Toast.makeText(this, "Not enough storage quota", Toast.LENGTH_SHORT).show();
                    return;
                }

                pendingUpload = 1;
                batchUploadMode = true;

                uploadToSwift(currentSelectedFolder, fileUri, fileName, () -> {
                    pendingUpload = 0;
                    batchUploadMode = false;
                    loadFiles(currentSelectedFolder);
                    loadFolders();
                    Toast.makeText(this, "Upload completed", Toast.LENGTH_SHORT).show();
                    loadCloudQuotaFromConfig();
                    doRefresh(false);
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
        } else if (requestCode == 9001) {
            Uri tree = data.getData();
            if (tree == null) return;

            getContentResolver().takePersistableUriPermission(
                    tree,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
            );

            startBatchDownload(tree);
        } else if (requestCode == 3001) {
            Uri treeUri = data.getData();
            if (treeUri == null) return;

            getContentResolver().takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );

            List<String> folders = BackupManager.getFolders(this);

            androidx.documentfile.provider.DocumentFile newDf =
                    androidx.documentfile.provider.DocumentFile.fromTreeUri(this, treeUri);

            if (newDf != null && newDf.getName() != null) {

                boolean exists = false;

                String newId = android.provider.DocumentsContract.getTreeDocumentId(treeUri);

                for (String u : folders) {
                    Uri oldUri = Uri.parse(u);
                    DocumentFile oldDf = DocumentFile.fromTreeUri(this, oldUri);

                    if (oldDf != null) {
                        String oldId =
                                android.provider.DocumentsContract.getTreeDocumentId(oldUri);

                        if (oldId.equals(newId)) {
                            exists = true;
                            break;
                        }
                    }
                }

                if (!exists) {
                    folders.add(treeUri.toString());
                    BackupManager.setFolders(this, folders);
                }
            }

            BackupManager.setEnabled(this, true);

            txtBackupStatus.setText(
                    BackupManager.getStatusText(this)
            );
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

                long totalSize = 0;
                for (Pair<String, Uri> p : files) {
                    totalSize += getFileSize(p.second);
                }

                if (!canUpload(totalSize)) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "Not enough storage quota", Toast.LENGTH_SHORT).show()
                    );
                    return;
                }

                int total = files.size();

                if (total == 0) {
                    runOnUiThread(() -> {
                        loadFolders();
                        Toast.makeText(this, "Upload completed", Toast.LENGTH_SHORT).show();
                        loadCloudQuotaFromConfig();
                        doRefresh(false);
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
                                loadCloudQuotaFromConfig();
                                doRefresh(false);
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

    private void applyFolderSort() {
        List<FolderAdapter.FolderItem> list = currentFoldersList;
        switch (folderSortMode) {
            case 0:
                Collections.sort(list, (a, b) -> a.name.compareToIgnoreCase(b.name));
                break;
            case 1:
                Collections.sort(list, (a, b) -> b.name.compareToIgnoreCase(a.name));
                break;
            case 2:
                Collections.sort(list, (a, b) -> Long.compare(a.size, b.size));
                break;
            case 3:
                Collections.sort(list, (a, b) -> Long.compare(b.size, a.size));
                break;
            case 4:
                Collections.sort(list, (a, b) -> a.name.split("\\.").length > 1
                        && b.name.split("\\.").length > 1
                        ? a.name.substring(a.name.lastIndexOf("."))
                        .compareToIgnoreCase(
                                b.name.substring(b.name.lastIndexOf(".")))
                        : 0);
                break;
            case 5:
                Collections.sort(list, (a, b) -> b.name.split("\\.").length > 1
                        && a.name.split("\\.").length > 1
                        ? b.name.substring(b.name.lastIndexOf("."))
                        .compareToIgnoreCase(
                                a.name.substring(a.name.lastIndexOf(".")))
                        : 0);
                break;
        }
        folderAdapter.notifyDataSetChanged();
    }

    private void applyFileSort() {
        List<FileAdapter.FileItem> list = currentFileList;
        switch (fileSortMode) {
            case 0:
                Collections.sort(list, (a, b) -> a.name.compareToIgnoreCase(b.name));
                break;
            case 1:
                Collections.sort(list, (a, b) -> b.name.compareToIgnoreCase(a.name));
                break;
            case 2:
                Collections.sort(list, (a, b) -> Long.compare(a.size, b.size));
                break;
            case 3:
                Collections.sort(list, (a, b) -> Long.compare(b.size, a.size));
                break;
            case 4:
                Collections.sort(list, (a, b) -> {
                    String extA = a.name.contains(".") ? a.name.substring(a.name.lastIndexOf(".")) : "";
                    String extB = b.name.contains(".") ? b.name.substring(b.name.lastIndexOf(".")) : "";
                    return extA.compareToIgnoreCase(extB);
                });
                break;
            case 5:
                Collections.sort(list, (a, b) -> {
                    String extA = a.name.contains(".") ? a.name.substring(a.name.lastIndexOf(".")) : "";
                    String extB = b.name.contains(".") ? b.name.substring(b.name.lastIndexOf(".")) : "";
                    return extB.compareToIgnoreCase(extA);
                });
                break;
        }
        fileAdapter.notifyDataSetChanged();
    }

    private void showSortToast(int mode) {
        String[] msgs = {
                "Sort: Name ↑",
                "Sort: Name ↓",
                "Sort: Size ↑",
                "Sort: Size ↓",
                "Sort: Type ↑",
                "Sort: Type ↓"
        };
        Toast.makeText(this, msgs[mode], Toast.LENGTH_SHORT).show();
    }

    private void startBatchDownload(Uri targetDirUri) {
        new Thread(() -> {
            DocumentFile root = DocumentFile.fromTreeUri(this, targetDirUri);
            if (root == null) return;

            OkHttpClient client = new OkHttpClient();

            for (String key : selectedDeleteItems) {
                try {
                    if (key.contains("/")) {

                        String folder = key.substring(0, key.indexOf("/"));
                        String name = key.substring(key.indexOf("/") + 1);

                        downloadSingleFile(client, root, folder, name);

                    } else {

                        downloadFolder(client, root, key);
                    }

                } catch (Exception ignored) {}
            }

            runOnUiThread(() -> {
                Toast.makeText(this, "Download completed", Toast.LENGTH_SHORT).show();
            });

        }).start();
    }

    private void downloadSingleFile(OkHttpClient client, DocumentFile root, String folder, String name) {
        try {
            String url = storageUrl + "/" + folder + "/" + name;

            Request req = new Request.Builder()
                    .url(url)
                    .addHeader("X-Auth-Token", token)
                    .get()
                    .build();

            Response resp = client.newCall(req).execute();
            byte[] data = resp.body().bytes();

            DocumentFile out = root.createFile("*/*", name);
            if (out == null) return;

            try (OutputStream os = getContentResolver().openOutputStream(out.getUri())) {
                os.write(data);
            }

        } catch (Exception ignored) {}
    }

    private void downloadFolder(OkHttpClient client, DocumentFile root, String folderName) {
        try {
            DocumentFile localFolder = root.findFile(folderName);

            if (localFolder != null) {
                int index = 1;
                String newName;

                while (true) {
                    newName = folderName + "(" + index + ")";
                    DocumentFile check = root.findFile(newName);
                    if (check == null) {
                        localFolder = root.createDirectory(newName);
                        break;
                    }
                    index++;
                }

            } else {
                localFolder = root.createDirectory(folderName);
            }

            if (localFolder == null) return;

            String url = storageUrl + "/" + folderName + "?format=json";

            Request req = new Request.Builder()
                    .url(url)
                    .addHeader("X-Auth-Token", token)
                    .get()
                    .build();

            Response resp = client.newCall(req).execute();
            JSONArray arr = new JSONArray(resp.body().string());

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String fname = obj.getString("name");

                downloadSingleFile(client, localFolder, folderName, fname);
            }

        } catch (Exception ignored) {}
    }

    private void loadCloudQuotaFromConfig() {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();

                Request putReq = new Request.Builder()
                        .url(storageUrl + "/config")
                        .addHeader("X-Auth-Token", token)
                        .put(RequestBody.create(new byte[0], null))
                        .build();
                client.newCall(putReq).execute();

                totalQuotaBytes = (long) (0.1 * 1024 * 1024 * 1024);
                usedBytes = 0;

                String url = storageUrl + "/config/config.json";
                Request req = new Request.Builder()
                        .url(url)
                        .addHeader("X-Auth-Token", token)
                        .get()
                        .build();

                Response resp = client.newCall(req).execute();

                if (resp.isSuccessful()) {
                    String base64Text = resp.body().string();
                    byte[] encrypted = base64Text.getBytes(StandardCharsets.UTF_8);
                    String json = decryptConfig(encrypted);

                    JSONObject obj = new JSONObject(json);
                    JSONObject users = obj.optJSONObject("users");

                    if (users != null) {
                        JSONObject me = users.optJSONObject(username.trim());
                        if (me != null && me.has("quota_gb")) {
                            double quotaGb = me.getDouble("quota_gb");
                            totalQuotaBytes = (long) (quotaGb * 1024 * 1024 * 1024);
                        }
                    }
                }

                calculateUsedBytesFromSwift();

            } catch (Exception e) {
                totalQuotaBytes = (long) (0.1 * 1024 * 1024 * 1024);
                usedBytes = 0;
                calculateUsedBytesFromSwift();
            }
        }).start();
    }


    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String unit = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), unit);
    }

    private String decryptConfig(byte[] encryptedFile) throws Exception {

        // 1. Base64 decode
        byte[] encrypted = android.util.Base64.decode(
                encryptedFile,
                android.util.Base64.DEFAULT
        );

        // 2. XOR decrypt
        byte[] decrypted = new byte[encrypted.length];
        for (int i = 0; i < encrypted.length; i++) {
            decrypted[i] = (byte) (encrypted[i] ^ SECRET_KEY[i % SECRET_KEY.length]);
        }

        // 3. JSON string
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private boolean canUpload(long uploadSize) {
        if (totalQuotaBytes <= 0) return false;
        return usedBytes + uploadSize <= totalQuotaBytes;
    }

    private long getFileSize(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.SIZE);
                if (idx != -1) return c.getLong(idx);
            }
        }
        return 0;
    }

    private void updateStorageBar() {
        if (totalQuotaBytes <= 0) return;

        int percent = (int) ((usedBytes * 100) / totalQuotaBytes);
        progressStorage.setProgress(percent);

        if (percent < 80) {
            progressStorage.setProgressTintList(
                    android.content.res.ColorStateList.valueOf(0xFF4CAF50)); // xanh
        } else if (percent < 95) {
            progressStorage.setProgressTintList(
                    android.content.res.ColorStateList.valueOf(0xFFFF9800)); // cam
        } else {
            progressStorage.setProgressTintList(
                    android.content.res.ColorStateList.valueOf(0xFFF44336)); // đỏ
        }

        txtStorageUsage.setText(
                formatSize(usedBytes) + " / " + formatSize(totalQuotaBytes)
        );
    }

    private void calculateUsedBytesFromSwift() {
        new Thread(() -> {
            long total = 0;
            try {
                OkHttpClient client = new OkHttpClient();

                // list containers
                Request listReq = new Request.Builder()
                        .url(storageUrl)
                        .addHeader("X-Auth-Token", token)
                        .get()
                        .build();

                Response listResp = client.newCall(listReq).execute();
                if (!listResp.isSuccessful()) return;

                String[] containers = listResp.body().string().split("\n");

                for (String container : containers) {
                    container = container.trim();
                    if (container.isEmpty() || container.equalsIgnoreCase("config"))
                        continue;

                    Request req = new Request.Builder()
                            .url(storageUrl + "/" + container + "?format=json")
                            .addHeader("X-Auth-Token", token)
                            .get()
                            .build();

                    Response resp = client.newCall(req).execute();
                    if (!resp.isSuccessful()) continue;

                    JSONArray arr = new JSONArray(resp.body().string());
                    for (int i = 0; i < arr.length(); i++) {
                        total += arr.getJSONObject(i).optLong("bytes", 0);
                    }
                }

            } catch (Exception e) {
                return;
            }

            usedBytes = total;
            runOnUiThread(this::updateStorageBar);
        }).start();
    }

    public void doRefresh(boolean showToast) {
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
        loadCloudQuotaFromConfig();

        if (showToast) {
            Toast.makeText(this, "Refresh", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateDashboardStats() {

        int cntDocs = 0, cntImages = 0, cntVideos = 0, cntAudios = 0, cntOthers = 0;
        long sizeDocs = 0, sizeImages = 0, sizeVideos = 0, sizeAudios = 0, sizeOthers = 0;
        long totalSize = 0;

        for (FileAdapter.FileItem f : allFiles) {
            String n = f.name.toLowerCase();
            long s = f.size;
            totalSize += s;

            if (n.endsWith(".pdf") || n.endsWith(".doc") || n.endsWith(".docx")
                    || n.endsWith(".xls") || n.endsWith(".xlsx")
                    || n.endsWith(".ppt") || n.endsWith(".pptx")
                    || n.endsWith(".txt")) {
                cntDocs++;
                sizeDocs += s;

            } else if (n.endsWith(".jpg") || n.endsWith(".png")
                    || n.endsWith(".jpeg") || n.endsWith(".gif")) {
                cntImages++;
                sizeImages += s;

            } else if (n.endsWith(".mp4") || n.endsWith(".mkv")
                    || n.endsWith(".avi")) {
                cntVideos++;
                sizeVideos += s;

            } else if (n.endsWith(".mp3") || n.endsWith(".wav")) {
                cntAudios++;
                sizeAudios += s;

            } else {
                cntOthers++;
                sizeOthers += s;
            }
        }

        final int fDocs = cntDocs;
        final int fImages = cntImages;
        final int fVideos = cntVideos;
        final int fAudios = cntAudios;
        final int fOthers = cntOthers;

        final long fSizeDocs = sizeDocs;
        final long fSizeImages = sizeImages;
        final long fSizeVideos = sizeVideos;
        final long fSizeAudios = sizeAudios;
        final long fSizeOthers = sizeOthers;
        final long fTotalSize = totalSize;

        runOnUiThread(() -> {

            // TEXT COUNTS
            txtDocCount.setText("Documents: " + fDocs);
            txtImageCount.setText("Images: " + fImages);
            txtVideoCount.setText("Videos: " + fVideos);
            txtAudioCount.setText("Audios: " + fAudios);
            txtOtherCount.setText("Others: " + fOthers);

            // PIE CHART (BY SIZE)
            if (pieChart != null) {
                List<PieEntry> entries = new ArrayList<>();

                if (fSizeDocs > 0) entries.add(new PieEntry(fSizeDocs, "Documents"));
                if (fSizeImages > 0) entries.add(new PieEntry(fSizeImages, "Images"));
                if (fSizeVideos > 0) entries.add(new PieEntry(fSizeVideos, "Videos"));
                if (fSizeAudios > 0) entries.add(new PieEntry(fSizeAudios, "Audios"));
                if (fSizeOthers > 0) entries.add(new PieEntry(fSizeOthers, "Others"));

                PieDataSet dataSet = new PieDataSet(entries, "");
                dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
                dataSet.setSliceSpace(2f);
                dataSet.setValueTextSize(12f);
                dataSet.setValueTextColor(Color.WHITE);

                PieData data = new PieData(dataSet);
                data.setValueFormatter(new ValueFormatter() {
                    @Override
                    public String getFormattedValue(float value) {
                        return String.format("%.1f%%", value);
                    }
                });

                pieChart.setUsePercentValues(true);
                pieChart.setDrawEntryLabels(true);
                pieChart.setEntryLabelColor(Color.BLACK);
                pieChart.setEntryLabelTextSize(12f);
                pieChart.setData(data);
                pieChart.getDescription().setEnabled(false);
                pieChart.getLegend().setEnabled(true);
                pieChart.animateY(800);
                pieChart.invalidate();
            }

            txtPieUsage.setText(
                    formatSize(fTotalSize) + " / " + formatSize(totalQuotaBytes)
            );
        });
    }


    private void showPage(View page) {
        View[] pages = {layoutDashboard, layoutMyFiles, layoutBackup};

        for (View v : pages) {
            if (v.getVisibility() == View.VISIBLE) {
                v.animate()
                        .alpha(0f)
                        .translationX(-40f)
                        .setDuration(150)
                        .withEndAction(() -> {
                            v.setVisibility(View.GONE);
                            v.setAlpha(1f);
                            v.setTranslationX(0f);
                        })
                        .start();
            }
        }

        page.setAlpha(0f);
        page.setTranslationX(40f);
        page.setVisibility(View.VISIBLE);
        page.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(400)
                .start();

        if (page == layoutDashboard) {
            recyclerFolders.postDelayed(this::updateDashboardStats, 300);
        }
    }

    private void showBackupConfirmDialog() {

        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_backup_confirm);
        dialog.setCancelable(true);

        dialog.findViewById(R.id.btnCancel)
                .setOnClickListener(v -> dialog.dismiss());

        dialog.findViewById(R.id.btnOk)
                .setOnClickListener(v -> {
                    dialog.dismiss();
                    BackupManager.startBackup(HomeActivity.this);
                });

        dialog.show();
    }

    private void showBackupTimeDialog() {

        final int[] selYear = {0};
        final int[] selMonth = {0};
        final int[] selDay = {0};

        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_backup_time);
        dialog.setCancelable(true);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            dialog.getWindow().setGravity(Gravity.CENTER);
        }

        RadioGroup rgMode = dialog.findViewById(R.id.rgMode);
        RadioButton rbDaily = dialog.findViewById(R.id.rbDaily);
        RadioButton rbWeekly = dialog.findViewById(R.id.rbWeekly);
        RadioButton rbSpecific = dialog.findViewById(R.id.rbSpecific);

        TimePicker timePicker = dialog.findViewById(R.id.timePicker);
        timePicker.setIs24HourView(true);
        LinearLayout layoutWeekdays = dialog.findViewById(R.id.layoutWeekdays);
        TextView txtSpecificDate = dialog.findViewById(R.id.txtSpecificDate);

        CheckBox cbMon = dialog.findViewById(R.id.cbMon);
        CheckBox cbTue = dialog.findViewById(R.id.cbTue);
        CheckBox cbWed = dialog.findViewById(R.id.cbWed);
        CheckBox cbThu = dialog.findViewById(R.id.cbThu);
        CheckBox cbFri = dialog.findViewById(R.id.cbFri);
        CheckBox cbSat = dialog.findViewById(R.id.cbSat);
        CheckBox cbSun = dialog.findViewById(R.id.cbSun);

        Button btnCancel = dialog.findViewById(R.id.btnCancel);
        Button btnOk = dialog.findViewById(R.id.btnOk);

        rbDaily.setChecked(true);

        txtSpecificDate.setOnClickListener(v -> {

            Calendar c = Calendar.getInstance();

            DatePickerDialog dp = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {

                        selYear[0] = year;
                        selMonth[0] = month + 1;
                        selDay[0] = dayOfMonth;

                        txtSpecificDate.setText(
                                String.format(
                                        "%04d-%02d-%02d",
                                        selYear[0], selMonth[0], selDay[0]
                                )
                        );
                    },
                    c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH),
                    c.get(Calendar.DAY_OF_MONTH)
            );

            dp.show();
        });

        rgMode.setOnCheckedChangeListener((g, id) -> {
            layoutWeekdays.setVisibility(
                    id == R.id.rbWeekly ? View.VISIBLE : View.GONE
            );
            txtSpecificDate.setVisibility(
                    id == R.id.rbSpecific ? View.VISIBLE : View.GONE
            );

            if (id != R.id.rbSpecific) {
                selYear[0] = 0;
                selMonth[0] = 0;
                selDay[0] = 0;
                txtSpecificDate.setText("Select date");
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnOk.setOnClickListener(v -> {

            int hour = timePicker.getHour();
            int minute = timePicker.getMinute();
            String time = String.format("%02d:%02d", hour, minute);

            BackupManager.setTime(this, time);
            BackupManager.setEnabled(this, true);

            if (rbDaily.isChecked()) {
                BackupManager.setMode(this, "DAILY");
            }

            if (rbWeekly.isChecked()) {
                BackupManager.setMode(this, "WEEKLY");

                List<Integer> days = new ArrayList<>();
                if (cbMon.isChecked()) days.add(Calendar.MONDAY);
                if (cbTue.isChecked()) days.add(Calendar.TUESDAY);
                if (cbWed.isChecked()) days.add(Calendar.WEDNESDAY);
                if (cbThu.isChecked()) days.add(Calendar.THURSDAY);
                if (cbFri.isChecked()) days.add(Calendar.FRIDAY);
                if (cbSat.isChecked()) days.add(Calendar.SATURDAY);
                if (cbSun.isChecked()) days.add(Calendar.SUNDAY);

                if (days.isEmpty()) {
                    Toast.makeText(
                            this,
                            "Please select at least one weekday",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                BackupManager.setWeekdays(this, days);
            }

            if (rbSpecific.isChecked()) {

                if (selYear[0] == 0) {
                    Toast.makeText(
                            this,
                            "Please select a date",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                BackupManager.setMode(this, "SPECIFIC_DAY");

                String date = String.format(
                        "%04d-%02d-%02d",
                        selYear[0], selMonth[0], selDay[0]
                );
                BackupManager.setSpecificDate(this, date);
            }

            BackupManager.schedule(this);
            txtBackupStatus.setText(BackupManager.getStatusText(this));

            dialog.dismiss();
        });

        dialog.show();
    }


}

