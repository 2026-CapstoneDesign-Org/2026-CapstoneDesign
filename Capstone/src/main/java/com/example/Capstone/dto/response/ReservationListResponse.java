package com.example.Capstone.dto.response;

import java.util.List;

public record ReservationListResponse(
        List<ReservationResponse> items
) {
}
