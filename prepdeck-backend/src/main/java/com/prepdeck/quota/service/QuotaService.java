package com.prepdeck.quota.service;

import com.prepdeck.quota.dto.response.QuotaCheckResult;
import com.prepdeck.quota.dto.response.QuotaVO;

public interface QuotaService {

    QuotaCheckResult consume(String serviceType);

    void deductFree(String serviceType);

    void deductPaid(String serviceType);

    void grantPaid(String serviceType);

    void grantPaid(Long userId, String serviceType);

    QuotaVO getQuota();

    void resetMonthly();
}
