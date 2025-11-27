package com.example.bkcloud;

public class LoginResponse {
    public boolean success;
    public String token;
    public String storageUrl;
    public String error;

    public LoginResponse(boolean success, String token, String storageUrl, String error) {
        this.success = success;
        this.token = token;
        this.storageUrl = storageUrl;
        this.error = error;
    }
}
