"use client";

import { sendWishlistChat } from "@/feature/wishlist/api";
import { useState } from "react";

interface Props {
  // Tool 호출로 실제 위시리스트가 바뀔 수 있으므로, 응답이 오면 부모가
  // 목록을 다시 불러올 수 있게 알려준다.
  onMessageSent: () => void;
}

interface ChatEntry {
  role: "user" | "assistant";
  content: string;
}

export default function WishlistChat({ onMessageSent }: Props) {
  const [message, setMessage] = useState("");
  const [entries, setEntries] = useState<ChatEntry[]>([]);
  const [loading, setLoading] = useState(false);

  const handleSend = async () => {
    const trimmed = message.trim();
    if (!trimmed || loading) return;

    setEntries((prev) => [...prev, { role: "user", content: trimmed }]);
    setMessage("");
    setLoading(true);

    try {
      const reply = await sendWishlistChat(trimmed);
      setEntries((prev) => [...prev, { role: "assistant", content: reply }]);
      await onMessageSent();
    } catch {
      setEntries((prev) => [
        ...prev,
        { role: "assistant", content: "요청 처리 중 오류가 발생했어요." },
      ]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="p-4 border-b border-sky-100">
      <p className="text-xs font-medium text-sky-600 mb-2 uppercase tracking-wider">
        채팅으로 추가
      </p>

      {entries.length > 0 && (
        <div className="space-y-2 mb-2 max-h-40 overflow-y-auto">
          {entries.map((entry, idx) => (
            <div
              key={idx}
              className={`flex ${
                entry.role === "user" ? "justify-end" : "justify-start"
              }`}
            >
              <div
                className={`max-w-[85%] px-3 py-2 rounded-2xl text-sm ${
                  entry.role === "user"
                    ? "bg-sky-400 text-white rounded-br-sm"
                    : "bg-sky-50 text-stone-700 rounded-bl-sm"
                }`}
              >
                {entry.content}
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="flex gap-2">
        <input
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          placeholder="예: 협재해수욕장 추가해줘"
          disabled={loading}
          className="flex-1 border border-sky-200 px-3 py-2 rounded-xl text-sm focus:outline-none focus:border-sky-400 bg-sky-50 placeholder-sky-300 disabled:opacity-60"
          onKeyDown={(e) => e.key === "Enter" && handleSend()}
        />
        <button
          onClick={handleSend}
          disabled={loading}
          className="bg-sky-400 hover:bg-sky-500 disabled:opacity-60 text-white px-4 py-2 rounded-xl text-sm transition-colors"
        >
          {loading ? "..." : "전송"}
        </button>
      </div>
      <p className="text-[11px] text-stone-400 mt-1.5">
        이미 저장된 실제 제주 장소 이름만 추가할 수 있어요.
      </p>
    </div>
  );
}
