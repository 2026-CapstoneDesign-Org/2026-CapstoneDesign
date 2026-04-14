package com.example.Capstone.runner;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.example.Capstone.dto.request.ImportRestaurantSeedRequest;
import com.example.Capstone.dto.response.RestaurantSeedImportResponse;
import com.example.Capstone.service.RestaurantSeedImportService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class SeedImportRunner implements ApplicationRunner {

    private final Environment environment;
    private final RestaurantSeedImportService restaurantSeedImportService;
    private final ConfigurableApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) {
        if (!environment.getProperty("seed.import.enabled", Boolean.class, false)) {
            return;
        }

        RestaurantSeedImportResponse response = restaurantSeedImportService.importSeed(new ImportRestaurantSeedRequest(
                environment.getProperty("seed.import.restaurants-file-path"),
                environment.getProperty("seed.import.menu-items-file-path"),
                environment.getProperty("seed.import.tags-file-path"),
                environment.getProperty("seed.import.restaurant-tags-file-path")
        ));

        log.info(
                "seed import completed: restaurants={} menuItems={} tags={} restaurantTags={} createdRestaurants={} updatedRestaurants={}",
                response.totalRestaurantCount(),
                response.totalMenuItemCount(),
                response.totalTagCount(),
                response.totalRestaurantTagCount(),
                response.createdRestaurantCount(),
                response.updatedRestaurantCount()
        );

        if (environment.getProperty("seed.import.exit-after-run", Boolean.class, false)) {
            int exitCode = org.springframework.boot.SpringApplication.exit(applicationContext, () -> 0);
            System.exit(exitCode);
        }
    }
}
