package com.example.demo.jeju;

public class JejuPlaceUtil {
    public static String getRegion(double lat, double lng) {
        if (lng > 126.8) return "동부";
        if (lng < 126.3) return "서부";
        if (lat < 33.3)  return "남부";
        return "제주시";
    }

    // 두 좌표 사이의 실제 지표면 거리(미터). RouteOptimizer의 동선 최적화와
    // plan/PlanEditTools의 위치 지정 삽입(#58) Tool이 같은 공식을 공유한다.
    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}