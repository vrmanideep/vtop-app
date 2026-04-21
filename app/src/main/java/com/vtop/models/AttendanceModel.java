package com.vtop.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class AttendanceModel implements Serializable {
    public String courseId;
    public String classId;
    public String courseCode;
    public String courseName;
    public String courseType;
    public String courseSlot;
    public String attendedClasses;
    public String totalClasses;
    public String attendancePercentage;
    public String debarStatus;

    // Holds the detailed history
    public List<AttendanceHistory> history = new ArrayList<>();

    public AttendanceModel(String courseId, String courseCode, String courseName,
                           String courseType, String courseSlot, String attendedClasses,
                           String totalClasses, String attendancePercentage, String debarStatus) {

        // --- NULL SAFETY WRAPPERS ---
        this.courseId = (courseId == null) ? "" : courseId;
        this.courseCode = (courseCode == null) ? "N/A" : courseCode;
        this.courseName = (courseName == null) ? "Unknown Course" : courseName;
        this.courseType = (courseType == null) ? "TH" : courseType;
        this.courseSlot = (courseSlot == null) ? "N/A" : courseSlot;
        this.classId = "N/A";

        // Ensure numbers are never null to prevent Compose rendering crashes
        this.attendedClasses = (attendedClasses == null) ? "0" : attendedClasses;
        this.totalClasses = (totalClasses == null) ? "0" : totalClasses;
        this.attendancePercentage = (attendancePercentage == null) ? "0" : attendancePercentage;
        this.debarStatus = (debarStatus == null) ? "N/A" : debarStatus;
    }

    public static class AttendanceHistory implements Serializable {
        public String date;
        public String slot;
        public String status;
        public String time;

        public AttendanceHistory(String date, String slot, String status, String time) {
            this.date = (date == null) ? "N/A" : date;
            this.slot = (slot == null) ? "N/A" : slot;
            this.status = (status == null) ? "N/A" : status;
            this.time = (time == null) ? "--:--" : time;
        }
    }
}