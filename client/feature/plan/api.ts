export interface Place {
  id?: string;
  name: string;
  category: string;
  reason: string;
  recommendedTime: string;
  lat?: number;
  lng?: number;
}

export interface Day {
  day: number;
  places: Place[];
}

export interface Schedule {
  days: Day[];
}

export interface ChatMessage {
  role: "user" | "assistant";
  content: string;
  options?: string[];
  multiSelect?: boolean;
  timePicker?: boolean;
  placeSearch?: boolean;
}

export const sendPlanChat = async (
  message: string,
  history: ChatMessage[],
  onMessage: (content: string) => void
): Promise<string> => {
  const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/plan/chat`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
    },
    body: JSON.stringify({ message, history }),
  });

  if (!res.body) {
    throw new Error("Response body is null");
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();

  let buffer = "";
  let fullText = "";

  while (true) {
    const { done, value } = await reader.read();

    if (done) break;

    buffer += decoder.decode(value, { stream: true });

    const events = buffer.split("\n\n");
    buffer = events.pop() || "";

    for (const event of events) {
      const lines = event.split("\n");

      for (const line of lines) {
        if (!line.startsWith("data:")) continue;

        const data = line.replace(/^data:\s*/, "");

        fullText += data;

        onMessage(fullText);
      }
    }
  }

  return fullText;
};
