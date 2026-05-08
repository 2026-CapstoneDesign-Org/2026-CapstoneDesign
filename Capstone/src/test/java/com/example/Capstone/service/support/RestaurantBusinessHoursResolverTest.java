package com.example.Capstone.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.Capstone.dto.response.RestaurantBusinessHoursDisplayResponse;
import com.example.Capstone.dto.response.RestaurantBusinessHoursResponse;
import com.example.Capstone.dto.response.RestaurantCurrentBusinessStatusResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

class RestaurantBusinessHoursResolverTest {

    private final RestaurantBusinessHoursResolver resolver = new RestaurantBusinessHoursResolver(new ObjectMapper());

    @Test
    void parseBusinessHoursAndBuildNaverLikeDisplayRows() {
        String raw = """
                {"source":"pcmap_business_hours","days":[{"day":"화(5/5)","businessHours":{"start":"11:30","end":"22:00"},"breakHours":[],"lastOrderTimes":[{"type":"영업시간","time":"20:50"}],"description":"어린이날","showEndsNextDay":false},{"day":"수","businessHours":{"start":"11:30","end":"22:00"},"breakHours":[],"lastOrderTimes":[{"type":"영업시간","time":"20:50"}],"description":null,"showEndsNextDay":false}],"comingIrregularClosedDays":[],"comingRegularClosedDays":null,"freeText":"라스트오더는 20시50분입니다!"}
                """.trim();

        RestaurantBusinessHoursResponse businessHours = resolver.parse(raw);
        RestaurantCurrentBusinessStatusResponse currentStatus = new RestaurantCurrentBusinessStatusResponse(
                "OPEN",
                "영업 중",
                true,
                "2026-05-05T12:00:00+09:00",
                "화",
                "12:00",
                "20:50에 라스트오더"
        );

        RestaurantBusinessHoursDisplayResponse display = resolver.resolveDisplay(businessHours, currentStatus);

        assertThat(display.statusLine()).isEqualTo("영업 중 · 20:50에 라스트오더");
        assertThat(display.rows().get(0).dayText()).isEqualTo("화(5/5) 어린이날");
        assertThat(display.rows().get(0).timeText()).isEqualTo("11:30 - 22:00");
        assertThat(display.rows().get(0).subTexts()).containsExactly("20:50 라스트오더");
        assertThat(display.noticeText()).isEqualTo("- 라스트오더는 20시50분입니다!");
    }

    @Test
    void buildEverydaySummaryLine() {
        RestaurantBusinessHoursResponse businessHours = new RestaurantBusinessHoursResponse(
                "pcmap_business_hours",
                List.of(new RestaurantBusinessHoursResponse.DayHours(
                        "매일",
                        new RestaurantBusinessHoursResponse.TimeRange("09:00", "23:00"),
                        List.of(),
                        List.of(),
                        null,
                        false
                )),
                List.of(),
                null,
                null
        );
        RestaurantCurrentBusinessStatusResponse currentStatus = new RestaurantCurrentBusinessStatusResponse(
                "OPEN",
                "영업 중",
                true,
                "2026-05-05T12:00:00+09:00",
                "화",
                "12:00",
                "23:00에 영업 종료"
        );

        RestaurantBusinessHoursDisplayResponse display = resolver.resolveDisplay(businessHours, currentStatus);

        assertThat(display.statusLine()).isEqualTo("영업 중 · 23:00에 영업 종료");
        assertThat(display.summaryLine()).isEqualTo("매일 09:00 - 23:00");
        assertThat(display.rows().get(0).isToday()).isTrue();
    }
}
