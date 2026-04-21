package com.vtop.logic;

import com.vtop.models.CourseSession;
import com.vtop.models.TimetableModel;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TimetableParser {

    private static final String TAG = "SYNC_PARSER";

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    public static TimetableModel parse(String html) {
        TimetableModel timetable = new TimetableModel();

        String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        for (String day : days) {
            timetable.scheduleMap.put(day, new ArrayList<>());
        }

        if (html == null || html.isEmpty()) return timetable;

        try {
            Document doc = Jsoup.parse(html);

            // 1. Build course info lookup (classId + type stored per entry)
            List<CourseInfo> courseInfoList = getCourseInfo(doc);

            // 2. Locate timetable grid
            Element timeTableGrid = doc.getElementById("timeTableStyle");
            if (timeTableGrid == null) {
                Log.e(TAG, "timeTableStyle element not found");
                return timetable;
            }

            Elements allRows = timeTableGrid.select("tr");
            if (allRows.size() < 4) return timetable;

            // 3. Extract real server timing headers
            // Row 0 = theory start, Row 1 = theory end
            // Row 2 = lab start,    Row 3 = lab end
            List<String> theoryTimings = buildTimings(allRows.get(0), allRows.get(1));
            List<String> labTimings    = buildTimings(allRows.get(2), allRows.get(3));

            Map<String, String> dayMapping = new HashMap<>();
            dayMapping.put("MON", "Monday");   dayMapping.put("TUE", "Tuesday");
            dayMapping.put("WED", "Wednesday"); dayMapping.put("THU", "Thursday");
            dayMapping.put("FRI", "Friday");    dayMapping.put("SAT", "Saturday");
            dayMapping.put("SUN", "Sunday");

            // 4. Parse each day (theory row + lab row pairs, starting at row 4)
            for (int i = 4; i < allRows.size(); i += 2) {
                Element theoryRow = allRows.get(i);
                Elements theoryCells = theoryRow.select("td");
                if (theoryCells.isEmpty()) continue;

                String dayAbbr = theoryCells.get(0).text().trim();
                String dayName = dayMapping.getOrDefault(dayAbbr, dayAbbr);
                if (!timetable.scheduleMap.containsKey(dayName)) continue;

                // Theory slots (cells start at index 2)
                parseSlots(theoryCells, 2, theoryTimings, dayName, timetable, courseInfoList);

                // Lab slots (cells start at index 1, no day-label cell)
                if (i + 1 < allRows.size()) {
                    Elements labCells = allRows.get(i + 1).select("td");
                    parseSlots(labCells, 1, labTimings, dayName, timetable, courseInfoList);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing timetable: " + e.getMessage());
            e.printStackTrace();
        }

        return timetable;
    }

    // -------------------------------------------------------------------------
    // Build timing list from a start-row / end-row pair
    // Row structure: [label, "Start"/"End", time1, time2, ...]
    // Start row cells indexed from 0: [0]=label(rowspan), [1]="Start", [2]=first time
    // End   row cells indexed from 0: [0]="End",           [1]=first end time
    // So for index i in starts (i>=2), the matching end is at (i-1) in ends.
    // -------------------------------------------------------------------------

    private static List<String> buildTimings(Element startRow, Element endRow) {
        List<String> timings = new ArrayList<>();
        Elements starts = startRow.select("td");
        Elements ends   = endRow.select("td");

        for (int i = 2; i < starts.size(); i++) {
            String start = starts.get(i).text().trim();
            // end row has no leading label cell, so end index = i - 1
            String end = (i - 1 < ends.size()) ? ends.get(i - 1).text().trim() : "";
            if (start.equals("Lunch")) {
                timings.add("Lunch");
            } else {
                timings.add(start + " - " + end);
            }
        }
        return timings;
    }

    // -------------------------------------------------------------------------
    // Parse one row of slots into the timetable
    // -------------------------------------------------------------------------

    private static void parseSlots(
            Elements cells,
            int startCellIndex,
            List<String> timings,
            String dayName,
            TimetableModel timetable,
            List<CourseInfo> courseInfoList
    ) {
        int gridIndex = 0;
        for (int j = startCellIndex; j < cells.size(); j++) {
            Element cell    = cells.get(j);
            String cellText = cell.text().trim();
            int colspan     = cell.hasAttr("colspan") ? Integer.parseInt(cell.attr("colspan")) : 1;

            if (isValidCourseString(cellText) && gridIndex < timings.size()) {
                String timeSlot = timings.get(gridIndex);

                // Merge time across colspan (e.g. a 2-hour theory block)
                if (colspan > 1) {
                    int endIndex = gridIndex + colspan - 1;
                    if (endIndex < timings.size()) {
                        try {
                            String startStr = timeSlot.split(" - ")[0].trim();
                            String endStr   = timings.get(endIndex).split(" - ")[1].trim();
                            timeSlot = startStr + " - " + endStr;
                        } catch (Exception ignored) {}
                    }
                }

                CourseSession session = resolveCourseData(cellText, timeSlot, courseInfoList);
                timetable.scheduleMap.get(dayName).add(session);
            }

            gridIndex += colspan;
        }
    }

    // -------------------------------------------------------------------------
    // Validity check for a grid cell
    // -------------------------------------------------------------------------

    private static boolean isValidCourseString(String text) {
        return !text.equals("-")
                && !text.equals("--")
                && !text.equals("Lunch")
                && text.length() >= 8
                && text.contains("-");
    }

    // -------------------------------------------------------------------------
    // Course info — flat list, one entry per registration row
    // Stores courseType properly so Pass 2 matching works
    // -------------------------------------------------------------------------

    private static class CourseInfo {
        String courseCode;
        String courseName;
        String courseType;   // ETH / ELA / TH / LO / PJ / EPJ / ELA / SS
        String slot;
        String venue;
        String faculty;
        String classId;

        CourseInfo(String courseCode, String courseName, String courseType,
                   String slot, String venue, String faculty, String classId) {
            this.courseCode = courseCode;
            this.courseName = courseName;
            this.courseType = courseType;
            this.slot       = slot;
            this.venue      = venue;
            this.faculty    = faculty;
            this.classId    = classId;
        }
    }

    private static List<CourseInfo> getCourseInfo(Document doc) {
        List<CourseInfo> list = new ArrayList<>();
        // CHANGE THIS:
// Elements rows = doc.select("tr");

// TO THIS:
        Element infoTable = doc.selectFirst("#studentDetailsList table");
        if (infoTable == null) return list;
        Elements rows = infoTable.select("tr");

        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() < 10) continue;

            try {
                // Column 2: first <p> = "COURSECODE - Course Name"
                Element courseP = cells.get(2).selectFirst("p");
                if (courseP == null) continue;

                String fullCourseStr = courseP.text().trim();
                String[] cParts = fullCourseStr.split(" - ", 2);
                if (cParts.length < 2) continue;

                String courseCode = cParts[0].trim();
                String courseName = cParts[1].trim();

                // Column 2: second <p> = "( Embedded Theory )" etc.
                String parsedType = "TH";
                Elements courseParagraphs = cells.get(2).select("p");
                if (courseParagraphs.size() >= 2) {
                    String rawType = courseParagraphs.get(1).text().toUpperCase();
                    if      (rawType.contains("EMBEDDED THEORY"))  parsedType = "ETH";
                    else if (rawType.contains("EMBEDDED LAB"))     parsedType = "ELA";
                    else if (rawType.contains("EMBEDDED PROJECT")) parsedType = "EPJ";
                    else if (rawType.contains("THEORY"))           parsedType = "TH";
                    else if (rawType.contains("LAB"))              parsedType = "LO";
                }

                // Column 6: Class number (AP2025264...)
// Use .ownText() or select the first <p> to avoid hidden text
                String classId = "N/A";
                Element classIdCell = cells.get(6);
                Element pTag = classIdCell.selectFirst("p");

                // Null check prevents the "failed" crash [cite: 15]
                if (pTag != null) {
                    classId = pTag.text().trim();
                } else {
                    classId = classIdCell.text().trim();
                }

                // Column 7: slot + venue
                String slot = "N/A", venue = "N/A";
                Elements venuePs = cells.get(7).select("p");
                if (venuePs.size() >= 2) {
                    slot  = venuePs.get(0).text().replace("-", "").trim();
                    venue = venuePs.get(1).text().trim();
                } else {
                    String venueText = cells.get(7).text();
                    if (venueText.contains("-")) {
                        String[] vParts = venueText.split("-", 2);
                        slot  = vParts[0].trim();
                        venue = vParts[1].trim();
                    }
                }

                // Column 8: faculty
                String faculty = "N/A";
                Elements facultyPs = cells.get(8).select("p");
                if (!facultyPs.isEmpty()) {
                    faculty = facultyPs.get(0).text().split("-")[0].trim();
                }

                Log.d(TAG, "Parsed Summary Table -> Course: " + courseCode + " | ClassID: " + classId);
                list.add(new CourseInfo(courseCode, courseName, parsedType,
                        slot, venue, faculty, classId));
            } catch (Exception ignored) {}
        }
        return list;
    }

    // -------------------------------------------------------------------------
    // Resolve a grid cell string to a full CourseSession
    //
    // Grid cell format: SLOT-COURSECODE-TYPE-BUILDING-ROOM-GROUP
    // e.g. "D1-CSE2007-ETH-526-CB-ALL"
    //
    // Three-pass lookup:
    //   Pass 1 — code + venue match (most precise)
    //   Pass 2 — code + type match  (handles same course, different components)
    //   Pass 3 — code only          (last resort, avoids "Unknown")
    // -------------------------------------------------------------------------

    private static CourseSession resolveCourseData(
            String gridText,
            String timeSlot,
            List<CourseInfo> courseInfoList
    ) {
        String[] parts = gridText.split("-");

        // Basic validation of the grid string format
        if (parts.length < 5) {
            return new CourseSession("Unknown", "N/A", "N/A", "N/A",
                    gridText, "N/A", timeSlot, gridText, "N/A");
        }

        String actualSlot    = parts[0].trim();
        String courseCode    = parts[1].trim();
        String courseType    = parts[2].trim();
        String expectedVenue = parts[3].trim() + "-" + parts[4].trim();
        String cleanText     = actualSlot + " • " + courseCode + " • "
                + expectedVenue.replace("-", " ");

        // --- Pass 1: course code + venue (Most precise match) ---
        for (CourseInfo info : courseInfoList) {
            if (!info.courseCode.equals(courseCode)) continue;

            String cleanGridVenue = expectedVenue.replace(" ", "");
            String cleanInfoVenue = info.venue.replace(" ", "");

            if (cleanGridVenue.equalsIgnoreCase(cleanInfoVenue)) {
                Log.d(TAG, "[MATCH SUCCESS] Grid Slot: " + actualSlot + " matched ClassID: " + info.classId + " via Venue");
                return new CourseSession(info.courseName, courseCode, courseType,
                        actualSlot, info.venue, info.faculty, timeSlot, cleanText, info.classId);
            }
        }

        // --- Pass 2: course code + course type (Handles same course, different components) ---
        for (CourseInfo info : courseInfoList) {
            if (!info.courseCode.equals(courseCode)) continue;
            if (info.courseType.equalsIgnoreCase(courseType)) {
                Log.d(TAG, "[MATCH SUCCESS] Grid Slot: " + actualSlot + " matched ClassID: " + info.classId + " via Type");
                return new CourseSession(info.courseName, courseCode, courseType,
                        actualSlot, expectedVenue, info.faculty, timeSlot, cleanText, info.classId);
            }
        }

        // --- Pass 3: course code only (Last resort to avoid "Unknown") ---
        for (CourseInfo info : courseInfoList) {
            if (info.courseCode.equals(courseCode)) {
                Log.d(TAG, "[MATCH SUCCESS] Grid Slot: " + actualSlot + " matched ClassID: " + info.classId + " via Code Only");
                return new CourseSession(info.courseName, courseCode, courseType,
                        actualSlot, expectedVenue, info.faculty, timeSlot, cleanText, info.classId);
            }
        }

        // --- Final Fallback: If absolutely no match is found ---
        Log.w(TAG, "[MATCH FAILED] Could not find ClassID for Grid Text: " + gridText);
        return new CourseSession("Unknown", courseCode, courseType,
                actualSlot, expectedVenue, "N/A", timeSlot, cleanText, "N/A");
    }
}