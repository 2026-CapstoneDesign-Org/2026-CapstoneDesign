package com.example.Capstone.repository;

import java.util.List;

public interface HiddenGemRecommendationRepository {

    List<HiddenGemRestaurantRow> findHiddenGemCandidates(
            String regionTownName,
            int smoothingConstant
    );
}
