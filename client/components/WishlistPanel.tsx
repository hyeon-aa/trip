"use client";

import { Wishlist } from "@/types/wishlist/wishlist";
import { useState } from "react";

interface Props {
  wishlist: Wishlist[];
  onDelete: (id: number) => void;
  onClickItem?: (item: Wishlist) => void;
  onUpdateMemo?: (id: number, memo: string) => void;
}

function WishlistMemo({
  item,
  onUpdateMemo,
}: {
  item: Wishlist;
  onUpdateMemo?: (id: number, memo: string) => void;
}) {
  const [isEditing, setIsEditing] = useState(false);
  const [draft, setDraft] = useState(item.memo ?? "");

  if (!onUpdateMemo) {
    return item.memo ? (
      <p className="text-xs text-stone-500 mt-1 whitespace-pre-wrap">
        📝 {item.memo}
      </p>
    ) : null;
  }

  const handleSave = () => {
    onUpdateMemo(item.id, draft.trim());
    setIsEditing(false);
  };

  if (isEditing) {
    return (
      <div className="mt-2 flex gap-1.5" onClick={(e) => e.stopPropagation()}>
        <input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="왜 저장했나요? (추천받아서, SNS에서 봐서...)"
          className="flex-1 min-w-0 border border-sky-200 px-2 py-1 rounded-lg text-xs focus:outline-none focus:border-sky-400 bg-white placeholder-stone-300"
          autoFocus
          onKeyDown={(e) => {
            if (e.key === "Enter") handleSave();
            if (e.key === "Escape") {
              setDraft(item.memo ?? "");
              setIsEditing(false);
            }
          }}
        />
        <button
          onClick={handleSave}
          className="text-xs bg-sky-400 hover:bg-sky-500 text-white px-2.5 py-1 rounded-lg transition-colors flex-shrink-0"
        >
          저장
        </button>
      </div>
    );
  }

  return (
    <div
      className="mt-1 flex items-start gap-1.5 cursor-pointer group"
      onClick={(e) => {
        e.stopPropagation();
        setDraft(item.memo ?? "");
        setIsEditing(true);
      }}
    >
      {item.memo ? (
        <p className="text-xs text-stone-500 whitespace-pre-wrap group-hover:text-stone-700 transition-colors">
          📝 {item.memo}
        </p>
      ) : (
        <p className="text-xs text-sky-400 group-hover:text-sky-600 transition-colors">
          ✏️ 메모 추가
        </p>
      )}
    </div>
  );
}

export default function WishlistPanel({
  wishlist,
  onDelete,
  onClickItem,
  onUpdateMemo,
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
            className={`p-3 bg-sky-50 rounded-xl transition-colors ${
              onClickItem ? "cursor-pointer hover:bg-sky-100" : ""
            }`}
            onClick={onClickItem ? () => onClickItem(item) : undefined}
          >
            <div className="flex justify-between items-center">
              <div className="min-w-0">
                <p className="text-sm font-medium text-stone-700">
                  {item.name}
                </p>
                <p className="text-xs text-stone-400 mt-0.5">
                  {item.address}
                </p>
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
            <WishlistMemo item={item} onUpdateMemo={onUpdateMemo} />
          </div>
        ))}
      </div>
    </div>
  );
}
