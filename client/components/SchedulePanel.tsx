"use client";

import { Schedule } from "@/feature/plan/api";
import { getDayColor } from "@/lib/dayColors";

interface Props {
  schedule: Schedule;
  selectedDay: number;
  onSelectDay: (day: number) => void;
}

export default function SchedulePanel({
  schedule,
  selectedDay,
  onSelectDay,
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

        <div className="space-y-2">
          {day.places.map((place, idx) => (
            <div key={idx} className="bg-sky-50 rounded-lg p-2">
              <div className="flex items-center gap-2">
                <span
                  className="w-5 h-5 text-white rounded-full text-xs flex items-center justify-center"
                  style={{ background: getDayColor(day.day) }}
                >
                  {idx + 1}
                </span>

                <span className="font-medium text-sm">{place.name}</span>

                <span className="text-xs text-stone-400">
                  {place.category}
                </span>
              </div>

              <p className="text-xs text-stone-500 mt-1">
                🕒 {place.recommendedTime}
              </p>

              <p className="text-xs text-stone-600 mt-1">{place.reason}</p>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
