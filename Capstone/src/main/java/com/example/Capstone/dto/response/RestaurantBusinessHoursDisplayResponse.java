package com.example.Capstone.dto.response;

import java.util.List;

public record RestaurantBusinessHoursDisplayResponse(
        String statusLine,
        String summaryLine,
        List<Row> rows,
        String noticeText
) {
    public record Row(
            String dayText,
            String timeText,
            List<String> subTexts,
            Boolean isToday,
            Boolean isClosed
    ) {
    }
}
