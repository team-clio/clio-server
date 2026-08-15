package ax.clio.issue.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ax.clio.bug.entity.Priority;
import org.junit.jupiter.api.Test;

class RiskPriorityMapperTest {

	@Test
	void mapsBandBoundaries() {
		assertEquals(Priority.P4, RiskPriorityMapper.fromScore(0));
		assertEquals(Priority.P4, RiskPriorityMapper.fromScore(29));
		assertEquals(Priority.P3, RiskPriorityMapper.fromScore(30));
		assertEquals(Priority.P3, RiskPriorityMapper.fromScore(49));
		assertEquals(Priority.P2, RiskPriorityMapper.fromScore(50));
		assertEquals(Priority.P2, RiskPriorityMapper.fromScore(69));
		assertEquals(Priority.P1, RiskPriorityMapper.fromScore(70));
		assertEquals(Priority.P1, RiskPriorityMapper.fromScore(84));
		assertEquals(Priority.P0, RiskPriorityMapper.fromScore(85));
		assertEquals(Priority.P0, RiskPriorityMapper.fromScore(100));
	}

	@Test
	void rejectsOutOfRangeScores() {
		assertThrows(IllegalArgumentException.class, () -> RiskPriorityMapper.fromScore(-1));
		assertThrows(IllegalArgumentException.class, () -> RiskPriorityMapper.fromScore(101));
	}
}
