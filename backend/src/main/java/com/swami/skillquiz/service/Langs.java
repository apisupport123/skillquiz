package com.swami.skillquiz.service;

import java.util.LinkedHashMap;
import java.util.Map;

/** Languages a test can be written in. Each topic+level keeps a separate question pool per language. */
public final class Langs {

    private Langs() {}

    public record Lang(String code, String english, String nativeName, String script) {}

    public static final Map<String, Lang> ALL = new LinkedHashMap<>();

    static {
        add("en", "English", "English", "Latin");
        add("hi", "Hindi", "हिन्दी", "Devanagari");
        add("mr", "Marathi", "मराठी", "Devanagari");
        add("gu", "Gujarati", "ગુજરાતી", "Gujarati");
        add("bn", "Bengali", "বাংলা", "Bengali");
        add("ta", "Tamil", "தமிழ்", "Tamil");
        add("te", "Telugu", "తెలుగు", "Telugu");
        add("kn", "Kannada", "ಕನ್ನಡ", "Kannada");
        add("ml", "Malayalam", "മലയാളം", "Malayalam");
        add("pa", "Punjabi", "ਪੰਜਾਬੀ", "Gurmukhi");
        add("or", "Odia", "ଓଡ଼ିଆ", "Odia");
    }

    private static void add(String code, String english, String nativeName, String script) {
        ALL.put(code, new Lang(code, english, nativeName, script));
    }

    /** unknown or missing -> English */
    public static String normalize(String code) {
        if (code == null) return "en";
        String c = code.trim().toLowerCase();
        return ALL.containsKey(c) ? c : "en";
    }

    public static Lang of(String code) {
        return ALL.get(normalize(code));
    }
}
