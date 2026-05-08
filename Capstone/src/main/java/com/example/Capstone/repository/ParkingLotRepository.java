package com.example.Capstone.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.Capstone.domain.ParkingLot;

@Repository
public interface ParkingLotRepository extends JpaRepository<ParkingLot, Long> {

    List<ParkingLot> findAllByParkingManagementNumber(String parkingManagementNumber);

    @Query("""
            select p
            from ParkingLot p
            where p.parkingManagementNumber = :parkingManagementNumber
              and p.parkingLotName = :parkingLotName
              and p.parkingLotDivision = :parkingLotDivision
              and p.parkingLotType = :parkingLotType
              and (p.roadAddress = :roadAddress or (p.roadAddress is null and :roadAddress is null))
              and (p.lotAddress = :lotAddress or (p.lotAddress is null and :lotAddress is null))
              and (p.lat = :lat or (p.lat is null and :lat is null))
              and (p.lng = :lng or (p.lng is null and :lng is null))
            """)
    List<ParkingLot> findSeedMatches(
            @Param("parkingManagementNumber") String parkingManagementNumber,
            @Param("parkingLotName") String parkingLotName,
            @Param("parkingLotDivision") String parkingLotDivision,
            @Param("parkingLotType") String parkingLotType,
            @Param("roadAddress") String roadAddress,
            @Param("lotAddress") String lotAddress,
            @Param("lat") BigDecimal lat,
            @Param("lng") BigDecimal lng
    );

    List<ParkingLot> findAllByLatIsNotNullAndLngIsNotNull();

    List<ParkingLot> findAllByParkingLotDivisionAndLatIsNotNullAndLngIsNotNull(String parkingLotDivision);
}
