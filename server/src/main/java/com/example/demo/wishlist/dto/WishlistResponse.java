package com.example.demo.wishlist.dto;

public record WishlistResponse(
    Long id,
    String name,
    String category,
    String address,
    Double lat,
    Double lng
) {
}