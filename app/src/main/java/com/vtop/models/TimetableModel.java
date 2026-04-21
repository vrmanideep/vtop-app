package com.vtop.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TimetableModel {
    public Map<String, List<CourseSession>> scheduleMap = new HashMap<>();

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