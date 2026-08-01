"use client";

import { Schedule } from "@/feature/plan/api";
import { getDayColor } from "@/lib/dayColors";

interface Props {
  schedule: Schedule;
  selectedDay: number;
  onSelectDay: (day: number) => void;
  onDeletePlace: (day: number, index: number) => void;
}

export default function SchedulePanel({
  schedule,
  selectedDay,
  onSelectDay,
  onDeletePlace,
}: Props) {
  const day = schedule.days.find((d) => d.day === selectedDay);

  if (!day) return null;

  return (
    <div className="mt-3">
      <div className="flex gap-1.5 flex-wrap mb-3">
        {schedule.days.map((d) => (
          <button
            key={d.day}
            onClick={() => onSelectDay(d.day)}
            className="text-xs font-medium px-3 py-1.5 rounded-full transition-colors"
            style={
              d.day === selectedDay
                ? { background: getDayColor(d.day), color: "white" }
                : { background: "#f0f9ff", color: "#0369a1" }
            }
          >
            Day {d.day}
          </button>
        ))}
      </div>

      <div className="border border-sky-100 rounded-xl p-3 bg-white">
        <p
          className="font-medium mb-2 flex items-center gap-1.5"
          style={{ color: getDayColor(day.day) }}
        >
          <span
            className="w-2.5 h-2.5 rounded-full inline-block"
            style={{ background: getDayColor(day.day) }}
          />
          Day {day.day}
        </p>

        <div>
          {day.places.map((place, idx) => {
            const isLast = idx === day.places.length - 1;
            return (
              <div key={idx} className="flex gap-2">
                {/* 장소 순서를 이어주는 세로 레일 — 점(번호)과 다음 장소까지의
                    연결선을 한 열에 쌓아서, 카드가 여러 개여도 하나의 동선으로
                    이어져 보이게 한다. */}
                <div className="flex flex-col items-center shrink-0">
                  <span
                    className="w-5 h-5 text-white rounded-full text-xs flex items-center justify-center shrink-0"
                    style={{ background: getDayColor(day.day) }}
                  >
                    {idx + 1}
                  </span>
                  {!isLast && (
                    <div className="w-px flex-1 min-h-[8px] my-1 bg-sky-200" />
                  )}
                </div>

                <div className="flex-1 min-w-0 pb-2">
                  <div className="bg-sky-50 rounded-lg p-2">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-sm">
                        {place.name}
                      </span>

                      <span className="text-xs text-stone-400">
                        {place.category}
                      </span>

                      <button
                        type="button"
                        onClick={() => onDeletePlace(day.day, idx)}
                        aria-label={`${place.name} 삭제`}
                        className="ml-auto text-stone-400 hover:text-red-500 transition-colors w-5 h-5 flex items-center justify-center shrink-0"
                      >
                        ✕
                      </button>
                    </div>

                    <p className="text-xs text-stone-500 mt-1">
                      🕒 {place.recommendedTime}
                    </p>

                    <p className="text-xs text-stone-600 mt-1">
                      {place.reason}
                    </p>
                  </div>

                  {!isLast && day.places[idx + 1]?.travelMinutesFromPrevious != null && (
                    <p className="text-[11px] text-stone-400 pt-1.5 pl-0.5">
                      🚗 이동 약 {day.places[idx + 1].travelMinutesFromPrevious}분
                    </p>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
