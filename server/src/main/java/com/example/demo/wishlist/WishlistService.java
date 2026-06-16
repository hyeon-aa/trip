package com.example.demo.wishlist;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class WishlistService {

    private final WishlistRepository wishlistRepository;

    public WishlistService(WishlistRepository wishlistRepository) {
        this.wishlistRepository = wishlistRepository;
    }

    public Wishlist add(Wishlist wishlist) {
        return wishlistRepository.save(wishlist);
    }

    public List<Wishlist> getAll() {
        return wishlistRepository.findAll();
    }

    public void delete(Long id) {
        wishlistRepository.deleteById(id);
    }
}