package com.example.Capstone.dto.request;

import com.example.Capstone.domain.User;

import jakarta.validation.constraints.NotNull;

public record AdditionalInfoRequest(
    @NotNull Short birthYear,
    @NotNull Short birthMonth,
    @NotNull Short birthDay,
    @NotNull User.Gender gender
) {}
