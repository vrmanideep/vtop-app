package com.vtop.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TimetableModel {
    public static class RegisteredCourse {
        public String courseCode;
        public String courseName;
        public String courseType;
        public double credits;

        public RegisteredCourse(String courseCode, String courseName, String courseType, double credits) {
            this.courseCode = courseCode;
            this.courseName = courseName;
            this.courseType = courseType;
            this.credits = credits;
        }
    }

    public Map<String, List<CourseSession>> scheduleMap = new HashMap<>();

    // Map to hold exact start dates. Key = "CourseCode|CourseType", Value = "dd-MMM-yyyy"
    public Map<String, String> courseStartDates = new HashMap<>();

    // All registered course components with L T P J C credit values
    public List<RegisteredCourse> registeredCourses = new ArrayList<>();

    public TimetableModel() {
        scheduleMap.put("Monday", new ArrayList<>());
        scheduleMap.put("Tuesday", new ArrayList<>());
        scheduleMap.put("Wednesday", new ArrayList<>());
        scheduleMap.put("Thursday", new ArrayList<>());
        scheduleMap.put("Friday", new ArrayList<>());
        scheduleMap.put("Saturday", new ArrayList<>());
        scheduleMap.put("Sunday", new ArrayList<>());
    }
}