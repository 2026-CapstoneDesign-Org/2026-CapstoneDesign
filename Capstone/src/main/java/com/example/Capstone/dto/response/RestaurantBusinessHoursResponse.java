package com.example.Capstone.dto.response;

import java.util.List;
import java.util.Map;

public record RestaurantBusinessHoursResponse(
        String source,
        List<DayHours> days,
        List<Map<String, Object>> comingIrregularClosedDays,
        String comingRegularClosedDays,
        String freeText
) {
    public record DayHours(
            String day,
            TimeRange businessHours,
            List<TimeRange> breakHours,
            List<LastOrderTime> lastOrderTimes,
            String description,
            Boolean showEndsNextDay
    ) {
    }

    public record TimeRange(
            String start,
            String end
    ) {
    }

    public record LastOrderTime(
            String type,
            String time
    ) {
    }
}
