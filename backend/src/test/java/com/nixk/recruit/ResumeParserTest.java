package com.nixk.recruit;

import static org.junit.jupiter.api.Assertions.*;

import com.nixk.recruit.service.ResumeParser;
import com.nixk.recruit.service.SkillCatalog;
import org.junit.jupiter.api.Test;

class ResumeParserTest {
    private final SkillCatalog catalog = new SkillCatalog();
    private final ResumeParser parser = new ResumeParser(catalog);

    @Test
    void extractsSkillsAndNormalisesAliases() {
        var skills = catalog.extract("Worked with Java, springboot and Postgres. Used Node.js and C++.");
        assertTrue(skills.contains("Java"));
        assertTrue(skills.contains("Spring Boot"));
        assertTrue(skills.contains("PostgreSQL"));
        assertTrue(skills.contains("Node.js"));
        assertTrue(skills.contains("C++"));
        assertFalse(skills.contains("JavaScript"));
    }

    @Test
    void splitsResumeIntoSections() {
        String text = "Aman Verma\naman@example.com\n+91 98765 43210\n\nEducation\nB.Tech CSE, GGSIPU 2023-2027\n\n"
                + "Skills\nJava, SQL\n\nProjects\nLibrary system using Spring Boot\n";
        var r = parser.structure("resume.pdf", text);
        assertEquals("Aman Verma", r.name());
        assertEquals("aman@example.com", r.email());
        assertTrue(r.education().contains("GGSIPU"));
        assertTrue(r.projects().contains("Spring Boot"));
        assertTrue(r.skills().contains("Java"));
    }
}
