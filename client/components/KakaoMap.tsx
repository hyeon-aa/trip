"use client";

import { Schedule } from "@/feature/plan/api";
import {
  addToWishlist,
  deleteWishlist,
  getWishlist,
} from "@/feature/wishlist/api";
import { Place } from "@/types/place/place";
import { Wishlist } from "@/types/wishlist/wishlist";
import { getDayColor } from "@/lib/dayColors";
import { useEffect, useState } from "react";
import {
  CustomOverlayMap,
  Map,
  MapMarker,
  Polyline,
} from "react-kakao-maps-sdk";
import PlanChat from "./PlanChat";
import SchedulePanel from "./SchedulePanel";
import SearchBar from "./SearchBar";
import WishlistPanel from "./WishlistPanel";

export default function KakaoMap() {
  const [center, setCenter] = useState({ lat: 33.450701, lng: 126.570667 });
  const [wishlist, setWishlist] = useState<Wishlist[]>([]);
  const [selectedPlaces, setSelectedPlaces] = useState<Place[]>([]);
  const [scheduleData, setScheduleData] = useState<Schedule | null>(null);

  useEffect(() => {
    getWishlist().then((data) => setWishlist(data));
  }, []);

  const fetchWishlist = async () => {
    const data = await getWishlist();
    setWishlist(data);
  };

  const handleSelectPlace = (place: Place) => {
    setCenter({ lat: place.lat, lng: place.lng });
    setSelectedPlaces((prev) => {
      if (prev.find((p) => p.id === place.id)) return prev;
      return [...prev, place];
    });
  };

  const handleAddToWishlist = async (place: Place) => {
    await addToWishlist(place);
    await fetchWishlist();
    alert(`${place.place_name} 위시리스트에 추가됐어요!`);
  };

  const handleDelete = async (id: number) => {
    await deleteWishlist(id);
    await fetchWishlist();
  };

  const handleScheduleUpdate = (schedule: Schedule) => {
    setScheduleData(schedule);

    // 첫번째 장소로 지도 중심 이동
    const firstPlace = schedule.days[0]?.places.find((p) => p.lat && p.lng);
    if (firstPlace?.lat && firstPlace?.lng) {
      setCenter({ lat: firstPlace.lat, lng: firstPlace.lng });
    }
  };

  return (
    <div className="flex h-screen bg-sky-50">
      {/* 왼쪽 - 검색 + 위시리스트 */}
      <div className="w-72 flex flex-col bg-white border-r border-sky-100 shadow-sm">
        <div className="p-4 border-b border-sky-100">
          <h1 className="text-base font-medium text-stone-700">
            🌏 여행 플래너
          </h1>
          <p className="text-xs text-stone-400 mt-0.5">
            위시리스트를 저장하고 AI로 일정을 짜보세요
          </p>
        </div>
        <SearchBar
          onSelectPlace={handleSelectPlace}
          onAddToWishlist={handleAddToWishlist}
        />
        <div className="flex-1 overflow-y-auto">
          <WishlistPanel
            wishlist={wishlist}
            onDelete={handleDelete}
            onClickItem={(item) => setCenter({ lat: item.lat, lng: item.lng })}
          />
        </div>
      </div>

      {/* 가운데 - 지도 */}
      <Map center={center} style={{ flex: 1, height: "100vh" }} level={3}>
        {selectedPlaces.map((place) => (
          <MapMarker
            key={place.id}
            position={{ lat: place.lat, lng: place.lng }}
            title={place.place_name}
          />
        ))}
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
        {scheduleData?.days.flatMap((day) =>
          day.places
            .filter((p) => p.lat && p.lng)
            .map((place, idx) => (
              <CustomOverlayMap
                key={`schedule-${day.day}-${idx}`}
                position={{ lat: place.lat!, lng: place.lng! }}
              >
                <div
                  style={{
                    background: getDayColor(day.day),
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
            ))
        )}

        {scheduleData?.days.map((day) => {
          const path = day.places
            .filter((p) => p.lat && p.lng)
            .map((p) => ({ lat: p.lat!, lng: p.lng! }));

          return (
            <Polyline
              key={`route-${day.day}`}
              path={path}
              strokeWeight={3}
              strokeColor={getDayColor(day.day)}
              strokeOpacity={0.7}
              strokeStyle="solid"
            />
          );
        })}
      </Map>

      {/* 오른쪽 - 일정 + 채팅 */}
      <div className="w-80 flex flex-col bg-white border-l border-sky-100 shadow-sm">
        <div className="max-h-[45%] overflow-y-auto border-b border-sky-100 p-4">
          <p className="text-xs font-medium text-sky-600 uppercase tracking-wider">
            일정
          </p>
          {scheduleData ? (
            <SchedulePanel schedule={scheduleData} />
          ) : (
            <p className="text-xs text-stone-400 mt-2">
              대화를 통해 일정이 만들어지면 여기에 표시돼요.
            </p>
          )}
        </div>
        <div className="flex-1 overflow-hidden flex flex-col min-h-0">
          <PlanChat onScheduleUpdate={handleScheduleUpdate} />
        </div>
      </div>
    </div>
  );
}
