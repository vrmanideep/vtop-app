package com.vtop.logic

import com.vtop.models.*
import org.jsoup.Jsoup

object CoursePageParser {

    /**
     * Strictly extracts semSubId from classId.
     * Examples:
     *  - AP2026272000416 -> AP2026272 (6-digit suffix stripped)
     *  - AP20232410000123 -> AP20232410
     */
    fun extractSemIdFromClassId(rawClassId: String): String? {
        val cleaned = rawClassId.trim().uppercase()
        if (cleaned.length < 10) return null
        val match = Regex("^([A-Z]{2,3}\\d{6,8}?)\\d{6}$").find(cleaned)
        return match?.groupValues?.get(1) ?: cleaned.take(9)
    }

    fun parseSemesters(html: String): List<SemesterOption> {
        val doc = Jsoup.parse(html)
        return doc.select("select#semesterSubId option").mapNotNull { opt ->
            val id = opt.attr("value").trim()
            val name = opt.text().trim()
            if (id.isNotEmpty() && !name.contains("Choose", ignoreCase = true)) {
                SemesterOption(id = id, name = name)
            } else null
        }
    }

    fun parseCourseOptions(html: String): List<GroupedCourseOption> {
        val doc = Jsoup.parse(html)
        val raw = doc.select("select#courseCode option").mapNotNull { opt ->
            val classId = opt.attr("value").trim()
            val text = opt.text().trim()
            if (classId.isEmpty() || text.contains("Choose", ignoreCase = true)) return@mapNotNull null

            val parts = text.split(" - ").map { it.trim() }
            val code = parts.firstOrNull() ?: text
            val type = if (parts.size >= 3) parts.last() else ""
            val title = if (parts.size >= 3) parts.subList(1, parts.size - 1).joinToString(" - ") else parts.getOrNull(1) ?: ""

            CoursePageOption(
                classId = classId,
                courseCode = code,
                courseTitle = title,
                courseType = type
            )
        }

        return raw.groupBy { it.courseCode }.map { (code, items) ->
            GroupedCourseOption(
                courseCode = code,
                courseTitle = items.first().courseTitle,
                types = items
            )
        }
    }

    fun parseSlotSections(html: String): List<CourseSection> {
        val doc = Jsoup.parse(html)
        val sections = mutableListOf<CourseSection>()
        val onclickRegex = Regex("processViewStudentCourseDetail\\s*\\(\\s*'([^']+)'\\s*,\\s*'([^']+)'\\s*,\\s*'([^']+)'\\s*\\)")

        doc.select("table tr").forEach { row ->
            val tds = row.select("td")
            if (tds.size >= 9) {
                val btn = tds[8].selectFirst("button[onclick]") ?: return@forEach
                val match = onclickRegex.find(btn.attr("onclick")) ?: return@forEach
                val erpId = match.groupValues[2].trim()
                val classId = match.groupValues[3].trim()

                val facultyRaw = tds[7].text().trim()
                val facParts = facultyRaw.split(" - ").map { it.trim() }
                val facultyName = if (facParts.size >= 2) facParts[1] else facultyRaw
                val school = if (facParts.size >= 3) facParts[2] else ""

                sections.add(
                    CourseSection(
                        classGroup = tds[1].text().trim(),
                        courseCode = tds[2].text().trim(),
                        courseTitle = tds[3].text().trim(),
                        courseType = tds[4].text().trim(),
                        classId = classId,
                        slot = tds[6].text().trim().replace("/", "+"),
                        erpId = erpId,
                        facultyName = facultyName,
                        school = school
                    )
                )
            }
        }
        return sections
    }

    fun parseCourseDetail(html: String): CourseDetailData? {
        val doc = Jsoup.parse(html)
        val tables = doc.select("form#CoursePageLectureDetail table.table")
        if (tables.isEmpty()) return null

        val downloadRegex = Regex("vtopDownload\\s*\\(\\s*'([^']+)'\\s*\\)")
        fun extractDownloadPath(href: String): String? = downloadRegex.find(href)?.groupValues?.get(1)

        // 1. Metadata table
        val metaRow = tables.getOrNull(0)?.select("tr")?.getOrNull(1)?.select("td")
        val classGroup = metaRow?.getOrNull(0)?.text()?.trim() ?: ""
        val courseCode = metaRow?.getOrNull(1)?.text()?.trim() ?: ""
        val courseTitle = metaRow?.getOrNull(2)?.text()?.trim() ?: ""
        val courseType = metaRow?.getOrNull(3)?.text()?.trim() ?: ""
        val classId = doc.selectFirst("input#classId")?.attr("value")?.trim()
            ?: metaRow?.getOrNull(4)?.text()?.trim() ?: ""
        val semSubId = doc.selectFirst("input#semesterSubId")?.attr("value")?.trim() ?: ""
        val slot = metaRow?.getOrNull(5)?.text()?.trim() ?: ""
        val facultyRaw = metaRow?.getOrNull(6)?.text()?.trim() ?: ""

        // 2. Bundle links
        val allMaterialsHref = doc.selectFirst("a[href*=allCourseMeterialDownload/1/]")?.attr("href") ?: ""
        val generalMaterialsHref = doc.selectFirst("a[href*=allCourseMeterialDownload/2/]")?.attr("href") ?: ""

        // 3. Syllabus & General reference materials table
        var syllabusPath: String? = null
        val generalMaterials = mutableListOf<CourseMaterialItem>()
        tables.getOrNull(1)?.select("tr")?.forEach { row ->
            val tds = row.select("td")
            if (tds.size >= 2) {
                val label = tds[0].text().trim()
                val link = tds[1].selectFirst("a[href*=vtopDownload]") ?: return@forEach
                val path = extractDownloadPath(link.attr("href")) ?: return@forEach
                val text = link.text().trim()

                if (label.equals("Syllabus", ignoreCase = true)) {
                    syllabusPath = path
                } else {
                    generalMaterials.add(
                        CourseMaterialItem(
                            label = label,
                            relativePath = path,
                            fileNameHint = text.takeIf { !it.equals("Download", true) } ?: label
                        )
                    )
                }
            }
        }

        // 4. Lecture schedule table
        val lectures = mutableListOf<CourseLectureItem>()
        tables.getOrNull(2)?.select("tr")?.drop(1)?.forEach { row ->
            val tds = row.select("td")
            if (tds.size >= 5) {
                val slNo = tds[0].text().trim()
                val dateSpans = tds[1].select("span")
                val date = dateSpans.getOrNull(1)?.text()?.trim()?.removeSurrounding("[", "]")
                    ?: dateSpans.firstOrNull()?.text()?.trim()
                    ?: tds[1].text().trim()
                val day = tds[2].text().trim()
                val topic = tds[3].text().trim()
                val materials = tds[4].select("a[href]").mapNotNull { a ->
                    val href = a.attr("href").trim()
                    val matLabel = a.text().trim().ifEmpty { "Material" }
                    val downloadPath = extractDownloadPath(href)
                    when {
                        downloadPath != null -> CourseMaterialItem(
                            label = matLabel,
                            relativePath = downloadPath,
                            fileNameHint = matLabel
                        )
                        href.startsWith("http://", ignoreCase = true) ||
                                href.startsWith("https://", ignoreCase = true) -> CourseMaterialItem(
                            label = matLabel,
                            relativePath = href,
                            fileNameHint = href,
                            isWebLink = true
                        )
                        else -> null
                    }
                }
                lectures.add(CourseLectureItem(slNo, date, day, topic, materials))
            }
        }

        return CourseDetailData(
            classGroup = classGroup,
            courseCode = courseCode,
            courseTitle = courseTitle,
            courseType = courseType,
            classId = classId,
            semSubId = semSubId,
            slot = slot,
            facultyName = facultyRaw,
            allMaterialsPath = extractDownloadPath(allMaterialsHref),
            generalMaterialsPath = extractDownloadPath(generalMaterialsHref),
            syllabusPath = syllabusPath,
            generalMaterials = generalMaterials,
            lectures = lectures
        )
    }
}