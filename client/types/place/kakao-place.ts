export interface KakaoPlaceDocument {
  id: string;
  place_name: string;
  x: string;
  y: string;
  category_name: string;
  address_name: string;
}

export interface KakaoPlaceResponse {
  documents: KakaoPlaceDocument[];
}
