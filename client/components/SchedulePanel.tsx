"use client";

import { Schedule } from "@/feature/plan/api";

interface Props {
  schedule: Schedule;
}

export default function SchedulePanel({ schedule }: Props) {
  return (
    <div className="space-y-4 mt-3">
      {schedule.days.map((day) => (
        <div
          key={day.day}
          className="border border-sky-100 rounded-xl p-3 bg-white"
        >
          <p className="font-medium text-sky-600 mb-2">Day {day.day}</p>

          <div className="space-y-2">
            {day.places.map((place, idx) => (
              <div key={idx} className="bg-sky-50 rounded-lg p-2">
                <div className="flex items-center gap-2">
                  <span className="w-5 h-5 bg-sky-100 text-sky-600 rounded-full text-xs flex items-center justify-center">
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
      ))}
    </div>
  );
}
