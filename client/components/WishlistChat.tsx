"use client";

import { sendWishlistChat } from "@/feature/wishlist/api";
import { useState } from "react";

interface Props {
  // Tool 호출로 실제 위시리스트가 바뀔 수 있으므로, 응답이 오면 부모가
  // 목록을 다시 불러올 수 있게 알려준다. 실패해도 채팅 자체는 이미 성공한
  // 뒤라 여기서 잡아서 조용히 넘어간다(아래 handleSend 참고) — Promise를
  // 반환해야 그게 가능해서 void가 아니라 Promise<void>로 받는다.
  onMessageSent: () => Promise<void>;
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
    } catch {
      setEntries((prev) => [
        ...prev,
        { role: "assistant", content: "요청 처리 중 오류가 발생했어요." },
      ]);
      setLoading(false);
      return;
    }
    setLoading(false);

    // 채팅 자체는 성공했으니(위 catch를 안 탔으니), 목록 새로고침이 실패해도
    // 채팅이 실패한 것처럼 보여주지 않는다 — 그러면 사용자가 이미 성공한
    // 요청을 다시 보내서 중복 저장으로 이어질 수 있다(코드 리뷰 지적).
    // 조용히 실패해도 사용자가 위시리스트 화면을 보면 결국 최신 상태를
    // 확인할 수 있다.
    try {
      await onMessageSent();
    } catch {
      // 새로고침만 실패 — 채팅 성공 메시지는 이미 표시됐으니 그대로 둔다.
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
          onKeyDown={(e) => {
            // 한글 등 IME로 글자를 조합하는 도중에 누른 Enter는 조합 확정용이지
            // 전송 의도가 아니다 — isComposing 체크 없이 e.key만 보면 완성 안 된
            // 글자가 그대로 전송될 수 있다(코드 리뷰 지적).
            if (e.key === "Enter" && !e.nativeEvent.isComposing) handleSend();
          }}
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
