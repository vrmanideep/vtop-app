package com.vtop.models;

public class CourseSession {
    public String courseName;
    public String courseCode;
    public String courseType;
    public String slot;
    public String venue;
    public String faculty;
    public String timeSlot;
    public String gridDisplay;
    public String classId;
    public double credits;

    public CourseSession(String courseName, String courseCode, String courseType,
                         String slot, String venue, String faculty,
                         String timeSlot, String gridDisplay, String classId) {
        this(courseName, courseCode, courseType, slot, venue, faculty, timeSlot, gridDisplay, classId, 0.0);
    }

    public CourseSession(String courseName, String courseCode, String courseType,
                         String slot, String venue, String faculty,
                         String timeSlot, String gridDisplay, String classId, double credits) {
        this.courseName = courseName;
        this.courseCode = courseCode;
        this.courseType = courseType;
        this.slot = slot;
        this.venue = venue;
        this.faculty = faculty;
        this.timeSlot = timeSlot;
        this.gridDisplay = gridDisplay;
        this.classId = classId;
        this.credits = credits;
    }
}