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

    // Entry point
    public static TimetableModel parse(String html) {
        TimetableModel timetable = new TimetableModel();

        String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        for (String day : days) {
            timetable.scheduleMap.put(day, new ArrayList<>());
        }

        if (html == null || html.isEmpty()) return timetable;

        try {
            Document doc = Jsoup.parse(html);

            // 1. Build course info lookup
            List<CourseInfo> courseInfoList = getCourseInfo(doc);

            // Populate the courseStartDates map with the binary compKey
            for (CourseInfo info : courseInfoList) {
                if (info.attendanceDate != null && !info.attendanceDate.isEmpty()) {
                    timetable.courseStartDates.put(info.courseCode + "|" + info.compKey, info.attendanceDate);
                }
            }

            // 2. Locate timetable grid
            Element timeTableGrid = doc.getElementById("timeTableStyle");
            if (timeTableGrid == null) {
                Log.e(TAG, "timeTableStyle element not found");
                return timetable;
            }

            Elements allRows = timeTableGrid.select("tr");
            if (allRows.size() < 4) return timetable;

            // 3. Extract real server timing headers
            List<String> theoryTimings = buildTimings(allRows.get(0), allRows.get(1));
            List<String> labTimings    = buildTimings(allRows.get(2), allRows.get(3));

            Map<String, String> dayMapping = new HashMap<>();
            dayMapping.put("MON", "Monday");   dayMapping.put("TUE", "Tuesday");
            dayMapping.put("WED", "Wednesday"); dayMapping.put("THU", "Thursday");
            dayMapping.put("FRI", "Friday");    dayMapping.put("SAT", "Saturday");
            dayMapping.put("SUN", "Sunday");

            // 4. Parse each day
            for (int i = 4; i < allRows.size(); i += 2) {
                Element theoryRow = allRows.get(i);
                Elements theoryCells = theoryRow.select("td");
                if (theoryCells.isEmpty()) continue;

                String dayAbbr = theoryCells.get(0).text().trim();
                String dayName = dayMapping.getOrDefault(dayAbbr, dayAbbr);
                if (!timetable.scheduleMap.containsKey(dayName)) continue;

                parseSlots(theoryCells, 2, theoryTimings, dayName, timetable, courseInfoList);

                if (i + 1 < allRows.size()) {
                    Elements labCells = allRows.get(i + 1).select("td");
                    parseSlots(labCells, 1, labTimings, dayName, timetable, courseInfoList);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing timetable", e);
        }

        return timetable;
    }

    // -------------------------------------------------------------------------
    // Build timing list
    // -------------------------------------------------------------------------

    private static List<String> buildTimings(Element startRow, Element endRow) {
        List<String> timings = new ArrayList<>();
        Elements starts = startRow.select("td");
        Elements ends   = endRow.select("td");

        for (int i = 2; i < starts.size(); i++) {
            String start = starts.get(i).text().trim();
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
    // Parse one row of slots
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
            int colSpan     = cell.hasAttr("colspan") ? Integer.parseInt(cell.attr("colspan")) : 1;

            if (isValidCourseString(cellText) && gridIndex < timings.size()) {
                String timeSlot = calculateTimeSlot(timings, gridIndex, colSpan);

                CourseSession session = resolveCourseData(cellText, timeSlot, courseInfoList);

                List<CourseSession> dayList = timetable.scheduleMap.get(dayName);
                if (dayList != null) {
                    dayList.add(session);
                }
            }

            gridIndex += colSpan;
        }
    }

    private static String calculateTimeSlot(List<String> timings, int gridIndex, int colSpan) {
        String timeSlot = timings.get(gridIndex);
        if (colSpan > 1) {
            int endIndex = gridIndex + colSpan - 1;
            if (endIndex < timings.size()) {
                try {
                    String startStr = timeSlot.split(" - ")[0].trim();
                    String endStr   = timings.get(endIndex).split(" - ")[1].trim();
                    timeSlot = startStr + " - " + endStr;
                } catch (Exception ignored) {}
            }
        }
        return timeSlot;
    }

    private static boolean isValidCourseString(String text) {
        return !text.equals("-")
                && !text.equals("--")
                && !text.equals("Lunch")
                && text.length() >= 8
                && text.contains("-");
    }

    // -------------------------------------------------------------------------
    // Course info class
    // -------------------------------------------------------------------------

    private static class CourseInfo {
        String courseCode;
        String courseName;
        String courseType;
        String slot;
        String venue;
        String faculty;
        String classId;
        String attendanceDate;
        String compKey;

        CourseInfo(String courseCode, String courseName, String courseType,
                   String slot, String venue, String faculty, String classId,
                   String attendanceDate, String compKey) {
            this.courseCode = courseCode;
            this.courseName = courseName;
            this.courseType = courseType;
            this.slot       = slot;
            this.venue      = venue;
            this.faculty    = faculty;
            this.classId    = classId;
            this.attendanceDate = attendanceDate;
            this.compKey  = compKey;
        }
    }

    private static List<CourseInfo> getCourseInfo(Document doc) {
        List<CourseInfo> list = new ArrayList<>();
        Element infoTable = doc.selectFirst("#studentDetailsList table");
        if (infoTable == null) return list;
        Elements rows = infoTable.select("tr");

        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() < 10) continue;

            try {
                Element courseP = cells.get(2).selectFirst("p");
                if (courseP == null) continue;

                String fullCourseStr = courseP.text().trim();
                String[] cParts = fullCourseStr.split(" - ", 2);
                if (cParts.length < 2) continue;

                String courseCode = cParts[0].trim();
                String courseName = cParts[1].trim();

                String parsedType = "TH";
                String rawCourseType;

                Elements courseParagraphs = cells.get(2).select("p");
                if (courseParagraphs.size() >= 2) {
                    rawCourseType = courseParagraphs.get(1).text();
                    String rawType = rawCourseType.toUpperCase();
                    if      (rawType.contains("EMBEDDED THEORY"))  parsedType = "ETH";
                    else if (rawType.contains("EMBEDDED LAB"))     parsedType = "ELA";
                    else if (rawType.contains("EMBEDDED PROJECT")) parsedType = "EPJ";
                    else if (rawType.contains("THEORY"))           parsedType = "TH";
                    else if (rawType.contains("LAB"))              parsedType = "LO";
                }

                // Determine binary component key
                boolean isLabComp = parsedType.contains("L") || parsedType.contains("P");
                String compKey = isLabComp ? "LAB" : "THEORY";

                String classId;
                Element classIdCell = cells.get(6);
                Element pTag = classIdCell.selectFirst("p");
                classId = (pTag != null) ? pTag.text().trim() : classIdCell.text().trim();

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

                String faculty = "N/A";
                Elements facultyPs = cells.get(8).select("p");
                if (!facultyPs.isEmpty()) {
                    faculty = facultyPs.get(0).text().split("-")[0].trim();
                }

                String attendanceDate = null;
                if (cells.size() >= 11) {
                    Element dateSpan = cells.get(10).selectFirst("span");
                    if (dateSpan != null) {
                        attendanceDate = dateSpan.text().trim();
                    } else {
                        String fullDateText = cells.get(10).text();
                        if (fullDateText.contains("- ")) {
                            attendanceDate = fullDateText.split(" - ")[0].trim();
                        } else {
                            attendanceDate = fullDateText.trim();
                        }
                    }
                }

                list.add(new CourseInfo(courseCode, courseName, parsedType,
                        slot, venue, faculty, classId, attendanceDate, compKey));
            } catch (Exception ignored) {}
        }
        return list;
    }

    // -------------------------------------------------------------------------
    // Resolve a grid cell string to a full CourseSession
    // -------------------------------------------------------------------------

    private static CourseSession resolveCourseData(
            String gridText,
            String timeSlot,
            List<CourseInfo> courseInfoList
    ) {
        String[] parts = gridText.split("-");

        if (parts.length < 5) {
            return new CourseSession("Unknown", "N/A", "N/A", "N/A",
                    gridText, "N/A", timeSlot, gridText, "N/A");
        }

        String actualSlot    = parts[0].trim();
        String courseCode    = parts[1].trim();
        String courseType    = parts[2].trim();

        StringBuilder venueBuilder = new StringBuilder();
        for (int i = 3; i < parts.length - 1; i++) {
            venueBuilder.append(parts[i].trim());
            if (i < parts.length - 2) {
                venueBuilder.append("-");
            }
        }
        String expectedVenue = venueBuilder.toString();

        String cleanText     = actualSlot + " • " + courseCode + " • "
                + expectedVenue.replace("-", " ");

        for (CourseInfo info : courseInfoList) {
            if (!info.courseCode.equals(courseCode)) continue;

            String cleanGridVenue = expectedVenue.replace(" ", "");
            String cleanInfoVenue = info.venue.replace(" ", "");

            if (cleanGridVenue.equalsIgnoreCase(cleanInfoVenue)) {
                return new CourseSession(info.courseName, courseCode, courseType,
                        actualSlot, info.venue, info.faculty, timeSlot, cleanText, info.classId);
            }
        }

        for (CourseInfo info : courseInfoList) {
            if (!info.courseCode.equals(courseCode)) continue;
            if (info.courseType.equalsIgnoreCase(courseType)) {
                return new CourseSession(info.courseName, courseCode, courseType,
                        actualSlot, expectedVenue, info.faculty, timeSlot, cleanText, info.classId);
            }
        }

        for (CourseInfo info : courseInfoList) {
            if (info.courseCode.equals(courseCode)) {
                return new CourseSession(info.courseName, courseCode, courseType,
                        actualSlot, expectedVenue, info.faculty, timeSlot, cleanText, info.classId);
            }
        }

        Log.w(TAG, "[MATCH FAILED] Could not find ClassID for Grid Text: " + gridText);
        return new CourseSession("Unknown", courseCode, courseType,
                actualSlot, expectedVenue, "N/A", timeSlot, cleanText, "N/A");
    }
}