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
