package com.example.Capstone.service.support;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.example.Capstone.dto.response.RestaurantBusinessHoursDisplayResponse;
import com.example.Capstone.dto.response.RestaurantBusinessHoursResponse;
import com.example.Capstone.dto.response.RestaurantBusinessHoursResponse.DayHours;
import com.example.Capstone.dto.response.RestaurantBusinessHoursResponse.TimeRange;
import com.example.Capstone.dto.response.RestaurantCurrentBusinessStatusResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RestaurantBusinessHoursResolver {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final ObjectMapper objectMapper;

    public RestaurantBusinessHoursResponse parse(String businessHoursRaw) {
        if (businessHoursRaw == null || businessHoursRaw.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(businessHoursRaw, RestaurantBusinessHoursResponse.class);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    public RestaurantCurrentBusinessStatusResponse resolveCurrentStatus(RestaurantBusinessHoursResponse businessHours) {
        ZonedDateTime now = ZonedDateTime.now(SERVICE_ZONE);
        String currentDay = toKoreanDay(now.getDayOfWeek());
        int currentMinute = now.getHour() * 60 + now.getMinute();

        if (businessHours == null || businessHours.days() == null || businessHours.days().isEmpty()) {
            return RestaurantCurrentBusinessStatusResponse.unknown(
                    now.toOffsetDateTime().toString(),
                    currentDay,
                    formatMinute(currentMinute)
            );
        }

        Optional<RestaurantCurrentBusinessStatusResponse> previousOvernightStatus =
                findMatchingDay(businessHours.days(), toKoreanDay(now.getDayOfWeek().minus(1)))
                        .filter(this::endsNextDay)
                        .filter(day -> isWithinPreviousOvernightRange(day, currentMinute))
                        .map(day -> statusForOpenDay(day, now, currentDay, currentMinute));
        if (previousOvernightStatus.isPresent()) {
            return previousOvernightStatus.get();
        }

        Optional<DayHours> today = findMatchingDay(businessHours.days(), currentDay);
        if (today.isEmpty()) {
            return RestaurantCurrentBusinessStatusResponse.unknown(
                    now.toOffsetDateTime().toString(),
                    currentDay,
                    formatMinute(currentMinute)
            );
        }

        DayHours day = today.get();
        if (day.businessHours() == null) {
            String description = normalize(day.description());
            return new RestaurantCurrentBusinessStatusResponse(
                    "CLOSED",
                    description.contains("휴무") ? "휴무" : "영업 안함",
                    false,
                    now.toOffsetDateTime().toString(),
                    currentDay,
                    formatMinute(currentMinute),
                    description.isBlank() ? null : description
            );
        }

        if (!isWithinBusinessRange(day, currentMinute)) {
            return statusForClosedDay(day, now, currentDay, currentMinute);
        }

        Optional<TimeRange> breakRange = findCurrentBreakRange(day, currentMinute);
        if (breakRange.isPresent()) {
            return new RestaurantCurrentBusinessStatusResponse(
                    "BREAK_TIME",
                    "브레이크타임",
                    false,
                    now.toOffsetDateTime().toString(),
                    currentDay,
                    formatMinute(currentMinute),
                    formatTime(breakRange.get().end()).map(time -> time + "에 영업 시작").orElse(null)
            );
        }

        return statusForOpenDay(day, now, currentDay, currentMinute);
    }

    public RestaurantBusinessHoursDisplayResponse resolveDisplay(
            RestaurantBusinessHoursResponse businessHours,
            RestaurantCurrentBusinessStatusResponse currentStatus
    ) {
        String statusLine = resolveStatusLine(currentStatus);
        if (businessHours == null || businessHours.days() == null || businessHours.days().isEmpty()) {
            return new RestaurantBusinessHoursDisplayResponse(
                    statusLine,
                    null,
                    List.of(),
                    null
            );
        }

        String currentDay = toKoreanDay(ZonedDateTime.now(SERVICE_ZONE).getDayOfWeek());
        List<RestaurantBusinessHoursDisplayResponse.Row> rows = businessHours.days().stream()
                .map(day -> toDisplayRow(day, currentDay))
                .toList();

        RestaurantBusinessHoursDisplayResponse.Row summaryRow = rows.stream()
                .filter(RestaurantBusinessHoursDisplayResponse.Row::isToday)
                .findFirst()
                .orElse(rows.get(0));

        return new RestaurantBusinessHoursDisplayResponse(
                statusLine,
                toSingleLine(summaryRow),
                rows,
                resolveNoticeText(businessHours)
        );
    }

    private String resolveStatusLine(RestaurantCurrentBusinessStatusResponse currentStatus) {
        if (currentStatus == null || normalize(currentStatus.label()).isBlank()) {
            return null;
        }

        String reason = normalize(currentStatus.reason());
        if (reason.isBlank()) {
            return currentStatus.label();
        }
        return currentStatus.label() + " · " + reason;
    }

    private RestaurantBusinessHoursDisplayResponse.Row toDisplayRow(DayHours day, String currentDay) {
        String dayText = resolveDayText(day);
        TimeRange businessHours = day.businessHours();
        List<String> subTexts = new ArrayList<>();

        if (day.breakHours() != null) {
            day.breakHours().stream()
                    .map(this::formatBreakText)
                    .filter(value -> !value.isBlank())
                    .forEach(subTexts::add);
        }

        if (day.lastOrderTimes() != null) {
            day.lastOrderTimes().stream()
                    .map(lastOrderTime -> formatTime(lastOrderTime.time())
                            .map(time -> time + " 라스트오더")
                            .orElse(""))
                    .filter(value -> !value.isBlank())
                    .forEach(subTexts::add);
        }

        return new RestaurantBusinessHoursDisplayResponse.Row(
                dayText,
                businessHours == null ? null : formatRangeText(businessHours),
                subTexts,
                isDayMatch(day.day(), currentDay) || "매일".equals(normalize(day.day())),
                businessHours == null
        );
    }

    private String resolveDayText(DayHours day) {
        String dayText = normalize(day.day());
        String description = normalize(day.description());
        if (description.isBlank()) {
            return dayText;
        }
        if (dayText.contains(description)) {
            return dayText;
        }
        return dayText + " " + description;
    }

    private String formatBreakText(TimeRange range) {
        String rangeText = formatRangeText(range);
        if (rangeText.isBlank()) {
            return "";
        }
        return rangeText + " 브레이크타임";
    }

    private String formatRangeText(TimeRange range) {
        Optional<String> start = formatTime(range.start());
        Optional<String> end = formatTime(range.end());
        if (start.isEmpty() || end.isEmpty()) {
            return "";
        }
        return start.get() + " - " + end.get();
    }

    private String toSingleLine(RestaurantBusinessHoursDisplayResponse.Row row) {
        if (row == null) {
            return null;
        }
        String timeText = normalize(row.timeText());
        if (timeText.isBlank()) {
            return row.dayText();
        }
        return row.dayText() + " " + timeText;
    }

    private String resolveNoticeText(RestaurantBusinessHoursResponse businessHours) {
        String freeText = normalize(businessHours.freeText());
        if (!freeText.isBlank()) {
            return freeText.startsWith("-") ? freeText : "- " + freeText;
        }

        String regularClosedDays = normalize(businessHours.comingRegularClosedDays());
        if (!regularClosedDays.isBlank()) {
            return "- " + regularClosedDays;
        }

        return null;
    }

    private Optional<DayHours> findMatchingDay(List<DayHours> days, String koreanDay) {
        return days.stream()
                .filter(day -> isDayMatch(day.day(), koreanDay))
                .findFirst()
                .or(() -> days.stream()
                        .filter(day -> "매일".equals(normalize(day.day())))
                        .findFirst());
    }

    private boolean isDayMatch(String rawDay, String koreanDay) {
        String day = normalize(rawDay);
        return day.equals(koreanDay) || day.startsWith(koreanDay + "(");
    }

    private boolean endsNextDay(DayHours day) {
        TimeRange range = day.businessHours();
        if (range == null) {
            return false;
        }

        Integer start = parseMinute(range.start());
        Integer end = parseMinute(range.end());
        return Boolean.TRUE.equals(day.showEndsNextDay())
                || start != null && end != null && end <= start;
    }

    private boolean isWithinPreviousOvernightRange(DayHours day, int currentMinute) {
        Integer end = parseMinute(day.businessHours().end());
        return end != null && currentMinute < end;
    }

    private boolean isWithinBusinessRange(DayHours day, int currentMinute) {
        TimeRange range = day.businessHours();
        Integer start = parseMinute(range.start());
        Integer end = parseMinute(range.end());
        if (start == null || end == null) {
            return false;
        }

        if (end == 1440) {
            return currentMinute >= start;
        }

        if (endsNextDay(day)) {
            return currentMinute >= start || currentMinute < end;
        }

        return currentMinute >= start && currentMinute < end;
    }

    private Optional<TimeRange> findCurrentBreakRange(DayHours day, int currentMinute) {
        if (day.breakHours() == null) {
            return Optional.empty();
        }

        return day.breakHours().stream()
                .filter(range -> isWithinRange(range, currentMinute))
                .findFirst();
    }

    private boolean isWithinRange(TimeRange range, int currentMinute) {
        Integer start = parseMinute(range.start());
        Integer end = parseMinute(range.end());
        if (start == null || end == null) {
            return false;
        }

        if (end <= start) {
            return currentMinute >= start || currentMinute < end;
        }

        return currentMinute >= start && currentMinute < end;
    }

    private RestaurantCurrentBusinessStatusResponse statusForOpenDay(
            DayHours day,
            ZonedDateTime now,
            String currentDay,
            int currentMinute
    ) {
        return new RestaurantCurrentBusinessStatusResponse(
                "OPEN",
                "영업 중",
                true,
                now.toOffsetDateTime().toString(),
                currentDay,
                formatMinute(currentMinute),
                resolveOpenReason(day, currentMinute)
        );
    }

    private String resolveOpenReason(DayHours day, int currentMinute) {
        Optional<String> lastOrderReason = firstFutureLastOrderTime(day, currentMinute)
                .map(time -> time + "에 라스트오더");
        if (lastOrderReason.isPresent()) {
            return lastOrderReason.get();
        }

        return formatTime(day.businessHours().end())
                .map(time -> time + "에 영업 종료")
                .orElse(null);
    }

    private RestaurantCurrentBusinessStatusResponse statusForClosedDay(
            DayHours day,
            ZonedDateTime now,
            String currentDay,
            int currentMinute
    ) {
        Integer start = parseMinute(day.businessHours().start());
        String label = start != null && currentMinute < start ? "영업 전" : "영업 종료";
        String reason = start != null && currentMinute < start
                ? formatMinute(start) + "에 영업 시작"
                : null;

        return new RestaurantCurrentBusinessStatusResponse(
                "CLOSED",
                label,
                false,
                now.toOffsetDateTime().toString(),
                currentDay,
                formatMinute(currentMinute),
                reason
        );
    }

    private Optional<String> firstFutureLastOrderTime(DayHours day, int currentMinute) {
        if (day.lastOrderTimes() == null) {
            return Optional.empty();
        }

        int effectiveCurrentMinute = effectiveCurrentMinute(day, currentMinute);
        return day.lastOrderTimes().stream()
                .map(lastOrderTime -> normalize(lastOrderTime.time()))
                .filter(value -> !value.isBlank())
                .filter(value -> {
                    Integer lastOrderMinute = parseMinute(value);
                    return lastOrderMinute != null
                            && effectiveMinute(day, lastOrderMinute) > effectiveCurrentMinute;
                })
                .findFirst();
    }

    private int effectiveCurrentMinute(DayHours day, int currentMinute) {
        Integer end = parseMinute(day.businessHours().end());
        if (endsNextDay(day) && end != null && currentMinute < end) {
            return currentMinute + 1440;
        }
        return currentMinute;
    }

    private int effectiveMinute(DayHours day, int minute) {
        Integer start = parseMinute(day.businessHours().start());
        if (endsNextDay(day) && start != null && minute < start) {
            return minute + 1440;
        }
        return minute;
    }

    private Optional<String> formatTime(String value) {
        Integer minute = parseMinute(value);
        if (minute == null) {
            return Optional.empty();
        }
        return Optional.of(formatMinute(minute));
    }

    private Integer parseMinute(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return null;
        }

        String[] parts = normalized.split(":");
        if (parts.length != 2) {
            return null;
        }

        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 24 || minute < 0 || minute > 59) {
                return null;
            }
            if (hour == 24 && minute != 0) {
                return null;
            }
            return hour * 60 + minute;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String formatMinute(int minute) {
        int hour = minute / 60;
        int resolvedMinute = minute % 60;
        return "%02d:%02d".formatted(hour, resolvedMinute);
    }

    private String toKoreanDay(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "월";
            case TUESDAY -> "화";
            case WEDNESDAY -> "수";
            case THURSDAY -> "목";
            case FRIDAY -> "금";
            case SATURDAY -> "토";
            case SUNDAY -> "일";
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
