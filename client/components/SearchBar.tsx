"use client";

import { searchPlaces } from "@/feature/place/api";
import { Place } from "@/types/place/place";
import { useEffect, useState } from "react";

interface Props {
  onSelectPlace: (place: Place) => void;
  onAddToWishlist: (place: Place) => void;
}

export default function SearchBar({ onSelectPlace, onAddToWishlist }: Props) {
  const [query, setQuery] = useState("");
  const [places, setPlaces] = useState<Place[]>([]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setQuery(value);
    if (value.length < 2) setPlaces([]);
  };

  useEffect(() => {
    if (query.length < 2) return;
    const timer = setTimeout(async () => {
      const results = await searchPlaces(query);
      setPlaces(results);
    }, 300);
    return () => clearTimeout(timer);
  }, [query]);

  const handleSearch = async () => {
    const results = await searchPlaces(query);
    setPlaces(results);
  };

  return (
    <div className="relative p-4 border-b border-sky-100">
      <p className="text-xs font-medium text-sky-600 mb-2 uppercase tracking-wider">
        장소 검색
      </p>
      <div className="flex gap-2">
        <input
          value={query}
          onChange={handleChange}
          placeholder="어디로 떠날까요?"
          className="flex-1 border border-sky-200 px-3 py-2 rounded-xl text-sm focus:outline-none focus:border-sky-400 bg-sky-50 placeholder-sky-300"
          onKeyDown={(e) => e.key === "Enter" && handleSearch()}
        />
        <button
          onClick={handleSearch}
          className="bg-sky-400 hover:bg-sky-500 text-white px-4 py-2 rounded-xl text-sm transition-colors"
        >
          검색
        </button>
      </div>

      {places.length > 0 && (
        <div className="absolute left-4 right-4 bg-white border border-sky-100 rounded-xl shadow-lg z-50 max-h-64 overflow-y-auto mt-1">
          {places.map((place) => (
            <div
              key={place.id}
              className="px-4 py-3 border-b border-sky-50 hover:bg-sky-50 cursor-pointer flex justify-between items-center last:border-0"
              onClick={() => {
                onSelectPlace(place);
                setPlaces([]);
                setQuery(place.place_name);
              }}
            >
              <div>
                <p className="font-medium text-sm text-stone-700">
                  {place.place_name}
                </p>
                <p className="text-xs text-stone-400 mt-0.5">
                  {place.address_name}
                </p>
              </div>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onAddToWishlist(place);
                  setPlaces([]);
                  setQuery("");
                }}
                className="text-xs bg-sky-100 hover:bg-sky-200 text-sky-700 px-3 py-1.5 rounded-lg transition-colors flex-shrink-0 ml-2"
              >
                저장
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
