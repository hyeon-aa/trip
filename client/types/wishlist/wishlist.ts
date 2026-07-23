export interface Wishlist {
  id: number;
  name: string;
  lat: number;
  lng: number;
  category: string;
  address: string;
  kakaoPlaceId: string;
  memo: string | null;
}
