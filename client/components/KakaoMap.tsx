"use client";

import {
  addToWishlist,
  deleteWishlist,
  getWishlist,
} from "@/feature/wishlist/api";
import { Place } from "@/types/place/place";
import { Wishlist } from "@/types/wishlist/wishlist";
import { useEffect, useState } from "react";
import { Map, MapMarker } from "react-kakao-maps-sdk";
import PlanChat from "./PlanChat";
import SchedulePanel from "./SchedulePanel";
import SearchBar from "./SearchBar";
import WishlistPanel from "./WishlistPanel";

interface Schedule {
  days: { day: number; places: string[] }[];
}

export default function KakaoMap() {
  const [center, setCenter] = useState({ lat: 33.450701, lng: 126.570667 });
  const [wishlist, setWishlist] = useState<Wishlist[]>([]);
  const [selectedPlaces, setSelectedPlaces] = useState<Place[]>([]);
  const [schedule, setSchedule] = useState<Schedule | null>(null);

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
      </Map>

      {/* 오른쪽 - 채팅 + 일정 */}
      <div className="w-80 flex flex-col bg-white border-l border-sky-100 shadow-sm">
        <div className="flex-1 overflow-hidden flex flex-col min-h-0">
          <PlanChat onScheduleUpdate={(s) => setSchedule(s as Schedule)} />
        </div>
        {schedule && (
          <div className="border-t border-sky-100 overflow-y-auto max-h-64">
            <SchedulePanel schedule={schedule} />
          </div>
        )}
      </div>
    </div>
  );
}
