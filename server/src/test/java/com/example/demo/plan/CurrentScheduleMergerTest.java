package com.example.demo.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.jeju.JejuPlace;
import com.example.demo.jeju.JejuPlaceRepository;
import com.example.demo.plan.dto.ScheduleDto;
import com.example.demo.wishlist.Wishlist;
import com.example.demo.wishlist.WishlistRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CurrentScheduleMergerTest {

  private final JejuPlaceRepository jejuPlaceRepository = mock(JejuPlaceRepository.class);
  private final WishlistRepository wishlistRepository = mock(WishlistRepository.class);
  private final CurrentScheduleMerger merger =
      new CurrentScheduleMerger(jejuPlaceRepository, wishlistRepository);

  private JejuPlace place(long id, String name) {
    JejuPlace p = new JejuPlace();
    p.setId(id);
    p.setName(name);
    return p;
  }

  private Wishlist wishlist(long id, String name) {
    Wishlist w = new Wishlist();
    w.setId(id);
    w.setName(name);
    return w;
  }

  private ScheduleDto.PlaceDto placeDto(String id, String name) {
    return new ScheduleDto.PlaceDto(id, name, "관광지", null, null, null, null, null);
  }

  private ScheduleDto.PlaceDto placeDto(String id, String name, String recommendedTime) {
    return new ScheduleDto.PlaceDto(id, name, "관광지", null, recommendedTime, null, null, null);
  }

  @Test
  void currentSchedule이_없으면_빈_문자열을_반환한다() {
    String result = merger.mergeAndBuildSection(null, new LinkedHashMap<>(), new LinkedHashMap<>());

    assertThat(result).isEmpty();
    verify(jejuPlaceRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void 이번_턴_후보에_이미_있는_id는_DB_조회_없이_그대로_재사용한다() {
    Map<String, JejuPlace> placeIdMap = new LinkedHashMap<>();
    placeIdMap.put("p101", place(101, "성산일출봉"));
    Map<String, Wishlist> wishlistIdMap = new LinkedHashMap<>();

    ScheduleDto schedule =
        new ScheduleDto(List.of(new ScheduleDto.DayDto(1, List.of(placeDto("p101", "성산일출봉")))));

    String result = merger.mergeAndBuildSection(schedule, placeIdMap, wishlistIdMap);

    assertThat(result).isEqualTo("[현재 일정 - 사용자가 이미 확인한 일정입니다]\n1일차: [p101] 성산일출봉\n");
    assertThat(placeIdMap).hasSize(1);
    verify(jejuPlaceRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void 이번_턴_후보에_없는_p_id는_findById로_바로_가져온다() {
    Map<String, JejuPlace> placeIdMap = new LinkedHashMap<>();
    Map<String, Wishlist> wishlistIdMap = new LinkedHashMap<>();
    when(jejuPlaceRepository.findById(102L)).thenReturn(Optional.of(place(102, "우도")));

    ScheduleDto schedule =
        new ScheduleDto(List.of(new ScheduleDto.DayDto(2, List.of(placeDto("p102", "우도")))));

    String result = merger.mergeAndBuildSection(schedule, placeIdMap, wishlistIdMap);

    assertThat(result).isEqualTo("[현재 일정 - 사용자가 이미 확인한 일정입니다]\n2일차: [p102] 우도\n");
    assertThat(placeIdMap).containsOnlyKeys("p102");
    assertThat(placeIdMap.get("p102").getName()).isEqualTo("우도");
  }

  @Test
  void 이번_턴_후보에_없는_w_id는_wishlistRepository_findById로_가져온다() {
    Map<String, JejuPlace> placeIdMap = new LinkedHashMap<>();
    Map<String, Wishlist> wishlistIdMap = new LinkedHashMap<>();
    when(wishlistRepository.findById(7L)).thenReturn(Optional.of(wishlist(7, "오늘의집카페")));

    ScheduleDto schedule =
        new ScheduleDto(List.of(new ScheduleDto.DayDto(1, List.of(placeDto("w7", "오늘의집카페")))));

    String result = merger.mergeAndBuildSection(schedule, placeIdMap, wishlistIdMap);

    assertThat(result).isEqualTo("[현재 일정 - 사용자가 이미 확인한 일정입니다]\n1일차: [w7] 오늘의집카페\n");
    assertThat(wishlistIdMap).containsOnlyKeys("w7");
  }

  @Test
  void DB에서도_못_찾으면_삭제된_장소_등_조용히_스킵한다() {
    Map<String, JejuPlace> placeIdMap = new LinkedHashMap<>();
    Map<String, Wishlist> wishlistIdMap = new LinkedHashMap<>();
    when(jejuPlaceRepository.findById(999L)).thenReturn(Optional.empty());

    ScheduleDto schedule =
        new ScheduleDto(List.of(new ScheduleDto.DayDto(1, List.of(placeDto("p999", "삭제된 장소")))));

    String result = merger.mergeAndBuildSection(schedule, placeIdMap, wishlistIdMap);

    assertThat(result).isEmpty();
    assertThat(placeIdMap).isEmpty();
  }

  @Test
  void id가_null이거나_형식이_이상하면_조용히_스킵한다() {
    Map<String, JejuPlace> placeIdMap = new LinkedHashMap<>();
    Map<String, Wishlist> wishlistIdMap = new LinkedHashMap<>();

    ScheduleDto schedule =
        new ScheduleDto(
            List.of(
                new ScheduleDto.DayDto(
                    1,
                    List.of(
                        placeDto(null, "이름만 있음"),
                        placeDto("p", "숫자 없음"),
                        placeDto("x5", "p나 w가 아님")))));

    String result = merger.mergeAndBuildSection(schedule, placeIdMap, wishlistIdMap);

    assertThat(result).isEmpty();
    verify(jejuPlaceRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void recommendedTime이_있으면_섹션에_시간을_같이_표기한다() {
    Map<String, JejuPlace> placeIdMap = new LinkedHashMap<>();
    placeIdMap.put("p1", place(1, "아끈다랑쉬 오름"));
    placeIdMap.put("p2", place(2, "용눈이오름"));
    Map<String, Wishlist> wishlistIdMap = new LinkedHashMap<>();

    ScheduleDto schedule =
        new ScheduleDto(
            List.of(
                new ScheduleDto.DayDto(
                    2,
                    List.of(
                        placeDto("p1", "아끈다랑쉬 오름", "08:30~10:00"),
                        placeDto("p2", "용눈이오름", null)))));

    String result = merger.mergeAndBuildSection(schedule, placeIdMap, wishlistIdMap);

    // "오후부터는 바꿔줘" 같은 시간대 기준 부분 교체 요청을 AI가 판단하려면
    // 시간이 필요하다(#40) — recommendedTime이 없는 장소는 이름만 표기한다.
    assertThat(result)
        .isEqualTo(
            "[현재 일정 - 사용자가 이미 확인한 일정입니다]\n" + "2일차: [p1] 아끈다랑쉬 오름 (08:30~10:00), [p2] 용눈이오름\n");
  }
}
