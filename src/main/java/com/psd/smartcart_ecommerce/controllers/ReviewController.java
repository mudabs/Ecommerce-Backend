package com.psd.smartcart_ecommerce.controllers;

import com.psd.smartcart_ecommerce.config.AppConstants;
import com.psd.smartcart_ecommerce.payload.ReviewDTO;
import com.psd.smartcart_ecommerce.payload.ReviewRequest;
import com.psd.smartcart_ecommerce.services.ReviewService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    @GetMapping("/public/products/{productId}/reviews")
    public ResponseEntity<Map<String, Object>> getReviewsByProduct(
            @PathVariable Long productId,
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize
    ) {
        Page<ReviewDTO> page = reviewService.getReviewsByProduct(productId, pageNumber, pageSize);
        Map<String, Object> response = new HashMap<>();
        response.put("content", page.getContent());
        response.put("pageNumber", page.getNumber());
        response.put("pageSize", page.getSize());
        response.put("totalElements", page.getTotalElements());
        response.put("totalPages", page.getTotalPages());
        response.put("lastPage", page.isLast());
        response.put("averageRating", reviewService.getAverageRating(productId));
        response.put("reviewCount", reviewService.getReviewCount(productId));
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/products/{productId}/reviews")
    public ResponseEntity<ReviewDTO> addReview(
            @PathVariable Long productId,
            @Valid @RequestBody ReviewRequest request
    ) {
        ReviewDTO created = reviewService.addReview(productId, request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @DeleteMapping("/products/{productId}/reviews/{reviewId}")
    public ResponseEntity<Map<String, String>> deleteReview(
            @PathVariable Long productId,
            @PathVariable Long reviewId
    ) {
        reviewService.deleteReview(reviewId);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Review deleted successfully.");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
