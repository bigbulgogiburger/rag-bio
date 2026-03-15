package com.biorad.csrag.infrastructure.rag.cost;

import com.biorad.csrag.infrastructure.rag.config.RagPipelineProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RagCostGuardService} 단위 테스트.
 *
 * <p>{@code getDailySpentUsd()}를 오버라이드하여 다양한 예산 소진 시나리오를 검증한다.
 */
class RagCostGuardServiceTest {

    private RagPipelineProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RagPipelineProperties();
        properties.getCost().setDailyBudgetUsd(50.0);
        properties.getCost().setAlertThresholdPercent(80);
        properties.getCost().setPerInquiryMaxUsd(0.10);
    }

    /**
     * 테스트용 서브클래스: getDailySpentUsd()를 원하는 값으로 제어한다.
     */
    private RagCostGuardService createServiceWithDailySpent(double spent) {
        return new RagCostGuardService(properties) {
            @Override
            double getDailySpentUsd() {
                return spent;
            }
        };
    }

    @Nested
    class IsDailyBudgetAvailable {

        @Test
        void returnsTrue_whenUnderBudget() {
            var service = createServiceWithDailySpent(10.0);
            assertThat(service.isDailyBudgetAvailable()).isTrue();
        }

        @Test
        void returnsTrue_whenExactlyAtAlertThreshold() {
            // 80% of 50 = 40
            var service = createServiceWithDailySpent(40.0);
            assertThat(service.isDailyBudgetAvailable()).isTrue();
        }

        @Test
        void returnsFalse_whenOverBudget() {
            var service = createServiceWithDailySpent(55.0);
            assertThat(service.isDailyBudgetAvailable()).isFalse();
        }

        @Test
        void returnsFalse_whenExactlyAtBudget() {
            var service = createServiceWithDailySpent(50.0);
            assertThat(service.isDailyBudgetAvailable()).isFalse();
        }

        @Test
        void returnsTrue_whenBudgetIsZero() {
            // budget <= 0 이면 가드를 비활성화한다
            properties.getCost().setDailyBudgetUsd(0.0);
            var service = createServiceWithDailySpent(10.0);
            assertThat(service.isDailyBudgetAvailable()).isTrue();
        }

        @Test
        void returnsTrue_whenBudgetIsNegative() {
            properties.getCost().setDailyBudgetUsd(-1.0);
            var service = createServiceWithDailySpent(0.0);
            assertThat(service.isDailyBudgetAvailable()).isTrue();
        }

        @Test
        void returnsTrue_whenNothingSpent() {
            var service = createServiceWithDailySpent(0.0);
            assertThat(service.isDailyBudgetAvailable()).isTrue();
        }
    }

    @Nested
    class IsInquiryCostWithinLimit {

        @Test
        void returnsTrue_whenCostBelowLimit() {
            var service = createServiceWithDailySpent(0.0);
            assertThat(service.isInquiryCostWithinLimit(0.05)).isTrue();
        }

        @Test
        void returnsTrue_whenCostExactlyAtLimit() {
            var service = createServiceWithDailySpent(0.0);
            assertThat(service.isInquiryCostWithinLimit(0.10)).isTrue();
        }

        @Test
        void returnsFalse_whenCostExceedsLimit() {
            var service = createServiceWithDailySpent(0.0);
            assertThat(service.isInquiryCostWithinLimit(0.15)).isFalse();
        }

        @Test
        void respectsCustomPerInquiryMaxUsd() {
            properties.getCost().setPerInquiryMaxUsd(0.50);
            var service = createServiceWithDailySpent(0.0);
            assertThat(service.isInquiryCostWithinLimit(0.40)).isTrue();
            assertThat(service.isInquiryCostWithinLimit(0.60)).isFalse();
        }
    }

    @Nested
    class GetRecommendedModelTier {

        @Test
        void returnsHeavy_whenPlentyOfBudgetRemaining() {
            // 50 - 10 = 40 remaining → 80% remaining → HEAVY
            var service = createServiceWithDailySpent(10.0);
            assertThat(service.getRecommendedModelTier()).isEqualTo("HEAVY");
        }

        @Test
        void returnsMedium_whenBudgetBelow30Percent() {
            // 50 * 0.3 = 15 remaining threshold
            // 50 - 38 = 12 remaining → 24% → MEDIUM
            var service = createServiceWithDailySpent(38.0);
            assertThat(service.getRecommendedModelTier()).isEqualTo("MEDIUM");
        }

        @Test
        void returnsLight_whenBudgetBelow10Percent() {
            // 50 * 0.1 = 5 remaining threshold
            // 50 - 47 = 3 remaining → 6% → LIGHT
            var service = createServiceWithDailySpent(47.0);
            assertThat(service.getRecommendedModelTier()).isEqualTo("LIGHT");
        }

        @Test
        void returnsLight_whenOverBudget() {
            // 50 - 60 = -10 remaining → negative → LIGHT
            var service = createServiceWithDailySpent(60.0);
            assertThat(service.getRecommendedModelTier()).isEqualTo("LIGHT");
        }

        @Test
        void returnsHeavy_whenNothingSpent() {
            var service = createServiceWithDailySpent(0.0);
            assertThat(service.getRecommendedModelTier()).isEqualTo("HEAVY");
        }

        @Test
        void returnsHeavy_whenBudgetIsZero() {
            // budget <= 0 → 가드 비활성화 → HEAVY
            properties.getCost().setDailyBudgetUsd(0.0);
            var service = createServiceWithDailySpent(0.0);
            assertThat(service.getRecommendedModelTier()).isEqualTo("HEAVY");
        }

        @Test
        void transitionsCorrectly_atBoundary() {
            // Exactly 30% remaining → 50 * 0.3 = 15, spent = 35 → remaining = 15
            // 15 < 15 is false → should be HEAVY (not MEDIUM)
            var service = createServiceWithDailySpent(35.0);
            assertThat(service.getRecommendedModelTier()).isEqualTo("HEAVY");

            // Just past boundary: spent = 35.01 → remaining = 14.99 < 15 → MEDIUM
            var service2 = createServiceWithDailySpent(35.01);
            assertThat(service2.getRecommendedModelTier()).isEqualTo("MEDIUM");
        }

        @Test
        void transitionsCorrectly_at10PercentBoundary() {
            // Exactly 10% remaining → 50 * 0.1 = 5, spent = 45 → remaining = 5
            // 5 < 5 is false → should be MEDIUM
            var service = createServiceWithDailySpent(45.0);
            assertThat(service.getRecommendedModelTier()).isEqualTo("MEDIUM");

            // Just past boundary: spent = 45.01 → remaining = 4.99 < 5 → LIGHT
            var service2 = createServiceWithDailySpent(45.01);
            assertThat(service2.getRecommendedModelTier()).isEqualTo("LIGHT");
        }
    }

    @Nested
    class DefaultImplementation {

        @Test
        void defaultGetDailySpentUsd_returnsZero() {
            var service = new RagCostGuardService(properties);
            // 기본 구현은 0.0을 반환하므로 항상 예산 내
            assertThat(service.isDailyBudgetAvailable()).isTrue();
            assertThat(service.getRecommendedModelTier()).isEqualTo("HEAVY");
        }
    }
}
