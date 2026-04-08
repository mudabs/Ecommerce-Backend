package com.psd.smartcart_ecommerce.payload;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ReviewRequest {

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private Integer rating;

    @NotBlank(message = "Review title is required")
    @Size(min = 3, max = 120, message = "Title must be 3–120 characters")
    private String title;

    @NotBlank(message = "Review comment is required")
    @Size(min = 10, max = 2000, message = "Comment must be 10–2000 characters")
    private String comment;
}
