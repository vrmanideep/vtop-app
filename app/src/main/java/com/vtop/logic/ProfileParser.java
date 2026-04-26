package com.vtop.logic;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.HashMap;
import java.util.Map;

public class ProfileParser {

    public static Map<String, Map<String, String>> parse(String html) {
        Map<String, Map<String, String>> data = new HashMap<>();
        Map<String, String> basic = new HashMap<>();
        Map<String, String> proctor = new HashMap<>();

        // Initialize defaults
        basic.put("name", "-");
        basic.put("regno", "-");
        basic.put("vitemail", "-");
        basic.put("mobile", "-");
        basic.put("program", "-");
        basic.put("school", "-");

        data.put("basic", basic);
        data.put("proctor", proctor);

        if (html == null || html.isEmpty()) return data;

        try {
            Document doc = Jsoup.parse(html);

            // --- 1. Top Card Labels ---
            Elements labels = doc.select("label");
            for (int i = 0; i < labels.size(); i++) {
                String key = labels.get(i).text().toUpperCase().trim();
                if (i + 1 < labels.size()) {
                    String val = labels.get(i + 1).text().trim();
                    if (key.contains("REGISTER NUMBER")) basic.put("regno", val);
                    else if (key.contains("VIT EMAIL") && val.contains("@vitapstudent.ac.in")) basic.put("vitemail", val);
                    else if (key.contains("PROGRAM")) basic.put("program", val);
                    else if (key.contains("SCHOOL NAME")) basic.put("school", val);
                }
            }

            // --- 2. Name Extraction ---
            Elements paragraphs = doc.select("p");
            for (Element p : paragraphs) {
                String style = p.attr("style");
                if (style != null && style.toLowerCase().contains("font-weight: bold") && style.toLowerCase().contains("text-align: center")) {
                    basic.put("name", p.text().trim());
                    break;
                }
            }

            // --- 3. Accordion Table Processing ---
            Elements tables = doc.select("table");
            for (Element table : tables) {
                String fullText = table.text().toLowerCase();
                Elements rows = table.select("tr");

                if (fullText.contains("faculty id")) {
                    for (Element row : rows) {
                        Elements cols = row.select("td");
                        if (cols.size() < 2) continue;
                        String k = cols.get(0).text().toLowerCase().trim();
                        String v = cols.get(1).text().trim();

                        if (k.contains("faculty id")) proctor.put("Faculty ID", v);
                        else if (k.contains("name")) proctor.put("Name", v);
                        else if (k.contains("email")) proctor.put("Email", v);
                        else if (k.contains("mobile")) proctor.put("Mobile", v);
                        else if (k.contains("cabin")) proctor.put("Cabin", v);
                    }
                } else if (fullText.contains("native state") || fullText.contains("blood group")) {
                    for (Element row : rows) {
                        Elements cols = row.select("td");
                        if (cols.size() < 2) continue;
                        String k = cols.get(0).text().toLowerCase().trim();
                        String v = cols.get(1).text().trim();

                        if (k.contains("mobile")) basic.put("mobile", v);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return data;
    }
}