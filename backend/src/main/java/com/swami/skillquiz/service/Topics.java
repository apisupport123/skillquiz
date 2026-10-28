package com.swami.skillquiz.service;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns whatever the employee typed into one pool key, so "JAVA", "java " and "Java"
 * share the same question pool, and "js" / "JavaScript" do too.
 */
public final class Topics {

    private Topics() {}

    /** letters (any script, incl. Hindi/Marathi vowel signs), digits, spaces and a few symbols (C#, C++, CI/CD, Class 10) */
    private static final Pattern ALLOWED = Pattern.compile("^[\\p{L}\\p{M}\\p{N} .#+/&()',:-]{1,60}$");

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
            Map.entry("gk", "general knowledge"),
            Map.entry("ga", "general awareness"),
            Map.entry("maths", "mathematics"),
            Map.entry("math", "mathematics"),
            Map.entry("reasoning", "logical reasoning"),
            Map.entry("evs", "environmental studies"),
            Map.entry("ias", "upsc civil services"),
            Map.entry("upsc cse", "upsc civil services"),
            Map.entry("mpsc rajyaseva", "mpsc rajyaseva"),
            Map.entry("oop", "object oriented programming"));

    public static String clean(String input) {
        if (input == null) throw new ApiException(400, "TOPIC_REQUIRED", "Enter an exam, subject or topic.");
        String t = input.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) throw new ApiException(400, "TOPIC_REQUIRED", "Enter an exam, subject or topic.");
        if (!ALLOWED.matcher(t).matches()) {
            throw new ApiException(400, "TOPIC_CHARS", "Use letters and numbers only (and . # + / - ( ) &), up to 60 characters.");
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
            "cd", "sre", "erp", "crm", "bi", "it", "hr", ".net", "mvc", "orm", "jpa", "dbms", "os",
            "upsc", "mpsc", "uppsc", "bpsc", "rpsc", "mppsc", "tnpsc", "kpsc", "appsc", "tspsc", "gpsc", "wbcs",
            "ssc", "cgl", "chsl", "mts", "gd", "ibps", "sbi", "po", "rrb", "ntpc", "nda", "cds", "afcat", "capf",
            "tgt", "pgt", "prt", "ctet", "tet", "htet", "reet", "neet", "jee", "cuet", "gate", "cat", "mat",
            "clat", "net", "ugc", "csir", "cbse", "icse", "hsc", "ias", "ips", "gk", "evs", "lic", "aao");

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
            else if (w.isEmpty()) continue;
            else sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private static final Set<String> STOP = Set.of(
            "a", "an", "the", "of", "in", "on", "to", "is", "are", "was", "be", "and", "or", "for", "with",
            "which", "what", "when", "how", "why", "following", "does", "do", "that", "this", "it", "by",
            "as", "at", "from", "can", "will", "most", "best", "correct", "true", "statement", "about");

    /** meaningful lowercase words of a question, for near-duplicate checks */
    public static Set<String> words(String text) {
        Set<String> out = new java.util.HashSet<>();
        for (String w : text.toLowerCase().split("[^\\p{L}\\p{M}\\p{N}#+.]+")) {
            if (w.length() > 1 && !STOP.contains(w)) out.add(w);
        }
        return out;
    }

    /** Jaccard similarity of two word sets: 1.0 = same words */
    public static double similarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        int common = 0;
        for (String w : a) if (b.contains(w)) common++;
        return common / (double) (a.size() + b.size() - common);
    }

    /** for duplicate detection: lowercase letters+digits only */
    public static String fingerprint(String questionText) {
        String f = questionText.toLowerCase().replaceAll("[^\\p{L}\\p{M}\\p{N}]", "");
        return f.length() > 300 ? f.substring(0, 300) : f;
    }
}
