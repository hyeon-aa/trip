package com.example.demo.wishlist.dto;

public record CreateWishlistRequest(
    String name,
    String category,
    String address,
    Double lat,
    Double lng
) {
}