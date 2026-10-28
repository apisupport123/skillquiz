package com.swami.skillquiz.service;

/** Turns a browser User-Agent into something readable like "Android, Chrome" or "iPhone, WhatsApp". */
public final class DeviceInfo {

    private DeviceInfo() {}

    public static String describe(String ua) {
        if (ua == null || ua.isBlank()) return "Unknown";
        String u = ua.toLowerCase();

        String os;
        if (u.contains("iphone")) os = "iPhone";
        else if (u.contains("ipad")) os = "iPad";
        else if (u.contains("android")) os = u.contains("mobile") ? "Android" : "Android tablet";
        else if (u.contains("windows")) os = "Windows";
        else if (u.contains("mac os") || u.contains("macintosh")) os = "Mac";
        else if (u.contains("cros")) os = "Chromebook";
        else if (u.contains("linux")) os = "Linux";
        else os = "Other";

        String browser;
        if (u.contains("whatsapp")) browser = "WhatsApp";
        else if (u.contains("instagram")) browser = "Instagram";
        else if (u.contains("fban") || u.contains("fbav")) browser = "Facebook";
        else if (u.contains("telegram")) browser = "Telegram";
        else if (u.contains("linkedinapp")) browser = "LinkedIn";
        else if (u.contains("samsungbrowser")) browser = "Samsung Internet";
        else if (u.contains("edg/") || u.contains("edga/") || u.contains("edgios/")) browser = "Edge";
        else if (u.contains("opr/") || u.contains("opera")) browser = "Opera";
        else if (u.contains("firefox/") || u.contains("fxios/")) browser = "Firefox";
        else if (u.contains("crios/") || u.contains("chrome/")) browser = u.contains("; wv)") ? "In-app browser" : "Chrome";
        else if (u.contains("safari/")) browser = "Safari";
        else browser = "Browser";

        String out = os + ", " + browser;
        return out.length() > 60 ? out.substring(0, 60) : out;
    }
}
