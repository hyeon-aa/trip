package com.example.demo.wishlist;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.wishlist.dto.CreateWishlistRequest;
import com.example.demo.wishlist.dto.UpdateWishlistMemoRequest;
import com.example.demo.wishlist.dto.WishlistResponse;

@RestController
@RequestMapping("/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @PostMapping
    public ResponseEntity<WishlistResponse> add(
        @RequestBody CreateWishlistRequest request
    ) {
        return ResponseEntity.ok(
            wishlistService.add(request)
        );
    }

    @GetMapping
    public ResponseEntity<List<WishlistResponse>> getAll() {
        return ResponseEntity.ok(
            wishlistService.getAll()
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable Long id
    ) {
        wishlistService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/memo")
    public ResponseEntity<WishlistResponse> updateMemo(
        @PathVariable Long id,
        @RequestBody UpdateWishlistMemoRequest request
    ) {
        return ResponseEntity.ok(
            wishlistService.updateMemo(id, request)
        );
    }
}