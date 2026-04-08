package com.psd.smartcart_ecommerce.services;

import com.psd.smartcart_ecommerce.payload.ReviewDTO;
import com.psd.smartcart_ecommerce.payload.ReviewRequest;
import org.springframework.data.domain.Page;

public interface ReviewService {
    Page<ReviewDTO> getReviewsByProduct(Long productId, int page, int size);
    ReviewDTO addReview(Long productId, ReviewRequest request);
    void deleteReview(Long reviewId);
    Double getAverageRating(Long productId);
    long getReviewCount(Long productId);
}
