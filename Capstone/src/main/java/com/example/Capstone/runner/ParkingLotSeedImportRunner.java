package com.example.Capstone.runner;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.example.Capstone.dto.request.ImportParkingLotSeedRequest;
import com.example.Capstone.dto.response.ParkingLotSeedImportResponse;
import com.example.Capstone.service.ParkingLotSeedImportService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class ParkingLotSeedImportRunner implements ApplicationRunner {

    private static final String OPEN_API_SOURCE = "gg-openapi";

    private final Environment environment;
    private final ParkingLotSeedImportService parkingLotSeedImportService;
    private final ConfigurableApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) {
        if (!environment.getProperty("parking-lot.seed.import.enabled", Boolean.class, false)) {
            return;
        }

        String source = environment.getProperty("parking-lot.seed.import.source", "file");
        ParkingLotSeedImportResponse response = OPEN_API_SOURCE.equalsIgnoreCase(source)
                ? parkingLotSeedImportService.importGyeonggiOpenApiSeed()
                : parkingLotSeedImportService.importSeed(new ImportParkingLotSeedRequest(
                        environment.getProperty("parking-lot.seed.import.file-path")
                ));

        log.info(
                "parking lot seed import completed: total={} created={} updated={}",
                response.totalParkingLotCount(),
                response.createdParkingLotCount(),
                response.updatedParkingLotCount()
        );

        if (environment.getProperty("parking-lot.seed.import.exit-after-run", Boolean.class, false)) {
            int exitCode = org.springframework.boot.SpringApplication.exit(applicationContext, () -> 0);
            System.exit(exitCode);
        }
    }
}
