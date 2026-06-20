"use client";

import { ChatMessage, sendPlanChat } from "@/feature/plan/api";
import { useState } from "react";

interface Props {
  onScheduleUpdate: (schedule: object) => void;
}

export default function PlanChat({ onScheduleUpdate }: Props) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  const sendMessage = async () => {
    if (!input.trim() || isLoading) return;

    const userMessage = input.trim();
    setInput("");
    setIsLoading(true);

    const newMessages: ChatMessage[] = [
      ...messages,
      { role: "user", content: userMessage },
    ];
    setMessages(newMessages);

    try {
      const response = await sendPlanChat(userMessage, messages);
      try {
        const parsed = JSON.parse(response);
        if (parsed.schedule) onScheduleUpdate(parsed.schedule);
        setMessages([
          ...newMessages,
          { role: "assistant", content: parsed.message || response },
        ]);
      } catch {
        setMessages([...newMessages, { role: "assistant", content: response }]);
      }
    } catch {
      setMessages([
        ...newMessages,
        { role: "assistant", content: "오류가 발생했어요. 다시 시도해주세요." },
      ]);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex flex-col h-full">
      <div className="p-4 border-b border-sky-100">
        <p className="text-xs font-medium text-sky-600 uppercase tracking-wider">
          AI 플래너
        </p>
        <p className="text-xs text-stone-400 mt-0.5">
          위시리스트 기반으로 일정을 짜드려요
        </p>
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-3">
        {messages.length === 0 && (
          <div className="text-center py-8">
            <p className="text-2xl mb-2">✈️</p>
            <p className="text-xs text-stone-400">
              여행 일정을 짜달라고 말해보세요!
            </p>
            <p className="text-xs text-stone-300 mt-1">
              제주도 2박 3일 일정 짜줘
            </p>
          </div>
        )}
        {messages.map((msg, idx) => (
          <div
            key={idx}
            className={`flex ${
              msg.role === "user" ? "justify-end" : "justify-start"
            }`}
          >
            <div
              className={`max-w-[80%] px-3 py-2 rounded-2xl text-sm leading-relaxed ${
                msg.role === "user"
                  ? "bg-sky-400 text-white rounded-br-sm"
                  : "bg-sky-50 text-stone-700 rounded-bl-sm"
              }`}
            >
              {msg.content}
            </div>
          </div>
        ))}
        {isLoading && (
          <div className="flex justify-start">
            <div className="bg-sky-50 px-4 py-2 rounded-2xl rounded-bl-sm text-sm text-stone-400">
              일정 생성 중...
            </div>
          </div>
        )}
      </div>

      <div className="flex gap-2 p-4 border-t border-sky-100">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && sendMessage()}
          placeholder="메시지를 입력하세요..."
          className="flex-1 border border-sky-200 px-3 py-2 rounded-xl text-sm bg-sky-50 placeholder-sky-300 focus:outline-none focus:border-sky-400"
          disabled={isLoading}
        />
        <button
          onClick={sendMessage}
          disabled={isLoading}
          className="bg-sky-400 hover:bg-sky-500 text-white px-4 py-2 rounded-xl text-sm transition-colors disabled:opacity-50"
        >
          전송
        </button>
      </div>
    </div>
  );
}
