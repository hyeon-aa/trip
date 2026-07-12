// 일차(Day)별 마커/동선 색상. 위시리스트 마커가 이미 빨간 핀을 쓰고 있어서
// red는 제외했다 — 색각이상(CVD) 검증 완료.
export const DAY_COLORS = [
  "#2a78d6", // day 1 - blue
  "#1baf7a", // day 2 - aqua
  "#eda100", // day 3 - yellow
  "#008300", // day 4 - green
  "#4a3aa7", // day 5 - violet
  "#e87ba4", // day 6 - magenta
  "#eb6834", // day 7 - orange
];

export function getDayColor(day: number): string {
  return DAY_COLORS[(day - 1) % DAY_COLORS.length];
}
