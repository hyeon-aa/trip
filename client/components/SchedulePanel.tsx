"use client";

interface Day {
  day: number;
  places: string[];
}

interface Schedule {
  days: Day[];
}

interface Props {
  schedule: Schedule | null;
}

export default function SchedulePanel({ schedule }: Props) {
  if (!schedule) {
    return (
      <div className="p-4 text-center">
        <p className="text-xs text-stone-400">
          AI와 대화해서 일정을 만들어보세요!
        </p>
      </div>
    );
  }

  return (
    <div className="p-4">
      <p className="text-xs font-medium text-sky-600 uppercase tracking-wider mb-3">
        여행 일정
      </p>
      <div className="space-y-4">
        {schedule.days.map((day) => (
          <div key={day.day}>
            <p className="text-xs font-medium text-sky-500 mb-2">
              Day {day.day}
            </p>
            <div className="space-y-1.5">
              {day.places.map((place, idx) => (
                <div key={idx} className="flex items-center gap-2">
                  <span className="w-5 h-5 bg-sky-100 text-sky-600 rounded-full text-xs flex items-center justify-center flex-shrink-0">
                    {idx + 1}
                  </span>
                  <span className="text-sm text-stone-600">{place}</span>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
