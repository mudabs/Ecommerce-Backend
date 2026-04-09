package com.psd.smartcart_ecommerce.ai.memory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Long-term memory: persists user shopping preferences to the database.
 * Tracks favorite categories, price range, and recent searches.
 */
@Service
public class UserPreferenceService {

    private static final int MAX_RECENT_SEARCHES = 20;
    private static final int MAX_FAVORITE_CATEGORIES = 10;

    @Autowired
    private UserPreferenceRepository preferenceRepository;

    public UserPreference getOrCreate(Long userId) {
        return preferenceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserPreference pref = new UserPreference(userId);
                    return preferenceRepository.save(pref);
                });
    }

    public void trackSearch(Long userId, String keyword, String category,
                            Double priceMin, Double priceMax) {
        UserPreference pref = getOrCreate(userId);

        if (keyword != null && !keyword.isBlank()) {
            addRecentSearch(pref, keyword.trim().toLowerCase());
        }

        if (category != null && !category.isBlank()) {
            addFavoriteCategory(pref, category.trim());
        }

        if (priceMin != null && (pref.getPriceMin() == null || priceMin < pref.getPriceMin())) {
            pref.setPriceMin(priceMin);
        }
        if (priceMax != null && (pref.getPriceMax() == null || priceMax > pref.getPriceMax())) {
            pref.setPriceMax(priceMax);
        }

        preferenceRepository.save(pref);
    }

    public void addFavoriteCategory(UserPreference pref, String category) {
        Set<String> categories = parseCommaSeparated(pref.getFavoriteCategories());
        categories.add(category);
        if (categories.size() > MAX_FAVORITE_CATEGORIES) {
            Iterator<String> it = categories.iterator();
            it.next();
            it.remove();
        }
        pref.setFavoriteCategories(String.join(",", categories));
    }

    private void addRecentSearch(UserPreference pref, String keyword) {
        LinkedList<String> searches = new LinkedList<>(parseCommaSeparatedList(pref.getRecentSearches()));
        searches.remove(keyword);
        searches.addFirst(keyword);
        while (searches.size() > MAX_RECENT_SEARCHES) {
            searches.removeLast();
        }
        pref.setRecentSearches(String.join(",", searches));
    }

    public String buildPreferenceSummary(Long userId) {
        UserPreference pref = getOrCreate(userId);
        StringBuilder sb = new StringBuilder();

        String cats = pref.getFavoriteCategories();
        if (cats != null && !cats.isBlank()) {
            sb.append("Favorite categories: ").append(cats).append(". ");
        }

        if (pref.getPriceMin() != null || pref.getPriceMax() != null) {
            sb.append("Typical price range: ");
            if (pref.getPriceMin() != null) sb.append("$").append(String.format("%.0f", pref.getPriceMin()));
            sb.append(" - ");
            if (pref.getPriceMax() != null) sb.append("$").append(String.format("%.0f", pref.getPriceMax()));
            sb.append(". ");
        }

        String searches = pref.getRecentSearches();
        if (searches != null && !searches.isBlank()) {
            List<String> recent = parseCommaSeparatedList(searches);
            int show = Math.min(5, recent.size());
            sb.append("Recent searches: ").append(String.join(", ", recent.subList(0, show))).append(". ");
        }

        return sb.toString().trim();
    }

    private Set<String> parseCommaSeparated(String value) {
        if (value == null || value.isBlank()) return new LinkedHashSet<>();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> parseCommaSeparatedList(String value) {
        if (value == null || value.isBlank()) return new ArrayList<>();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
