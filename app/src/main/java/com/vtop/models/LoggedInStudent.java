package com.vtop.models;

public class LoggedInStudent {
    public String registrationNumber;
    public String postLoginCsrfToken;

    public LoggedInStudent(String registrationNumber, String postLoginCsrfToken) {
        this.registrationNumber = registrationNumber;
        this.postLoginCsrfToken = postLoginCsrfToken;
    }
}