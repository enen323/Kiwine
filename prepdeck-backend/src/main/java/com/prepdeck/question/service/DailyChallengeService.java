package com.prepdeck.question.service;

import com.prepdeck.question.dto.DailyChallengeVO;

public interface DailyChallengeService {

    DailyChallengeVO getTodayChallenge();

    DailyChallengeVO completeTodayChallenge();
}
