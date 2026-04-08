package com.psd.smartcart_ecommerce.services;

import com.psd.smartcart_ecommerce.exceptions.APIException;
import com.psd.smartcart_ecommerce.exceptions.ResourceNotFoundException;
import com.psd.smartcart_ecommerce.models.Product;
import com.psd.smartcart_ecommerce.models.Review;
import com.psd.smartcart_ecommerce.models.User;
import com.psd.smartcart_ecommerce.payload.ReviewDTO;
import com.psd.smartcart_ecommerce.payload.ReviewRequest;
import com.psd.smartcart_ecommerce.repositories.ProductRepository;
import com.psd.smartcart_ecommerce.repositories.ReviewRepository;
import com.psd.smartcart_ecommerce.util.AuthUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class ReviewServiceImpl implements ReviewService {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AuthUtil authUtil;

    private ReviewDTO mapToDto(Review review) {
        ReviewDTO dto = new ReviewDTO();
        dto.setReviewId(review.getReviewId());
        dto.setProductId(review.getProduct().getProductId());
        dto.setUserId(review.getUser().getUserId());
        dto.setUsername(review.getUser().getUserName());
        dto.setRating(review.getRating());
        dto.setTitle(review.getTitle());
        dto.setComment(review.getComment());
        dto.setCreatedAt(review.getCreatedAt());
        return dto;
    }

    @Override
    public Page<ReviewDTO> getReviewsByProduct(Long productId, int page, int size) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product", "productId", productId);
        }
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return reviewRepository.findByProduct_ProductId(productId, pageRequest).map(this::mapToDto);
    }

    @Override
    public ReviewDTO addReview(Long productId, ReviewRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        User user = authUtil.loggedInUser();

        reviewRepository.findByProduct_ProductIdAndUser_UserId(productId, user.getUserId())
                .ifPresent(existing -> {
                    throw new APIException("You have already reviewed this product. Delete your existing review to add a new one.");
                });

        Review review = new Review();
        review.setProduct(product);
        review.setUser(user);
        review.setRating(request.getRating());
        review.setTitle(request.getTitle());
        review.setComment(request.getComment());

        return mapToDto(reviewRepository.save(review));
    }

    @Override
    public void deleteReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "reviewId", reviewId));

        User user = authUtil.loggedInUser();

        boolean isOwner = review.getUser().getUserId().equals(user.getUserId());
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(r -> r.getRoleName().name().equals("ROLE_ADMIN"));

        if (!isOwner && !isAdmin) {
            throw new APIException("You are not authorized to delete this review.");
        }

        reviewRepository.delete(review);
    }

    @Override
    public Double getAverageRating(Long productId) {
        return reviewRepository.findAverageRatingByProductId(productId);
    }

    @Override
    public long getReviewCount(Long productId) {
        return reviewRepository.countByProduct_ProductId(productId);
    }
}
