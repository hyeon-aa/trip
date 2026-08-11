package com.example.demo.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.demo.jeju.JejuPlaceUtil;

import tools.jackson.databind.node.ObjectNode;

@Service
public class RouteOptimizer {

    // startLat/startLng, endLat/endLng: 그날 동선의 시작/종료 앵커(숙소·공항 등). null이면
    // 해당 쪽은 앵커 없이(순수 방문지 간 거리만) 최적화한다.
    public List<ObjectNode> optimalOrder(
        List<ObjectNode> places, Double startLat, Double startLng, Double endLat, Double endLng
    ) {
        if (places.size() <= 1) return new ArrayList<>(places);
        if (places.size() > 8) return nearestNeighborOrder(places, startLat, startLng);

        List<List<ObjectNode>> permutations = new ArrayList<>();
        permute(new ArrayList<>(places), 0, permutations);

        List<ObjectNode> best = null;
        double bestDistance = Double.MAX_VALUE;

        for (List<ObjectNode> perm : permutations) {
            double total = 0;
            for (int i = 0; i < perm.size() - 1; i++) {
                total += JejuPlaceUtil.haversineMeters(
                    perm.get(i).get("lat").asDouble(), perm.get(i).get("lng").asDouble(),
                    perm.get(i + 1).get("lat").asDouble(), perm.get(i + 1).get("lng").asDouble()
                );
            }
            if (startLat != null && startLng != null) {
                ObjectNode first = perm.get(0);
                total += JejuPlaceUtil.haversineMeters(startLat, startLng, first.get("lat").asDouble(), first.get("lng").asDouble());
            }
            if (endLat != null && endLng != null) {
                ObjectNode last = perm.get(perm.size() - 1);
                total += JejuPlaceUtil.haversineMeters(last.get("lat").asDouble(), last.get("lng").asDouble(), endLat, endLng);
            }
            if (total < bestDistance) {
                bestDistance = total;
                best = perm;
            }
        }
        return best;
    }

    private void permute(List<ObjectNode> list, int k, List<List<ObjectNode>> result) {
        if (k == list.size()) {
            result.add(new ArrayList<>(list));
            return;
        }
        for (int i = k; i < list.size(); i++) {
            Collections.swap(list, k, i);
            permute(list, k + 1, result);
            Collections.swap(list, k, i);
        }
    }

    private List<ObjectNode> nearestNeighborOrder(List<ObjectNode> places, Double startLat, Double startLng) {
        if (places.isEmpty()) return new ArrayList<>();
        List<ObjectNode> remaining = new ArrayList<>(places);
        List<ObjectNode> result = new ArrayList<>();

        ObjectNode current;
        if (startLat != null && startLng != null) {
            current = Collections.min(
                remaining,
                Comparator.comparingDouble(p ->
                    JejuPlaceUtil.haversineMeters(startLat, startLng, p.get("lat").asDouble(), p.get("lng").asDouble()))
            );
            remaining.remove(current);
        } else {
            current = remaining.remove(0);
        }
        result.add(current);

        while (!remaining.isEmpty()) {
            ObjectNode nearest = null;
            double minDist = Double.MAX_VALUE;
            double curLat = current.get("lat").asDouble();
            double curLng = current.get("lng").asDouble();

            for (ObjectNode candidate : remaining) {
                double dist = JejuPlaceUtil.haversineMeters(curLat, curLng,
                    candidate.get("lat").asDouble(), candidate.get("lng").asDouble());
                if (dist < minDist) {
                    minDist = dist;
                    nearest = candidate;
                }
            }
            result.add(nearest);
            remaining.remove(nearest);
            current = nearest;
        }
        return result;
    }
}
