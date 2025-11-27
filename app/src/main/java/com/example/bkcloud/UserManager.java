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

        // không thêm trùng user
        for (UserItem u : users) {
            if (u.username.equals(user.username)) {
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
                        o.getString("storageUrl")
                ));
            }
        } catch (Exception e) {}

        return list;
    }

    public static void deleteUser(Context ctx, String username) {
        List<UserItem> list = loadUsers(ctx);
        List<UserItem> newList = new ArrayList<>();

        for (UserItem u : list) {
            if (!u.username.equals(username)) {
                newList.add(u);
            }
        }

        saveList(ctx, newList);
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
                arr.put(o);
            }

            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY, arr.toString())
                    .apply();

        } catch (Exception e) {}
    }
}
