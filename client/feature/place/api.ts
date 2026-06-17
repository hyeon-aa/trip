import {
  KakaoPlaceDocument,
  KakaoPlaceResponse,
} from "@/types/place/kakao-place";
import { Place } from "@/types/place/place";
const BASE_URL = process.env.NEXT_PUBLIC_API_URL;

export const searchPlaces = async (query: string): Promise<Place[]> => {
  const res = await fetch(`${BASE_URL}/place/search?query=${query}`);
  const data: KakaoPlaceResponse = await res.json();
  return data.documents.map((doc: KakaoPlaceDocument) => ({
    id: doc.id,
    place_name: doc.place_name,
    lat: parseFloat(doc.y),
    lng: parseFloat(doc.x),
    category_name: doc.category_name,
    address_name: doc.address_name,
  }));
};
