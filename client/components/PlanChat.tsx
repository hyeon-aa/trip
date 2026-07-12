"use client";

import { ChatMessage, Schedule, sendPlanChat } from "@/feature/plan/api";
import { useState } from "react";

interface Props {
  onScheduleUpdate: (schedule: Schedule) => void;
}

export default function PlanChat({ onScheduleUpdate }: Props) {
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      role: "assistant",
      content:
        "안녕하세요! 제주도 여행을 계획해드릴게요 😊 어떤 스타일을 좋아하시는지, 누구와 함께 가시는지, 며칠 일정인지 알려주세요! 한 번에 말씀해주셔도 되고, 하나씩 알려주셔도 좋아요.",
    },
  ]);
  const [input, setInput] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  const sendMessage = async (overrideText?: string) => {
    const text = overrideText ?? input;
    if (!text.trim() || isLoading) return;

    const userMessage = text.trim();
    setInput("");
    setIsLoading(true);

    const newMessages: ChatMessage[] = [
      ...messages,
      { role: "user", content: userMessage },
    ];

    setMessages(newMessages);

    try {
      setMessages([...newMessages, { role: "assistant", content: "" }]);

      const response = await sendPlanChat(userMessage, messages, (content) => {
        setMessages([...newMessages, { role: "assistant", content }]);
      });

      const cleaned = response
        .replace(/```json\n?/g, "")
        .replace(/```/g, "")
        .trim();

      const parsed = JSON.parse(cleaned);

      if (parsed.type === "schedule" && parsed.schedule) {
        onScheduleUpdate(parsed.schedule);
      }

      setMessages([
        ...newMessages,
        {
          role: "assistant",
          content: parsed.message,
          options: parsed.options,
        },
      ]);
    } catch {
      setMessages([
        ...newMessages,
        {
          role: "assistant",
          content: "오류가 발생했어요. 다시 시도해주세요.",
        },
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
        {messages.map((msg, idx) => (
          <div
            key={idx}
            className={`flex flex-col ${
              msg.role === "user" ? "items-end" : "items-start"
            }`}
          >
            <div
              className={`max-w-[90%] px-3 py-2 rounded-2xl text-sm ${
                msg.role === "user"
                  ? "bg-sky-400 text-white rounded-br-sm"
                  : "bg-sky-50 text-stone-700 rounded-bl-sm"
              }`}
            >
              {msg.content}
            </div>

            {msg.options &&
              msg.options.length > 0 &&
              idx === messages.length - 1 && (
                <div className="flex flex-wrap gap-2 mt-2 ml-1">
                  {msg.options.map((opt) => (
                    <button
                      key={opt}
                      onClick={() => sendMessage(opt)}
                      className="text-xs bg-white border border-sky-300 text-sky-600 px-3 py-1.5 rounded-full hover:bg-sky-100 transition-colors"
                    >
                      {opt}
                    </button>
                  ))}
                </div>
              )}
          </div>
        ))}

        {isLoading && (
          <div className="flex justify-start">
            <div className="bg-sky-50 px-4 py-2 rounded-2xl text-sm text-stone-400">
              생각 중...
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
          className="flex-1 border border-sky-200 px-3 py-2 rounded-xl text-sm"
          disabled={isLoading}
        />

        <button
          onClick={() => sendMessage()}
          disabled={isLoading}
          className="bg-sky-400 text-white px-4 py-2 rounded-xl disabled:opacity-50"
        >
          전송
        </button>
      </div>
    </div>
  );
}
