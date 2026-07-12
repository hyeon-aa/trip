"use client";

import { Schedule } from "@/feature/plan/api";
import { getWishlist } from "@/feature/wishlist/api";
import { Wishlist } from "@/types/wishlist/wishlist";
import { getDayColor } from "@/lib/dayColors";
import Link from "next/link";
import { useEffect, useState } from "react";
import {
  CustomOverlayMap,
  Map,
  MapMarker,
  Polyline,
} from "react-kakao-maps-sdk";
import PlanChat from "./PlanChat";
import SchedulePanel from "./SchedulePanel";

export default function KakaoMap() {
  const [center, setCenter] = useState({ lat: 33.450701, lng: 126.570667 });
  const [wishlist, setWishlist] = useState<Wishlist[]>([]);
  const [scheduleData, setScheduleData] = useState<Schedule | null>(null);
  const [selectedDay, setSelectedDay] = useState<number | null>(null);

  useEffect(() => {
    getWishlist().then((data) => setWishlist(data));
  }, []);

  const handleScheduleUpdate = (schedule: Schedule) => {
    setScheduleData(schedule);
    setSelectedDay(schedule.days[0]?.day ?? null);

    // 첫번째 장소로 지도 중심 이동
    const firstPlace = schedule.days[0]?.places.find((p) => p.lat && p.lng);
    if (firstPlace?.lat && firstPlace?.lng) {
      setCenter({ lat: firstPlace.lat, lng: firstPlace.lng });
    }
  };

  const activeDay = scheduleData?.days.find((d) => d.day === selectedDay);

  return (
    <div className="flex h-screen bg-sky-50">
      {/* 왼쪽 - 지도 */}
      <div className="flex-1 flex flex-col h-screen">
        <div className="flex items-center justify-between px-4 py-3 bg-white border-b border-sky-100 shadow-sm">
          <div>
            <h1 className="text-base font-medium text-stone-700">
              🌏 여행 플래너
            </h1>
            <p className="text-xs text-stone-400 mt-0.5">
              AI와 대화하며 제주 여행 일정을 짜보세요
            </p>
          </div>
          <Link
            href="/wishlist"
            className="text-xs bg-sky-100 hover:bg-sky-200 text-sky-700 px-3 py-1.5 rounded-lg transition-colors"
          >
            위시리스트
          </Link>
        </div>
        <Map center={center} style={{ flex: 1, minHeight: 0 }} level={3}>
          {wishlist.map((item) => (
            <MapMarker
              key={`wish-${item.id}`}
              position={{ lat: item.lat, lng: item.lng }}
              title={item.name}
              image={{
                src: "https://t1.daumcdn.net/localimg/localimages/07/mapapidoc/marker_red.png",
                size: { width: 32, height: 35 },
                options: { offset: { x: 16, y: 35 } },
              }}
            />
          ))}
          {activeDay &&
            (() => {
              const points = activeDay.places.filter((p) => p.lat && p.lng);
              return (
                <>
                  {points.map((place, idx) => (
                    <CustomOverlayMap
                      key={`schedule-${activeDay.day}-${idx}`}
                      position={{ lat: place.lat!, lng: place.lng! }}
                    >
                      <div
                        style={{
                          background: getDayColor(activeDay.day),
                          color: "white",
                          borderRadius: "50%",
                          width: 28,
                          height: 28,
                          display: "flex",
                          alignItems: "center",
                          justifyContent: "center",
                          fontSize: 12,
                          fontWeight: 600,
                          border: "2px solid white",
                          boxShadow: "0 1px 3px rgba(0,0,0,0.3)",
                        }}
                      >
                        {idx + 1}
                      </div>
                    </CustomOverlayMap>
                  ))}
                  <Polyline
                    path={points.map((p) => ({ lat: p.lat!, lng: p.lng! }))}
                    strokeWeight={3}
                    strokeColor={getDayColor(activeDay.day)}
                    strokeOpacity={0.7}
                    strokeStyle="solid"
                  />
                </>
              );
            })()}
        </Map>
      </div>

      {/* 가운데 - 일정 */}
      <div className="w-72 flex flex-col bg-white border-l border-sky-100 shadow-sm overflow-y-auto">
        <div className="p-4 border-b border-sky-100">
          <p className="text-xs font-medium text-sky-600 uppercase tracking-wider">
            일정
          </p>
        </div>
        <div className="flex-1 p-4">
          {scheduleData && selectedDay !== null ? (
            <SchedulePanel
              schedule={scheduleData}
              selectedDay={selectedDay}
              onSelectDay={setSelectedDay}
            />
          ) : (
            <p className="text-xs text-stone-400">
              대화를 통해 일정이 만들어지면 여기에 표시돼요.
            </p>
          )}
        </div>
      </div>

      {/* 오른쪽 - 채팅 */}
      <div className="w-80 flex flex-col bg-white border-l border-sky-100 shadow-sm">
        <PlanChat onScheduleUpdate={handleScheduleUpdate} />
      </div>
    </div>
  );
}
