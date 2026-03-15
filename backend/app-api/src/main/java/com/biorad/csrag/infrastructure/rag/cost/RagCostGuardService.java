package com.biorad.csrag.infrastructure.rag.cost;

import com.biorad.csrag.infrastructure.rag.config.RagPipelineProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * RAG 파이프라인 비용 가드 서비스.
 *
 * <p>일일 예산 한도, 건당 비용 제한, 잔여 예산 기반 모델 티어 추천 등
 * 비용 초과를 사전에 방지하는 역할을 담당한다.
 *
 * <p>현재는 {@code getDailySpentUsd()}가 0.0을 반환하며,
 * 향후 {@code RagPipelineMetricRepository}에 토큰/비용 추적 컬럼이 추가되면
 * 실제 일일 누적 비용을 쿼리하도록 확장한다.
 */
@Service
public class RagCostGuardService {

    private static final Logger log = LoggerFactory.getLogger(RagCostGuardService.class);

    private final RagPipelineProperties properties;

    public RagCostGuardService(RagPipelineProperties properties) {
        this.properties = properties;
    }

    /**
     * 일일 예산 내에서 새로운 문의를 처리할 수 있는지 확인한다.
     *
     * @return 예산 내이면 {@code true}, 초과하면 {@code false}
     */
    public boolean isDailyBudgetAvailable() {
        double dailySpent = getDailySpentUsd();
        double budget = properties.getCost().getDailyBudgetUsd();

        if (budget <= 0) {
            log.warn("RAG 일일 예산이 0 이하로 설정됨 ({}). 비용 가드를 비활성화합니다.", budget);
            return true;
        }

        double percent = (dailySpent / budget) * 100;
        if (percent >= properties.getCost().getAlertThresholdPercent()) {
            log.warn("RAG 일일 비용 경고: {}% 소진 (${} / ${})",
                    String.format("%.2f", percent),
                    String.format("%.4f", dailySpent),
                    String.format("%.2f", budget));
        }

        return dailySpent < budget;
    }

    /**
     * 단건 문의의 예상 비용이 건당 한도 이내인지 확인한다.
     *
     * @param estimatedCostUsd 예상 비용 (USD)
     * @return 한도 이내이면 {@code true}
     */
    public boolean isInquiryCostWithinLimit(double estimatedCostUsd) {
        return estimatedCostUsd <= properties.getCost().getPerInquiryMaxUsd();
    }

    /**
     * 잔여 일일 예산을 기반으로 권장 모델 티어를 반환한다.
     *
     * <ul>
     *   <li>잔여 예산 &lt; 10% : {@code LIGHT} (비용 최소화)</li>
     *   <li>잔여 예산 &lt; 30% : {@code MEDIUM}</li>
     *   <li>그 외 : {@code HEAVY} (최대 품질)</li>
     * </ul>
     *
     * @return 권장 모델 티어 문자열
     */
    public String getRecommendedModelTier() {
        double dailySpent = getDailySpentUsd();
        double budget = properties.getCost().getDailyBudgetUsd();

        if (budget <= 0) {
            return "HEAVY";
        }

        double remaining = budget - dailySpent;

        if (remaining < budget * 0.1) {
            log.warn("일일 예산 거의 소진 (잔여 ${}) — LIGHT 모델 권장",
                    String.format("%.4f", remaining));
            return "LIGHT";
        } else if (remaining < budget * 0.3) {
            return "MEDIUM";
        }
        return "HEAVY";
    }

    /**
     * 오늘 누적 소진 비용을 조회한다.
     *
     * <p>현재는 메트릭 테이블에 비용 컬럼이 없으므로 0.0을 반환한다.
     * 향후 {@code RagPipelineMetricRepository}에 일일 비용 집계 쿼리를 추가하면
     * 이 메서드를 교체한다.
     *
     * @return 오늘 소진한 비용 (USD)
     */
    double getDailySpentUsd() {
        // TODO: RagPipelineMetricRepository에 토큰/비용 추적 컬럼 추가 후
        //       LocalDate.now() 기준 SUM(estimated_cost_usd) 쿼리로 교체
        return 0.0;
    }
}
