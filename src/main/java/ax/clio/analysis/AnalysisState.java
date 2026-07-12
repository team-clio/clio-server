package ax.clio.analysis;

import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;

/**
 * 분석 그래프(#12)의 공유 상태. 노드가 산출물을 키로 축적하고 다음 노드가 읽는다.
 *
 * <p>G2: JPA 엔티티(BugReport·Project)는 담지 않고 식별자(jobId)만 담는다 — 엔티티는 노드가 jobId로 조회.
 * 유사이슈·관련결정도 엔티티를 감싼 ScoredIssue/ScoredDecision 대신 <b>plain entry</b>로 담는다(체크포인트
 * 직렬화를 위해). 나머지 중간 산출물 record는 {@code Serializable}이라 그대로 담는다. 채널은 비워
 * 기본 덮어쓰기(단일값 갱신)만 쓴다.
 */
public class AnalysisState extends AgentState {

	public static final String JOB_ID = "jobId";
	public static final String PREPARATION = "preparation";
	public static final String CANDIDATES = "candidates";
	public static final String FLOWS = "flows";
	public static final String SIMILAR_ISSUES = "similarIssues";
	public static final String RELATED_DECISIONS = "relatedDecisions";
	public static final String DRAFT = "draft";

	/** 단일값 갱신만 쓰므로 스키마는 비운다(키 없으면 기본 덮어쓰기). */
	public static final Map<String, Channel<?>> SCHEMA = Map.of();

	public AnalysisState(Map<String, Object> initData) {
		super(initData);
	}

	public Long jobId() {
		return this.<Long>value(JOB_ID).orElseThrow();
	}

	public ReportSearchPreparation preparation() {
		return this.<ReportSearchPreparation>value(PREPARATION).orElseThrow();
	}

	public List<RankedCodeCandidate> candidates() {
		return this.<List<RankedCodeCandidate>>value(CANDIDATES).orElseGet(List::of);
	}

	public List<CodeFlow> flows() {
		return this.<List<CodeFlow>>value(FLOWS).orElseGet(List::of);
	}

	public List<SimilarIssueEntry> similarIssues() {
		return this.<List<SimilarIssueEntry>>value(SIMILAR_ISSUES).orElseGet(List::of);
	}

	public List<RelatedDecisionEntry> relatedDecisions() {
		return this.<List<RelatedDecisionEntry>>value(RELATED_DECISIONS).orElseGet(List::of);
	}

	public AnalysisResultDraft draft() {
		return this.<AnalysisResultDraft>value(DRAFT).orElseThrow();
	}
}
