package com.example.Capstone.service.support;

import java.util.List;

public final class RestaurantCategoryResolver {

    private static final List<CategoryRule> RULES = List.of(
            rule("중식", "중식", "중국", "짜장", "짬뽕", "마라", "양꼬치"),
            rule("일식", "일식", "초밥", "스시", "라멘", "우동", "소바", "돈가스", "돈까스", "카츠", "생선회", "이자카야"),
            rule("양식", "양식", "파스타", "스파게티", "스테이크", "피자", "햄버거", "버거", "샌드위치", "브런치", "타코", "멕시코"),
            rule("분식", "분식", "김밥", "떡볶이", "순대", "튀김"),
            rule("카페/디저트", "카페", "커피", "디저트", "베이커리", "빵", "케이크", "와플"),
            rule("치킨", "치킨", "닭강정"),
            rule("술집", "술집", "주점", "호프", "맥주", "바"),
            rule("한식", "한식", "국밥", "찌개", "탕", "해장국", "고기", "구이", "갈비", "삼겹살", "보쌈", "족발", "냉면", "칼국수", "백반", "오리", "닭갈비")
    );

    private RestaurantCategoryResolver() {
    }

    public static String resolvePrimaryCategory(String... categoryNames) {
        String joined = String.join(" ", normalizeAll(categoryNames));
        if (joined.isBlank()) {
            return null;
        }

        String normalized = joined.toLowerCase();
        for (CategoryRule rule : RULES) {
            if (rule.matches(normalized)) {
                return rule.primaryCategoryName();
            }
        }

        return "기타";
    }

    private static List<String> normalizeAll(String... values) {
        if (values == null) {
            return List.of();
        }

        return java.util.Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }

    private static CategoryRule rule(String primaryCategoryName, String... keywords) {
        return new CategoryRule(primaryCategoryName, List.of(keywords));
    }

    private record CategoryRule(String primaryCategoryName, List<String> keywords) {
        private boolean matches(String value) {
            return keywords.stream()
                    .map(String::toLowerCase)
                    .anyMatch(value::contains);
        }
    }
}
