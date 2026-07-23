"use client";

import SearchBar from "@/components/SearchBar";
import WishlistPanel from "@/components/WishlistPanel";
import {
  addToWishlist,
  deleteWishlist,
  getWishlist,
  updateWishlistMemo,
} from "@/feature/wishlist/api";
import { Place } from "@/types/place/place";
import { Wishlist } from "@/types/wishlist/wishlist";
import Link from "next/link";
import { useEffect, useState } from "react";

export default function WishlistPage() {
  const [wishlist, setWishlist] = useState<Wishlist[]>([]);

  const fetchWishlist = () => getWishlist().then((data) => setWishlist(data));

  useEffect(() => {
    fetchWishlist();
  }, []);

  const handleAddToWishlist = async (place: Place) => {
    await addToWishlist(place);
    await fetchWishlist();
    alert(`${place.place_name} 위시리스트에 추가됐어요!`);
  };

  const handleDelete = async (id: number) => {
    await deleteWishlist(id);
    await fetchWishlist();
  };

  const handleUpdateMemo = async (id: number, memo: string) => {
    await updateWishlistMemo(id, memo);
    await fetchWishlist();
  };

  return (
    <div className="min-h-screen bg-sky-50 flex justify-center">
      <div className="w-full max-w-xl bg-white min-h-screen flex flex-col shadow-sm">
        <div className="flex items-center justify-between p-4 border-b border-sky-100">
          <div>
            <h1 className="text-base font-medium text-stone-700">
              🗺️ 위시리스트
            </h1>
            <p className="text-xs text-stone-400 mt-0.5">
              가고 싶은 장소를 검색하고 저장해보세요
            </p>
          </div>
          <Link
            href="/"
            className="text-xs bg-sky-100 hover:bg-sky-200 text-sky-700 px-3 py-1.5 rounded-lg transition-colors"
          >
            지도로 돌아가기
          </Link>
        </div>
        <SearchBar onAddToWishlist={handleAddToWishlist} />
        <div className="flex-1 overflow-y-auto">
          <WishlistPanel
            wishlist={wishlist}
            onDelete={handleDelete}
            onUpdateMemo={handleUpdateMemo}
          />
        </div>
      </div>
    </div>
  );
}
