package com.example.demo.wishlist;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.demo.wishlist.dto.CreateWishlistRequest;
import com.example.demo.wishlist.dto.WishlistResponse;

@Service
public class WishlistService {

    private final WishlistRepository wishlistRepository;

    public WishlistService(WishlistRepository wishlistRepository) {
        this.wishlistRepository = wishlistRepository;
    }

    public WishlistResponse add(CreateWishlistRequest request) {

        Wishlist wishlist = new Wishlist();

        wishlist.setName(request.name());
        wishlist.setCategory(request.category());
        wishlist.setAddress(request.address());
        wishlist.setLat(request.lat());
        wishlist.setLng(request.lng());

        Wishlist saved = wishlistRepository.save(wishlist);

        return toResponse(saved);
    }

    public List<WishlistResponse> getAll() {
        return wishlistRepository.findAll()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    public void delete(Long id) {
        wishlistRepository.deleteById(id);
    }

    private WishlistResponse toResponse(Wishlist wishlist) {
        return new WishlistResponse(
            wishlist.getId(),
            wishlist.getName(),
            wishlist.getCategory(),
            wishlist.getAddress(),
            wishlist.getLat(),
            wishlist.getLng()
        );
    }
}