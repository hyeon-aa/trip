package com.example.demo.jeju;

public class JejuPlaceUtil {
    public static String getRegion(double lat, double lng) {
        if (lng > 126.8) return "동부";
        if (lng < 126.3) return "서부";
        if (lat < 33.3)  return "남부";
        return "제주시";
    }
}