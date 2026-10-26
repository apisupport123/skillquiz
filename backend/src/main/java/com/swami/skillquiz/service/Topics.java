package com.swami.skillquiz.service;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns whatever the employee typed into one pool key, so "JAVA", "java " and "Java"
 * share the same question pool, and "js" / "JavaScript" do too.
 */
public final class Topics {

    private Topics() {}

    /** letters, digits, spaces and a few symbols used in tech names (C#, C++, .NET, CI/CD, Node.js) */
    private static final Pattern ALLOWED = Pattern.compile("^[\\p{L}\\p{N} .#+/&()-]{1,40}$");

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("js", "javascript"),
            Map.entry("ts", "typescript"),
            Map.entry("py", "python"),
            Map.entry("python3", "python"),
            Map.entry("golang", "go"),
            Map.entry("k8s", "kubernetes"),
            Map.entry("c sharp", "c#"),
            Map.entry("csharp", "c#"),
            Map.entry("cpp", "c++"),
            Map.entry("springboot", "spring boot"),
            Map.entry("spring-boot", "spring boot"),
            Map.entry("genai", "generative ai"),
            Map.entry("gen ai", "generative ai"),
            Map.entry("gen-ai", "generative ai"),
            Map.entry("ml", "machine learning"),
            Map.entry("ai", "artificial intelligence"),
            Map.entry("dl", "deep learning"),
            Map.entry("nlp", "natural language processing"),
            Map.entry("llm", "large language models"),
            Map.entry("llms", "large language models"),
            Map.entry("node", "node.js"),
            Map.entry("nodejs", "node.js"),
            Map.entry("reactjs", "react"),
            Map.entry("react.js", "react"),
            Map.entry("angularjs", "angular"),
            Map.entry("vuejs", "vue"),
            Map.entry("vue.js", "vue"),
            Map.entry("dotnet", ".net"),
            Map.entry("asp.net", ".net"),
            Map.entry("postgres", "postgresql"),
            Map.entry("mongo", "mongodb"),
            Map.entry("aws cloud", "aws"),
            Map.entry("amazon web services", "aws"),
            Map.entry("gcp", "google cloud"),
            Map.entry("devops", "devops"),
            Map.entry("ci/cd", "ci cd"),
            Map.entry("cicd", "ci cd"),
            Map.entry("dsa", "data structures and algorithms"),
            Map.entry("oops", "object oriented programming"),
            Map.entry("oop", "object oriented programming"));

    public static String clean(String input) {
        if (input == null) throw new ApiException(400, "Topic daalo (jaise Java, Python, GenAI).");
        String t = input.trim().replaceAll("\\s+", " ");
        if (t.length() < 1 || !ALLOWED.matcher(t).matches()) {
            throw new ApiException(400, "Topic mein sirf letters/numbers (aur . # + / -) allowed hain, max 40 characters.");
        }
        return t;
    }

    public static String key(String cleaned) {
        String k = cleaned.toLowerCase();
        return ALIASES.getOrDefault(k, k);
    }

    private static final java.util.Set<String> ACRONYMS = java.util.Set.of(
            "php", "sql", "aws", "html", "css", "xml", "api", "apis", "gcp", "nlp", "ml", "ai", "ios",
            "sap", "jsp", "etl", "ui", "ux", "qa", "seo", "iot", "rpa", "jvm", "rest", "json", "ci",
            "cd", "sre", "erp", "crm", "bi", "it", "hr", ".net", "mvc", "orm", "jpa", "dbms", "os");

    /**
     * Nice display label. If the user typed capitals ("GenAI"), keep it.
     * Otherwise build from the pool key: "spring boot" -> "Spring Boot", "php" -> "PHP".
     */
    public static String label(String cleaned) {
        String key = key(cleaned);
        boolean aliased = !key.equals(cleaned.toLowerCase());
        if (!aliased && !cleaned.equals(cleaned.toLowerCase())) return cleaned;
        StringBuilder sb = new StringBuilder();
        for (String w : key.split(" ")) {
            if (sb.length() > 0) sb.append(' ');
            if (ACRONYMS.contains(w)) sb.append(w.toUpperCase());
            else if (w.equals("and") || w.equals("of")) sb.append(w);
            else sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    /** for duplicate detection: lowercase letters+digits only */
    public static String fingerprint(String questionText) {
        String f = questionText.toLowerCase().replaceAll("[^\\p{L}\\p{N}]", "");
        return f.length() > 300 ? f.substring(0, 300) : f;
    }
}
