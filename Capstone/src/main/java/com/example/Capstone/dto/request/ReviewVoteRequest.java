package com.example.Capstone.dto.request;

import com.example.Capstone.domain.ReviewVote;

import jakarta.validation.constraints.NotNull;

public record ReviewVoteRequest(
        @NotNull ReviewVote.VoteType voteType
) {}
