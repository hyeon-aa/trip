"use client";

import { Wishlist } from "@/types/wishlist/wishlist";

interface Props {
  wishlist: Wishlist[];
  onDelete: (id: number) => void;
  onClickItem?: (item: Wishlist) => void;
}

export default function WishlistPanel({
  wishlist,
  onDelete,
  onClickItem,
}: Props) {
  return (
    <div className="p-4">
      <p className="text-xs font-medium text-sky-600 uppercase tracking-wider mb-3">
        위시리스트 {wishlist.length > 0 && `(${wishlist.length})`}
      </p>
      {wishlist.length === 0 && (
        <div className="text-center py-8">
          <p className="text-2xl mb-2">🗺️</p>
          <p className="text-xs text-stone-400">
            가고 싶은 장소를 저장해보세요
          </p>
        </div>
      )}
      <div className="space-y-2">
        {wishlist.map((item) => (
          <div
            key={item.id}
            className={`flex justify-between items-center p-3 bg-sky-50 rounded-xl transition-colors ${
              onClickItem ? "cursor-pointer hover:bg-sky-100" : ""
            }`}
            onClick={onClickItem ? () => onClickItem(item) : undefined}
          >
            <div>
              <p className="text-sm font-medium text-stone-700">{item.name}</p>
              <p className="text-xs text-stone-400 mt-0.5">{item.address}</p>
            </div>
            <button
              onClick={(e) => {
                e.stopPropagation();
                onDelete(item.id);
              }}
              className="text-xs text-red-300 hover:text-red-500 transition-colors ml-2 flex-shrink-0"
            >
              삭제
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
