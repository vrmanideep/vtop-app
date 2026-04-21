package com.vtop.models;

public class AttendanceDetailModel {
    public String date;
    public String slot;
    public String status;

    public AttendanceDetailModel(String date, String slot, String status) {
        this.date = date;
        this.slot = slot;
        this.status = status;
    }
}