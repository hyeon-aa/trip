import { Place } from "@/types/place/place";
import { Wishlist } from "@/types/wishlist/wishlist";

const BASE_URL = process.env.NEXT_PUBLIC_API_URL;

export const addToWishlist = async (place: Place): Promise<void> => {
  await fetch(`${BASE_URL}/wishlist`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      name: place.place_name,
      lat: place.lat,
      lng: place.lng,
      category: place.category_name,
      address: place.address_name,
      kakaoPlaceId: place.id,
    }),
  });
};

export const getWishlist = async (): Promise<Wishlist[]> => {
  const res = await fetch(`${BASE_URL}/wishlist`);
  return res.json();
};

export const deleteWishlist = async (id: number): Promise<void> => {
  await fetch(`${BASE_URL}/wishlist/${id}`, {
    method: "DELETE",
  });
};

export const updateWishlistMemo = async (
  id: number,
  memo: string
): Promise<Wishlist> => {
  const res = await fetch(`${BASE_URL}/wishlist/${id}/memo`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ memo }),
  });
  return res.json();
};

// Spring AI Tool 로드맵 2단계(docs/SPRING_AI.md) — 자연어로 위시리스트에
// 추가 요청. 이미 jeju_place DB에 있는 장소만 추가되고(좌표는 서버가 DB에서
// 그대로 가져옴), 없는 이름은 실패 메시지가 그대로 reply에 담겨 온다.
export const sendWishlistChat = async (message: string): Promise<string> => {
  const res = await fetch(`${BASE_URL}/wishlist/chat`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ message }),
  });
  const data = await res.json();
  return data.reply;
};
