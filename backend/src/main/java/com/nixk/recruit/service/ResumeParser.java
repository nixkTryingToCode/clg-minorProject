package com.nixk.recruit.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Extracts text from PDF/DOCX resumes and converts it into structured (reviewable) profile fields. */
@Service
public class ResumeParser {
    public record ParsedResume(String fileName, String text, String name, String email, String phone,
                               String summary, List<String> skills, String education, String experience,
                               String projects, String certifications, double experienceYears) {}

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(\\+?\\d[\\d\\s\\-()]{8,14}\\d)");
    private static final Pattern YEARS = Pattern.compile("(\\d{1,2}(?:\\.\\d)?)\\s*\\+?\\s*(?:years?|yrs?)", Pattern.CASE_INSENSITIVE);

    private static final Map<String, List<String>> SECTION_KEYWORDS = new LinkedHashMap<>();
    static {
        SECTION_KEYWORDS.put("summary", List.of("summary", "professional summary", "objective", "career objective", "profile", "about me", "about"));
        SECTION_KEYWORDS.put("skills", List.of("skills", "technical skills", "key skills", "core competencies", "technologies", "skills and tools"));
        SECTION_KEYWORDS.put("education", List.of("education", "academic background", "academics", "educational qualifications", "education and training"));
        SECTION_KEYWORDS.put("experience", List.of("experience", "work experience", "professional experience", "employment history", "internships", "internship", "work history"));
        SECTION_KEYWORDS.put("projects", List.of("projects", "academic projects", "personal projects", "key projects", "project work"));
        SECTION_KEYWORDS.put("certifications", List.of("certifications", "certificates", "licenses and certifications", "courses", "achievements", "awards", "achievements and certifications"));
    }

    private final SkillCatalog catalog;

    public ResumeParser(SkillCatalog catalog) {
        this.catalog = catalog;
    }

    public ParsedResume parse(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ApiException(400, "Please choose a resume file");
        if (file.getSize() > MAX_BYTES) throw new ApiException(400, "File is too large (max 5 MB)");
        String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("resume");
        String lower = fileName.toLowerCase(Locale.ROOT);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(400, "Could not read the uploaded file");
        }
        String text;
        try {
            if (lower.endsWith(".pdf") && startsWith(bytes, "%PDF")) {
                text = pdfText(bytes);
            } else if (lower.endsWith(".docx") && startsWith(bytes, "PK")) {
                text = docxText(bytes);
            } else {
                throw new ApiException(400, "Only valid PDF or DOCX files are supported");
            }
        } catch (IOException e) {
            throw new ApiException(422, "Could not extract text from this file. Is it a scanned/encrypted document?");
        }
        if (text.isBlank()) throw new ApiException(422, "No readable text found in the resume (scanned image PDF?)");
        return structure(fileName, text);
    }

    private static boolean startsWith(byte[] b, String prefix) {
        if (b.length < prefix.length()) return false;
        for (int i = 0; i < prefix.length(); i++) if (b[i] != (byte) prefix.charAt(i)) return false;
        return true;
    }

    private String pdfText(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private String docxText(byte[] bytes) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            return ex.getText();
        }
    }

    /** Public so it can be unit-tested without files. */
    public ParsedResume structure(String fileName, String rawText) {
        String text = rawText.replace("\r", "").replaceAll("[ \\t]+", " ").trim();
        String[] lines = text.split("\n");

        Map<String, StringBuilder> sections = new LinkedHashMap<>();
        String current = "header";
        sections.put(current, new StringBuilder());
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            String heading = headingOf(t);
            if (heading != null) {
                current = heading;
                sections.computeIfAbsent(current, k -> new StringBuilder());
                continue;
            }
            sections.computeIfAbsent(current, k -> new StringBuilder()).append(t).append('\n');
        }

        Matcher em = EMAIL.matcher(text);
        String email = em.find() ? em.group() : "";
        Matcher ph = PHONE.matcher(text);
        String phone = ph.find() ? ph.group().trim() : "";

        String name = "";
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (t.split("\\s+").length <= 4 && !t.matches(".*[0-9@].*")) name = t;
            break;
        }

        double years = 0;
        Matcher ym = YEARS.matcher(text);
        while (ym.find()) {
            try {
                years = Math.max(years, Double.parseDouble(ym.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }
        if (years > 40) years = 0;

        List<String> skills = catalog.extract(text);
        return new ParsedResume(fileName, text.length() > 19000 ? text.substring(0, 19000) : text,
                name, email, phone,
                cut(get(sections, "summary"), 1900), skills,
                cut(get(sections, "education"), 4900), cut(get(sections, "experience"), 7900),
                cut(get(sections, "projects"), 7900), cut(get(sections, "certifications"), 2900), years);
    }

    private static String get(Map<String, StringBuilder> m, String k) {
        StringBuilder sb = m.get(k);
        return sb == null ? "" : sb.toString().trim();
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String headingOf(String line) {
        if (line.length() > 45) return null;
        String norm = line.toLowerCase(Locale.ROOT).replaceAll("[^a-z& ]", " ").replace("&", "and").replaceAll("\\s+", " ").trim();
        if (norm.isEmpty()) return null;
        for (var e : SECTION_KEYWORDS.entrySet()) {
            if (e.getValue().contains(norm)) return e.getKey();
        }
        return null;
    }
}
