"use client";

import { Place, Schedule } from "@/feature/plan/api";
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

// 지도 위에서 클릭된 마커 하나만 추적한다. 위시리스트 마커는 recommendedTime/reason이
// 없어 일정 마커와 팝업 내용을 다르게 분기해야 하므로 태그드 유니온으로 구분한다.
type SelectedMarker =
  | { kind: "wishlist"; item: Wishlist }
  | { kind: "schedule"; place: Place };

export default function KakaoMap() {
  const [center, setCenter] = useState({ lat: 33.450701, lng: 126.570667 });
  const [wishlist, setWishlist] = useState<Wishlist[]>([]);
  const [scheduleData, setScheduleData] = useState<Schedule | null>(null);
  const [selectedDay, setSelectedDay] = useState<number | null>(null);
  const [selectedMarker, setSelectedMarker] = useState<SelectedMarker | null>(
    null
  );

  useEffect(() => {
    getWishlist().then((data) => setWishlist(data));
  }, []);

  const handleScheduleUpdate = (schedule: Schedule) => {
    setScheduleData(schedule);
    setSelectedDay(schedule.days[0]?.day ?? null);
    setSelectedMarker(null);

    // 첫번째 장소로 지도 중심 이동
    const firstPlace = schedule.days[0]?.places.find((p) => p.lat && p.lng);
    if (firstPlace?.lat && firstPlace?.lng) {
      setCenter({ lat: firstPlace.lat, lng: firstPlace.lng });
    }
  };

  // 날짜 탭을 바꾸면 이전 날짜의 장소 마커는 지도에서 사라지므로, 열려있던
  // 팝업이 더 이상 없는 마커를 가리키며 남아있지 않도록 함께 닫는다.
  const handleSelectDay = (day: number) => {
    setSelectedDay(day);
    setSelectedMarker(null);
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
              onClick={() =>
                setSelectedMarker((prev) =>
                  prev?.kind === "wishlist" && prev.item.id === item.id
                    ? null
                    : { kind: "wishlist", item }
                )
              }
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
                      clickable
                    >
                      <div
                        onClick={() =>
                          setSelectedMarker((prev) =>
                            prev?.kind === "schedule" &&
                            prev.place === place
                              ? null
                              : { kind: "schedule", place }
                          )
                        }
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
                          cursor: "pointer",
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
          {selectedMarker && (
            <CustomOverlayMap
              position={
                selectedMarker.kind === "wishlist"
                  ? {
                      lat: selectedMarker.item.lat,
                      lng: selectedMarker.item.lng,
                    }
                  : {
                      lat: selectedMarker.place.lat!,
                      lng: selectedMarker.place.lng!,
                    }
              }
              yAnchor={1}
              zIndex={20}
              clickable
            >
              <div className="relative -translate-y-3">
                <div className="relative w-64 px-3.5 py-3 text-left bg-white rounded-2xl shadow-lg shadow-stone-300/40 border border-stone-100">
                  <button
                    type="button"
                    onClick={() => setSelectedMarker(null)}
                    aria-label="닫기"
                    className="absolute top-2 right-2 w-5 h-5 flex items-center justify-center rounded-full text-stone-400 hover:bg-stone-100 hover:text-stone-600 text-xs leading-none transition-colors"
                  >
                    ✕
                  </button>
                  <p className="font-semibold text-sm text-stone-800 pr-6 break-keep leading-snug">
                    {selectedMarker.kind === "wishlist"
                      ? selectedMarker.item.name
                      : selectedMarker.place.name}
                  </p>
                  <span className="inline-block mt-1.5 px-2 py-0.5 rounded-full bg-sky-50 text-sky-600 text-[11px] font-medium">
                    {selectedMarker.kind === "wishlist"
                      ? selectedMarker.item.category
                      : selectedMarker.place.category}
                  </span>
                  {selectedMarker.kind === "schedule" ? (
                    <>
                      {selectedMarker.place.recommendedTime && (
                        <p className="text-xs text-stone-500 mt-2 break-keep">
                          🕒 {selectedMarker.place.recommendedTime}
                        </p>
                      )}
                      {selectedMarker.place.reason && (
                        <p className="text-xs text-stone-600 mt-1.5 leading-relaxed break-keep border-t border-stone-100 pt-1.5">
                          {selectedMarker.place.reason}
                        </p>
                      )}
                    </>
                  ) : (
                    selectedMarker.item.address && (
                      <p className="text-xs text-stone-500 mt-2 leading-relaxed break-keep">
                        {selectedMarker.item.address}
                      </p>
                    )
                  )}
                </div>
                <div className="absolute left-1/2 -bottom-1.5 -translate-x-1/2 w-3 h-3 bg-white border-b border-r border-stone-100 rotate-45" />
              </div>
            </CustomOverlayMap>
          )}
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
              onSelectDay={handleSelectDay}
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
