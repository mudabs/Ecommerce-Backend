package com.psd.smartcart_ecommerce.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReviewDTO {
    private Long reviewId;
    private Long productId;
    private Long userId;
    private String username;
    private Integer rating;
    private String title;
    private String comment;
    private LocalDateTime createdAt;
}
