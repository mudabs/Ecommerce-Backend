package com.psd.smartcart_ecommerce.ai.memory;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@NoArgsConstructor
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** Comma-separated list of favorite category names */
    @Column(length = 1000)
    private String favoriteCategories;

    private Double priceMin;

    private Double priceMax;

    /** Comma-separated recent search keywords */
    @Column(length = 2000)
    private String recentSearches;

    public UserPreference(Long userId) {
        this.userId = userId;
    }
}
