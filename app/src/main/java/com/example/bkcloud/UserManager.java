package com.example.bkcloud;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class UserManager {

    private static final String PREF = "UserList";
    private static final String KEY = "users";

    public static void saveUser(Context ctx, UserItem user) {
        List<UserItem> users = loadUsers(ctx);

        for (UserItem u : users) {
            if (u.username.equals(user.username) &&
                    u.project.equals(user.project)) {
                return;
            }
        }

        users.add(user);
        saveList(ctx, users);
    }

    public static List<UserItem> loadUsers(Context ctx) {
        List<UserItem> list = new ArrayList<>();

        SharedPreferences pref = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String json = pref.getString(KEY, "[]");

        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new UserItem(
                        o.getString("username"),
                        o.getString("project"),
                        o.getString("token"),
                        o.getString("storageUrl"),
                        o.getString("password")
                ));
            }
        } catch (Exception e) {}

        return list;
    }

    private static void saveList(Context ctx, List<UserItem> users) {
        try {
            JSONArray arr = new JSONArray();

            for (UserItem u : users) {
                JSONObject o = new JSONObject();
                o.put("username", u.username);
                o.put("project", u.project);
                o.put("token", u.token);
                o.put("storageUrl", u.storageUrl);
                o.put("password", u.password);
                arr.put(o);
            }

            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY, arr.toString())
                    .apply();

        } catch (Exception e) {}
    }

    public static boolean verifyAndDeleteUser(
            Context ctx,
            String username,
            String project,
            String inputPassword
    ) {
        List<UserItem> users = loadUsers(ctx);
        List<UserItem> newList = new ArrayList<>();
        boolean deleted = false;

        for (UserItem u : users) {

            if (u.username.equals(username) &&
                    u.project.equals(project)) {

                // Sai mật khẩu → không xóa
                if (!u.password.equals(inputPassword)) {
                    return false;
                }

                // Đúng mật khẩu → bỏ qua user này (tức là xóa)
                deleted = true;
                continue;
            }

            newList.add(u);
        }

        if (deleted) {
            saveList(ctx, newList);
        }

        return deleted;
    }

    public static void updatePassword(
            Context ctx,
            String username,
            String project,
            String newPassword
    ) {
        List<UserItem> users = loadUsers(ctx);
        boolean updated = false;

        for (UserItem u : users) {
            if (u.username.equals(username) &&
                    u.project.equals(project)) {
                u.password = newPassword;
                updated = true;
                break;
            }
        }

        if (updated) {
            saveList(ctx, users);
        }
    }

}
