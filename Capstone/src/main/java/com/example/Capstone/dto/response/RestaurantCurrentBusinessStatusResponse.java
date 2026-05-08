package com.example.Capstone.dto.response;

public record RestaurantCurrentBusinessStatusResponse(
        String status,
        String label,
        Boolean isOpen,
        String checkedAt,
        String day,
        String time,
        String reason
) {
    public static RestaurantCurrentBusinessStatusResponse unknown(
            String checkedAt,
            String day,
            String time
    ) {
        return new RestaurantCurrentBusinessStatusResponse(
                "UNKNOWN",
                "확인 불가",
                false,
                checkedAt,
                day,
                time,
                null
        );
    }
}
