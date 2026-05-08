package com.example.Capstone.domain;

import java.math.BigDecimal;

import com.example.Capstone.domain.base.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "parking_lots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParkingLot extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String parkingManagementNumber;

    @Column(nullable = false, length = 100)
    private String parkingLotName;

    @Column(nullable = false, length = 20)
    private String parkingLotDivision;

    @Column(nullable = false, length = 20)
    private String parkingLotType;

    @Column(length = 255)
    private String roadAddress;

    @Column(length = 255)
    private String lotAddress;

    @Column
    private Integer parkingCapacity;

    @Column(length = 50)
    private String alternateNoDivision;

    @Column(length = 50)
    private String weekdayOperatingHours;

    @Column(length = 50)
    private String saturdayOperatingHours;

    @Column(length = 50)
    private String holidayOperatingHours;

    @Column(precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(precision = 10, scale = 7)
    private BigDecimal lng;

    @Column
    private Integer basicParkingTime;

    @Column
    private Integer basicParkingFee;

    @Column
    private Integer additionalUnitTime;

    @Column
    private Integer additionalUnitFee;

    @Column(length = 50)
    private String phoneNumber;

    public void updateFromSeed(
            String parkingLotName,
            String parkingLotDivision,
            String parkingLotType,
            String roadAddress,
            String lotAddress,
            Integer parkingCapacity,
            String alternateNoDivision,
            String weekdayOperatingHours,
            String saturdayOperatingHours,
            String holidayOperatingHours,
            BigDecimal lat,
            BigDecimal lng,
            Integer basicParkingTime,
            Integer basicParkingFee,
            Integer additionalUnitTime,
            Integer additionalUnitFee,
            String phoneNumber
    ) {
        this.parkingLotName = parkingLotName;
        this.parkingLotDivision = parkingLotDivision;
        this.parkingLotType = parkingLotType;
        this.roadAddress = roadAddress;
        this.lotAddress = lotAddress;
        this.parkingCapacity = parkingCapacity;
        this.alternateNoDivision = alternateNoDivision;
        this.weekdayOperatingHours = weekdayOperatingHours;
        this.saturdayOperatingHours = saturdayOperatingHours;
        this.holidayOperatingHours = holidayOperatingHours;
        this.lat = lat;
        this.lng = lng;
        this.basicParkingTime = basicParkingTime;
        this.basicParkingFee = basicParkingFee;
        this.additionalUnitTime = additionalUnitTime;
        this.additionalUnitFee = additionalUnitFee;
        this.phoneNumber = phoneNumber;
    }

    @Builder
    private ParkingLot(
            String parkingManagementNumber,
            String parkingLotName,
            String parkingLotDivision,
            String parkingLotType,
            String roadAddress,
            String lotAddress,
            Integer parkingCapacity,
            String alternateNoDivision,
            String weekdayOperatingHours,
            String saturdayOperatingHours,
            String holidayOperatingHours,
            BigDecimal lat,
            BigDecimal lng,
            Integer basicParkingTime,
            Integer basicParkingFee,
            Integer additionalUnitTime,
            Integer additionalUnitFee,
            String phoneNumber
    ) {
        this.parkingManagementNumber = parkingManagementNumber;
        this.parkingLotName = parkingLotName;
        this.parkingLotDivision = parkingLotDivision;
        this.parkingLotType = parkingLotType;
        this.roadAddress = roadAddress;
        this.lotAddress = lotAddress;
        this.parkingCapacity = parkingCapacity;
        this.alternateNoDivision = alternateNoDivision;
        this.weekdayOperatingHours = weekdayOperatingHours;
        this.saturdayOperatingHours = saturdayOperatingHours;
        this.holidayOperatingHours = holidayOperatingHours;
        this.lat = lat;
        this.lng = lng;
        this.basicParkingTime = basicParkingTime;
        this.basicParkingFee = basicParkingFee;
        this.additionalUnitTime = additionalUnitTime;
        this.additionalUnitFee = additionalUnitFee;
        this.phoneNumber = phoneNumber;
    }
}
