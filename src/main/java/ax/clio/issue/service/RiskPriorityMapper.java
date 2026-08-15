package ax.clio.issue.service;

import ax.clio.bug.entity.Priority;

/**
 * 위험도 점수(0~100)를 P0~P4 우선순위로 변환한다.
 *
 * <p>대역 경계는 clio-agent-graph의 {@code risk.py}와 동일하게 유지한다.</p>
 */
public final class RiskPriorityMapper {

    private RiskPriorityMapper() {
    }

    public static Priority fromScore(int riskScore) {
        if (riskScore < 0 || riskScore > 100) {
            throw new IllegalArgumentException("risk_score must be between 0 and 100: " + riskScore);
        }
        if (riskScore >= 85) {
            return Priority.P0;
        }
        if (riskScore >= 70) {
            return Priority.P1;
        }
        if (riskScore >= 50) {
            return Priority.P2;
        }
        if (riskScore >= 30) {
            return Priority.P3;
        }
        return Priority.P4;
    }
}
