package com.example.demo.plan;

import com.example.demo.jeju.JejuPlace;
import com.example.demo.jeju.JejuPlaceRepository;
import com.example.demo.plan.dto.ScheduleDto;
import com.example.demo.wishlist.Wishlist;
import com.example.demo.wishlist.WishlistRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

// place id("p" + JejuPlace.id, "w" + Wishlist.id)가 DB의 실제 PK를 그대로 담고
// 있으므로, 이전 턴 일정의 장소가 이번 턴 후보(placeIdMap/wishlistIdMap)에 없을
// 때도 findById 하나면 바로 찾을 수 있다 — id 접두사(p/w)로 어느 테이블인지 알고,
// 뒤의 숫자가 곧 PK이기 때문이다. id가 매 턴 1부터 다시 매겨지는 임시 번호였을
// 때는 이름+좌표로 퍼지 매칭해서 새 id를 발급해야 했지만, 이제는 그럴 필요가
// 없다(#40 리팩터링 — 원래 방식이 단계가 많고 매칭 실패 여지가 있어 번거롭다는
// 피드백 반영).
@Service
public class CurrentScheduleMerger {

  private final JejuPlaceRepository jejuPlaceRepository;
  private final WishlistRepository wishlistRepository;

  public CurrentScheduleMerger(
      JejuPlaceRepository jejuPlaceRepository, WishlistRepository wishlistRepository) {
    this.jejuPlaceRepository = jejuPlaceRepository;
    this.wishlistRepository = wishlistRepository;
  }

  public String mergeAndBuildSection(
      ScheduleDto currentSchedule,
      Map<String, JejuPlace> placeIdMap,
      Map<String, Wishlist> wishlistIdMap) {
    if (currentSchedule == null || currentSchedule.days() == null) {
      return "";
    }

    StringBuilder section = new StringBuilder();
    for (ScheduleDto.DayDto day : currentSchedule.days()) {
      if (day.places() == null || day.places().isEmpty()) {
        continue;
      }

      List<String> entries = new ArrayList<>();
      for (ScheduleDto.PlaceDto place : day.places()) {
        if (resolveOrFetch(place.id(), placeIdMap, wishlistIdMap)) {
          // "오후부터는 바꿔줘" 같은 시간대 기준 부분 교체 요청을 AI가
          // 판단할 수 있으려면 시간 정보가 필요하다 — 시간이 없으면
          // (드물게 recommendedTime이 비어있는 경우) 이름만 표기한다.
          String time = place.recommendedTime();
          String timeSuffix = (time != null && !time.isBlank()) ? " (" + time + ")" : "";
          entries.add("[" + place.id() + "] " + place.name() + timeSuffix);
        }
      }
      if (!entries.isEmpty()) {
        section.append(day.day()).append("일차: ").append(String.join(", ", entries)).append("\n");
      }
    }

    if (section.isEmpty()) {
      return "";
    }
    return "[현재 일정 - 사용자가 이미 확인한 일정입니다]\n" + section;
  }

  // id가 이번 턴 후보에 이미 있으면 그대로 두고, 없으면 DB에서 직접 findById로
  // 가져와 채워 넣는다. id 자체가 실제 PK라서 이름/좌표 매칭이 필요 없다.
  private boolean resolveOrFetch(
      String id, Map<String, JejuPlace> placeIdMap, Map<String, Wishlist> wishlistIdMap) {
    if (id == null || id.length() < 2) {
      return false;
    }
    if (placeIdMap.containsKey(id) || wishlistIdMap.containsKey(id)) {
      return true;
    }

    Long dbId = parseDbId(id);
    if (dbId == null) {
      return false;
    }

    if (id.charAt(0) == 'p') {
      return jejuPlaceRepository
          .findById(dbId)
          .map(
              p -> {
                placeIdMap.put(id, p);
                return true;
              })
          .orElse(false);
    }
    if (id.charAt(0) == 'w') {
      return wishlistRepository
          .findById(dbId)
          .map(
              w -> {
                wishlistIdMap.put(id, w);
                return true;
              })
          .orElse(false);
    }
    return false;
  }

  private Long parseDbId(String id) {
    try {
      return Long.parseLong(id.substring(1));
    } catch (NumberFormatException e) {
      // 삭제된 장소를 가리키던 id이거나, 형식이 안 맞으면 조용히 스킵
      return null;
    }
  }
}
