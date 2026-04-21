package com.vtop.logic;

import android.util.Log;
import com.vtop.models.AttendanceModel;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.ArrayList;
import java.util.List;

public class AttendanceParser {
    private static final String TAG = "VTOP_DEBUG";

    public static List<AttendanceModel> parseSummary(String html) {
        List<AttendanceModel> dataList = new ArrayList<>();
        try {
            Document doc = Jsoup.parse(html);
            Element table = doc.selectFirst("table#AttendanceDetailDataTable");
            if (table == null) return dataList;

            Elements rows = table.select("tr");
            for (int i = 1; i < rows.size(); i++) {
                try {
                    Elements cells = rows.get(i).select("td");
                    if (cells.size() < 6) continue;

                    String courseDesc = cells.get(2).text().trim();
                    String[] parts = courseDesc.split("\\s*-\\s*");
                    if (parts.length < 2) continue;

                    String courseCode = parts[0].trim();

                    String rawType = "Theory";
                    if (parts.length > 2) {
                        rawType = parts[parts.length - 1].trim();
                    }

                    String courseName = parts[1].trim();
                    if (parts.length > 3) {
                        StringBuilder nameBuilder = new StringBuilder();
                        for(int j = 1; j < parts.length - 1; j++) {
                            nameBuilder.append(parts[j]).append(j < parts.length - 2 ? " - " : "");
                        }
                        courseName = nameBuilder.toString().trim();
                    }

                    String courseTypeCode = "TH";
                    String upType = rawType.toUpperCase();
                    if (upType.contains("EMBEDDED THEORY")) courseTypeCode = "ETH";
                    else if (upType.contains("EMBEDDED LAB") || upType.contains("PRACTICAL")) courseTypeCode = "ELA";
                    else if (upType.contains("EMBEDDED PROJECT") || upType.contains("PROJECT")) courseTypeCode = "EPJ";
                    else if (upType.contains("THEORY")) courseTypeCode = "TH";
                    else if (upType.contains("LAB") || upType.contains("LO")) courseTypeCode = "LO";

                    String classDetail = cells.get(3).text().trim();
                    String[] classParts = classDetail.split("\\s*-\\s*");
                    String apClassId = classParts[0];
                    String courseSlot = classParts.length > 1 ? classParts[1] : "";

                    String realCourseId = "";
                    String rowHtml = rows.get(i).outerHtml();

                    java.util.regex.Pattern idPattern = java.util.regex.Pattern.compile("(AM_[A-Z0-9_]+)");
                    java.util.regex.Matcher idMatcher = idPattern.matcher(rowHtml);

                    if (idMatcher.find()) {
                        realCourseId = idMatcher.group(1);
                    } else {
                        realCourseId = "AM_" + courseCode + "_00100";
                    }

                    String attended = cells.size() > 5 ? cells.get(5).text().trim() : "0";
                    String total = cells.size() > 6 ? cells.get(6).text().trim() : "0";
                    String percentage = cells.size() > 7 ? cells.get(7).text().replace("%", "").trim() : "0";
                    String debar = cells.size() > 9 ? cells.get(9).text().replace("%", "").trim() : "N/A";

                    AttendanceModel model = new AttendanceModel(
                            realCourseId, courseCode, courseName, courseTypeCode,
                            courseSlot, attended, total, percentage, debar);

                    model.classId = apClassId;
                    dataList.add(model);

                } catch (Exception rowEx) {
                    Log.e(TAG, "Failed to parse specific row, skipping safely.", rowEx);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Summary Document Error", e);
        }
        return dataList;
    }

    public static void parseDetailAndUpdate(String html, AttendanceModel course) {
        if (html == null || html.isEmpty()) return;

        try {
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);

            // 1. Get Class ID from the top summary table
            org.jsoup.nodes.Element courseTable = doc.getElementById("StudentCourseDetailDataTable");
            if (courseTable != null) {
                org.jsoup.select.Elements tds = courseTable.select("tbody tr td");
                if (tds.size() > 2) {
                    course.classId = tds.get(2).text().split(" - ")[0].trim();
                }
            }

            // 2. Locate the History Table
            org.jsoup.nodes.Element historyTable = doc.getElementById("StudentAttendanceDetailDataTable");

            // --- THE BULLETPROOF FALLBACK ---
            // If the ID is missing or changed, we search every table for the column headers.
            if (historyTable == null) {
                org.jsoup.select.Elements allTables = doc.select("table");
                for (org.jsoup.nodes.Element t : allTables) {
                    String text = t.text().toLowerCase();
                    // A true attendance history table will ALWAYS contain these headers
                    if (text.contains("date") && text.contains("slot") && text.contains("status")) {
                        historyTable = t;
                        break;
                    }
                }
            }

            if (historyTable == null) {
                android.util.Log.e("VTOP_DEBUG", "History table absolutely NOT FOUND for " + course.courseCode);
                return;
            }

            // 3. Select ONLY the rows inside the <tbody> to skip the <thead>
            org.jsoup.select.Elements rows = historyTable.select("tbody tr");

            if (course.history == null) course.history = new java.util.ArrayList<>();
            else course.history.clear();

            // 4. Parse the data
            for (org.jsoup.nodes.Element row : rows) {
                org.jsoup.select.Elements cols = row.select("td");

                // Safety check: The table in your example had 6 columns.
                // We need at least 5 to guarantee we hit the 'Status' column.
                if (cols.size() < 5) continue;

                // Extra safety: Make sure we aren't accidentally parsing a header row
                // if VTOP didn't use a <thead> tag.
                String firstCell = cols.get(0).text().trim().toLowerCase();
                if (firstCell.equals("sl.no.") || firstCell.equals("sno")) continue;

                String rawDate = cols.get(1).text().trim();      // e.g., "31-03-2026"
                String slot = cols.get(2).text().trim();         // e.g., "TC1"
                String dayTimeRaw = cols.get(3).text().trim();   // e.g., "TUE / 11:00-11:50"

                // Status is at index 4
                String status = cols.get(4).text().trim();       // e.g., "Present" or "Absent"

                // Format "31-03-2026" to "31-03" for cleaner UI
                String shortDate = rawDate;
                if (rawDate.contains("-")) {
                    String[] dParts = rawDate.split("-");
                    if (dParts.length >= 2) shortDate = dParts[0] + "-" + dParts[1];
                }

                // Extract Exact Time AND Day
                String exactTime = course.courseSlot;
                String dayOfWeek = "";

                if (dayTimeRaw.contains("/")) {
                    String[] dtParts = dayTimeRaw.split("/");
                    dayOfWeek = dtParts[0].trim();
                    if (dayOfWeek.length() > 1) {
                        dayOfWeek = dayOfWeek.substring(0, 1).toUpperCase() + dayOfWeek.substring(1).toLowerCase();
                    }
                    if (dtParts.length > 1) exactTime = dtParts[1].trim();
                } else if (dayTimeRaw.contains("-")) {
                    exactTime = dayTimeRaw;
                }

                String displayDate = dayOfWeek.isEmpty() ? shortDate : (dayOfWeek + ", " + shortDate);

                course.history.add(new AttendanceModel.AttendanceHistory(displayDate, slot, status, exactTime));
            }

            android.util.Log.d("VTOP_DEBUG", "SUCCESS: Parsed " + course.history.size() + " records for " + course.courseCode);

        } catch (Exception e) {
            android.util.Log.e("VTOP_DEBUG", "Detail Parse Crash for " + course.courseCode, e);
        }
    }
}