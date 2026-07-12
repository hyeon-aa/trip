"use client";

import { searchPlaces } from "@/feature/place/api";
import {
  Accommodation,
  ChatMessage,
  Schedule,
  sendPlanChat,
} from "@/feature/plan/api";
import { Place as KakaoPlace } from "@/types/place/place";
import { useEffect, useRef, useState } from "react";

interface Props {
  onScheduleUpdate: (schedule: Schedule) => void;
}

// 스타일/동행자/기간/시간/숙소는 서버(임베딩+Gemini 호출)를 타지 않고 프론트에서만
// 순서대로 물어본다 — 마지막 답변에서만 실제 API를 한 번 호출한다.
const ONBOARDING_QUESTIONS: {
  content: string;
  options: string[];
  multiSelect?: boolean;
  timePicker?: boolean;
  placeSearch?: boolean;
}[] = [
  {
    content: "먼저 어떤 스타일의 여행을 원하시나요? (여러 개 골라도 좋아요)",
    options: [
      "바다/해변",
      "오름/자연경관",
      "맛집 탐방",
      "카페 투어",
      "액티비티(서핑·카트 등)",
      "역사/문화",
      "사진 명소",
      "휴양/힐링",
    ],
    multiSelect: true,
  },
  {
    content: "누구와 함께 가시나요?",
    options: ["혼자", "연인", "가족", "친구"],
  },
  {
    content: "며칠 일정인가요?",
    options: ["당일치기", "1박 2일", "2박 3일", "3박 4일"],
  },
  {
    content: "첫날 제주 도착 시간대는 언제쯤인가요? 정확한 시간을 알면 시계 아이콘으로 골라주세요.",
    options: ["오전 (9~11시)", "오후 (12~5시)", "저녁 이후 (6시~)", "모르겠어요, 패스"],
    timePicker: true,
  },
  {
    content: "마지막날 출발(비행기) 시간대는요?",
    options: ["오전 (9~11시)", "오후 (12~5시)", "저녁 이후 (6시~)", "모르겠어요, 패스"],
    timePicker: true,
  },
  {
    content:
      "마지막으로, 숙소는 어디인가요? 검색해서 골라주시면 숙소까지 동선을 더 정확하게 짜드려요.",
    options: ["숙소 없음, 건너뛰기"],
    placeSearch: true,
  },
];

function formatTimeLabel(time: string): string {
  const [hStr, mStr] = time.split(":");
  const hour = parseInt(hStr, 10);
  const minute = parseInt(mStr, 10);
  const period = hour < 12 ? "오전" : "오후";
  const hour12 = hour % 12 === 0 ? 12 : hour % 12;
  return minute === 0 ? `${period} ${hour12}시` : `${period} ${hour12}시 ${minute}분`;
}

export default function PlanChat({ onScheduleUpdate }: Props) {
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      role: "assistant",
      content:
        "안녕하세요! 제주도 여행을 계획해드릴게요 😊 버튼으로 골라도 되고, 직접 입력하셔도 좋아요.\n\n" +
        ONBOARDING_QUESTIONS[0].content,
      options: ONBOARDING_QUESTIONS[0].options,
      multiSelect: ONBOARDING_QUESTIONS[0].multiSelect,
      timePicker: ONBOARDING_QUESTIONS[0].timePicker,
      placeSearch: ONBOARDING_QUESTIONS[0].placeSearch,
    },
  ]);
  const [input, setInput] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [onboardingStep, setOnboardingStep] = useState(0);
  const [selectedOptions, setSelectedOptions] = useState<string[]>([]);
  const [pickedTime, setPickedTime] = useState("");
  const [lodgingQuery, setLodgingQuery] = useState("");
  const [lodgingResults, setLodgingResults] = useState<KakaoPlace[]>([]);
  const accommodationRef = useRef<Accommodation | undefined>(undefined);

  useEffect(() => {
    if (lodgingQuery.length < 2) return;
    const timer = setTimeout(async () => {
      const results = await searchPlaces(lodgingQuery);
      setLodgingResults(results);
    }, 300);
    return () => clearTimeout(timer);
  }, [lodgingQuery]);

  const sendMessage = async (overrideText?: string) => {
    const text = overrideText ?? input;
    if (!text.trim() || isLoading) return;

    const userMessage = text.trim();
    setInput("");

    // 아직 온보딩 질문이 남았으면 서버 호출 없이 다음 질문만 로컬에서 이어간다.
    if (onboardingStep < ONBOARDING_QUESTIONS.length - 1) {
      const next = ONBOARDING_QUESTIONS[onboardingStep + 1];
      setMessages((prev) => [
        ...prev,
        { role: "user", content: userMessage },
        {
          role: "assistant",
          content: next.content,
          options: next.options,
          multiSelect: next.multiSelect,
          timePicker: next.timePicker,
          placeSearch: next.placeSearch,
        },
      ]);
      setOnboardingStep((step) => step + 1);
      setSelectedOptions([]);
      setPickedTime("");
      setLodgingQuery("");
      setLodgingResults([]);
      return;
    }
    setOnboardingStep(ONBOARDING_QUESTIONS.length);

    setIsLoading(true);

    const newMessages: ChatMessage[] = [
      ...messages,
      { role: "user", content: userMessage },
    ];

    setMessages(newMessages);

    try {
      setMessages([...newMessages, { role: "assistant", content: "" }]);

      const response = await sendPlanChat(
        userMessage,
        messages,
        (content) => {
          setMessages([...newMessages, { role: "assistant", content }]);
        },
        accommodationRef.current
      );

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

            {msg.placeSearch && idx === messages.length - 1 && (
              <div className="mt-2 ml-1 w-full max-w-[90%] relative">
                <div className="flex gap-2">
                  <input
                    value={lodgingQuery}
                    onChange={(e) => {
                      const value = e.target.value;
                      setLodgingQuery(value);
                      if (value.length < 2) setLodgingResults([]);
                    }}
                    placeholder="숙소 이름으로 검색..."
                    className="flex-1 border border-sky-200 px-3 py-2 rounded-xl text-sm"
                  />
                  <button
                    onClick={() => sendMessage("숙소 없음, 건너뛰기")}
                    className="text-xs bg-white border border-sky-300 text-sky-600 px-3 py-1.5 rounded-full hover:bg-sky-100 transition-colors flex-shrink-0"
                  >
                    건너뛰기
                  </button>
                </div>
                {lodgingResults.length > 0 && (
                  <div className="absolute left-0 right-0 bg-white border border-sky-100 rounded-xl shadow-lg z-50 max-h-64 overflow-y-auto mt-1">
                    {lodgingResults.map((place) => (
                      <div
                        key={place.id}
                        className="px-4 py-3 border-b border-sky-50 hover:bg-sky-50 cursor-pointer last:border-0"
                        onClick={() => {
                          accommodationRef.current = {
                            lat: place.lat,
                            lng: place.lng,
                            name: place.place_name,
                          };
                          setLodgingResults([]);
                          sendMessage(place.place_name);
                        }}
                      >
                        <p className="font-medium text-sm text-stone-700">
                          {place.place_name}
                        </p>
                        <p className="text-xs text-stone-400 mt-0.5">
                          {place.address_name}
                        </p>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {msg.options &&
              msg.options.length > 0 &&
              !msg.placeSearch &&
              idx === messages.length - 1 &&
              (msg.multiSelect ? (
                <div className="flex flex-wrap gap-2 mt-2 ml-1 items-center">
                  {msg.options.map((opt) => {
                    const isSelected = selectedOptions.includes(opt);
                    return (
                      <button
                        key={opt}
                        onClick={() =>
                          setSelectedOptions((prev) =>
                            isSelected
                              ? prev.filter((o) => o !== opt)
                              : [...prev, opt]
                          )
                        }
                        className={`text-xs px-3 py-1.5 rounded-full border transition-colors ${
                          isSelected
                            ? "bg-sky-400 border-sky-400 text-white"
                            : "bg-white border-sky-300 text-sky-600 hover:bg-sky-100"
                        }`}
                      >
                        {opt}
                      </button>
                    );
                  })}
                  <button
                    onClick={() => sendMessage(selectedOptions.join(", "))}
                    disabled={selectedOptions.length === 0}
                    className="text-xs bg-sky-600 text-white px-3 py-1.5 rounded-full hover:bg-sky-700 transition-colors disabled:opacity-40"
                  >
                    선택 완료
                  </button>
                </div>
              ) : (
                <div className="flex flex-wrap gap-2 mt-2 ml-1 items-center">
                  {msg.options.map((opt) => (
                    <button
                      key={opt}
                      onClick={() => sendMessage(opt)}
                      className="text-xs bg-white border border-sky-300 text-sky-600 px-3 py-1.5 rounded-full hover:bg-sky-100 transition-colors"
                    >
                      {opt}
                    </button>
                  ))}
                  {msg.timePicker && (
                    <div className="flex items-center gap-1.5">
                      <input
                        type="time"
                        value={pickedTime}
                        onChange={(e) => setPickedTime(e.target.value)}
                        className="text-xs border border-sky-200 rounded-lg px-2 py-1.5"
                      />
                      <button
                        onClick={() => {
                          if (!pickedTime) return;
                          sendMessage(formatTimeLabel(pickedTime));
                          setPickedTime("");
                        }}
                        disabled={!pickedTime}
                        className="text-xs bg-sky-600 text-white px-3 py-1.5 rounded-full hover:bg-sky-700 transition-colors disabled:opacity-40"
                      >
                        이 시간으로
                      </button>
                    </div>
                  )}
                </div>
              ))}
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
