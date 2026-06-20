export interface ChatMessage {
  role: "user" | "assistant";
  content: string;
}

export const sendPlanChat = async (
  message: string,
  history: ChatMessage[]
): Promise<string> => {
  const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/plan/chat`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ message, history }),
  });

  const text = await res.text();
  return text;
};
