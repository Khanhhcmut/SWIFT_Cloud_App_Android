package com.example.bkcloud;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LoginHelper {

    public interface LoginResult {
        void onSuccess(String token, String storageUrl, String userId, String projectId);
        void onError(String message);
    }

    public static void login(Context ctx, String username, String password, String project, LoginResult callback) {

        new Thread(() -> {
            try {
                SharedPreferences prefs = ctx.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
                String baseUrl = prefs.getString("swift_url", "");

                if (baseUrl == null || baseUrl.isEmpty()) {
                    baseUrl = "http://192.168.1.106";
                }

                String authUrl = baseUrl + "/identity/v3/auth/tokens";

                OkHttpClient client = new OkHttpClient();

                String json = "{\n" +
                        "  \"auth\": {\n" +
                        "    \"identity\": {\n" +
                        "      \"methods\": [\"password\"],\n" +
                        "      \"password\": {\n" +
                        "        \"user\": {\n" +
                        "          \"name\": \"" + username + "\",\n" +
                        "          \"domain\": {\"id\": \"default\"},\n" +
                        "          \"password\": \"" + password + "\"\n" +
                        "        }\n" +
                        "      }\n" +
                        "    },\n" +
                        "    \"scope\": {\n" +
                        "      \"project\": {\n" +
                        "        \"name\": \"" + project + "\",\n" +
                        "        \"domain\": {\"id\": \"default\"}\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "}";

                RequestBody body = RequestBody.create(json, okhttp3.MediaType.parse("application/json"));

                Request request = new Request.Builder()
                        .url(authUrl)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .build();

                Response response = client.newCall(request).execute();

                if (response.code() == 201) {
                    String token = response.header("X-Subject-Token");
                    String respBody = response.body().string();

                    JSONObject root = new JSONObject(respBody).getJSONObject("token");

                    String userId = root.getJSONObject("user").getString("id");
                    String projectId = root.getJSONObject("project").getString("id");

                    JSONArray catalog = root.getJSONArray("catalog");

                    String storageUrl = null;

                    for (int i = 0; i < catalog.length(); i++) {
                        JSONObject service = catalog.getJSONObject(i);
                        if (service.getString("type").equals("object-store")) {
                            JSONArray endpoints = service.getJSONArray("endpoints");
                            for (int j = 0; j < endpoints.length(); j++) {
                                JSONObject ep = endpoints.getJSONObject(j);
                                if (ep.getString("interface").equals("public")) {
                                    storageUrl = ep.getString("url");
                                    break;
                                }
                            }
                        }
                    }

                    if (storageUrl != null && !storageUrl.contains("/v1/")) {
                        storageUrl = storageUrl + "/v1/AUTH_" + projectId;
                    }

                    String finalStorageUrl = storageUrl;

                    callback.onSuccess(token, finalStorageUrl, userId, projectId);

                } else if (response.code() == 401) {
                    callback.onError("Wrong username or password");
                } else {
                    callback.onError("Login error: " + response.code());
                }

            } catch (Exception e) {
                callback.onError("Connection error: " + e.getMessage());
            }
        }).start();
    }
}
