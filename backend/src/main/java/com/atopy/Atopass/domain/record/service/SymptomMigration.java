package com.atopy.Atopass.domain.record.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 증상 목록을 바꾸기 전에 저장된 기록을 새 목록으로 옮긴다. 서버가 뜰 때마다 실행되며, 옮길 것이 없으면 아무것도 하지 않는다.
 *
 * <ul>
 *   <li>나타난 증상: 구진·결절 → 부종/구진(EDEMA), 각질(SCALING)은 새 목록에 없어 지운다.</li>
 *   <li>부위별 증상 점수: 1점 이상인 증상을 그 부위의 나타난 증상으로 옮긴다
 *       (가려움 → 가려움, 붉어짐 → 홍반, 건조함 → 건조증, 부기 → 부종/구진, 따가움·각질은 지움).
 *       그 부위에 고통 점수가 없으면 옮긴 점수 중 가장 높은 점수를 고통 점수로 남긴다.</li>
 * </ul>
 *
 * <p>예전에는 이 칸들이 MySQL ENUM 타입이었다. ENUM에 예전 값이 남아 있으면 JPA(ddl-auto: update)가 새 값 목록으로
 * 바꾸지 못하고, 새 증상도 넣을 수 없다. 그래서 먼저 문자열(VARCHAR) 칸으로 바꾼 뒤 값을 옮긴다.
 * 지금은 엔티티가 이 칸들을 VARCHAR로 매핑하므로, 한 번 바꾸고 나면 다시 ENUM으로 돌아가지 않는다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class SymptomMigration implements ApplicationRunner {

	/** 값 목록이 바뀔 수 있는 enum 칸 (테이블, 칸) */
	private static final List<String[]> ENUM_COLUMNS = List.of(
		new String[] {"record_skin_signs", "skin_sign"},
		new String[] {"skin_condition_signs", "skin_sign"},
		new String[] {"symptom_scores", "symptom"},
		new String[] {"skin_conditions", "body_part"});

	/** 예전 증상별 점수 → 새 나타난 증상. 없는 것(따가움·각질)은 옮기지 않는다. */
	private static final Map<String, String> SCORE_TO_SIGN = Map.of(
		"ITCHING", "ITCHING",
		"REDNESS", "ERYTHEMA",
		"DRYNESS", "XEROSIS",
		"SWELLING", "EDEMA");

	private static final String PAIN = "PAIN";

	private final JdbcTemplate jdbc;
	private final TransactionTemplate transaction;

	@Override
	public void run(ApplicationArguments args) {
		widenEnumColumns();
		transaction.executeWithoutResult(status -> {
			migrateSigns("record_skin_signs", "record_id");
			migrateSigns("skin_condition_signs", "skin_condition_id");
			migrateScores();
		});
	}

	/** ENUM 칸을 VARCHAR로 바꾼다. 이미 바꿨으면 건너뛴다. */
	void widenEnumColumns() {
		for (String[] column : ENUM_COLUMNS) {
			List<String> types = jdbc.queryForList(
				"select data_type from information_schema.columns"
					+ " where table_schema = schema() and upper(table_name) = upper(?) and upper(column_name) = upper(?)",
				String.class, column[0], column[1]);
			if (!types.isEmpty() && "enum".equalsIgnoreCase(types.get(0))) {
				jdbc.execute("alter table " + column[0] + " modify column " + column[1] + " varchar(30) not null");
				log.info("{}.{} 칸을 문자열 칸으로 바꿨습니다.", column[0], column[1]);
			}
		}
	}

	/** 나타난 증상: 구진·결절 → 부종/구진, 각질은 지운다. */
	private void migrateSigns(String table, String idColumn) {
		List<Long> edemaIds = jdbc.queryForList(
			"select distinct " + idColumn + " from " + table + " where skin_sign in ('PAPULE', 'NODULE')", Long.class);
		if (edemaIds.isEmpty() && count(table, "skin_sign = 'SCALING'") == 0) {
			return;
		}
		Set<Long> hasEdema = new HashSet<>(jdbc.queryForList(
			"select " + idColumn + " from " + table + " where skin_sign = 'EDEMA'", Long.class));
		List<Object[]> inserts = edemaIds.stream()
			.filter(id -> !hasEdema.contains(id))
			.map(id -> new Object[] {id, "EDEMA"})
			.toList();
		jdbc.batchUpdate("insert into " + table + " (" + idColumn + ", skin_sign) values (?, ?)", inserts);
		int deleted = jdbc.update("delete from " + table + " where skin_sign in ('PAPULE', 'NODULE', 'SCALING')");
		log.info("{}: 예전 증상 {}건을 새 증상 목록으로 옮겼습니다.", table, deleted);
	}

	/** 부위별 증상 점수: 고통 점수만 남기고, 나머지는 그 부위의 나타난 증상으로 옮긴다. */
	private void migrateScores() {
		record OldScore(long conditionId, long recordId, String symptom, int score) {
		}
		List<OldScore> old = jdbc.query(
			"select s.skin_condition_id, c.record_id, s.symptom, s.score from symptom_scores s"
				+ " join skin_conditions c on c.id = s.skin_condition_id where s.symptom <> '" + PAIN + "'",
			(rs, i) -> new OldScore(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4)));
		if (old.isEmpty()) {
			return;
		}

		Set<Long> hasPain = new HashSet<>(jdbc.queryForList(
			"select skin_condition_id from symptom_scores where symptom = '" + PAIN + "'", Long.class));
		Set<String> partSigns = new HashSet<>(jdbc.query("select skin_condition_id, skin_sign from skin_condition_signs",
			(rs, i) -> rs.getLong(1) + ":" + rs.getString(2)));
		Set<String> daySigns = new HashSet<>(jdbc.query("select record_id, skin_sign from record_skin_signs",
			(rs, i) -> rs.getLong(1) + ":" + rs.getString(2)));

		Map<Long, Integer> maxScore = new HashMap<>();
		List<Object[]> partInserts = new ArrayList<>();
		List<Object[]> dayInserts = new ArrayList<>();
		for (OldScore score : old) {
			maxScore.merge(score.conditionId(), score.score(), Math::max);
			String sign = SCORE_TO_SIGN.get(score.symptom());
			if (sign == null || score.score() < 1) {
				continue;
			}
			if (partSigns.add(score.conditionId() + ":" + sign)) {
				partInserts.add(new Object[] {score.conditionId(), sign});
			}
			// 하루의 나타난 증상은 부위마다 고른 증상을 합친 값이므로 함께 넣는다.
			if (daySigns.add(score.recordId() + ":" + sign)) {
				dayInserts.add(new Object[] {score.recordId(), sign});
			}
		}
		List<Object[]> painInserts = maxScore.entrySet().stream()
			.filter(e -> !hasPain.contains(e.getKey()))
			.map(e -> new Object[] {e.getKey(), PAIN, e.getValue()})
			.toList();

		jdbc.batchUpdate("insert into skin_condition_signs (skin_condition_id, skin_sign) values (?, ?)", partInserts);
		jdbc.batchUpdate("insert into record_skin_signs (record_id, skin_sign) values (?, ?)", dayInserts);
		jdbc.batchUpdate("insert into symptom_scores (skin_condition_id, symptom, score) values (?, ?, ?)", painInserts);
		jdbc.update("delete from symptom_scores where symptom <> '" + PAIN + "'");
		log.info("예전 증상별 점수 {}건을 옮겼습니다. (부위 증상 {}건, 고통 점수 {}건 추가)",
			old.size(), partInserts.size(), painInserts.size());
	}

	private int count(String table, String where) {
		Integer count = jdbc.queryForObject("select count(*) from " + table + " where " + where, Integer.class);
		return count == null ? 0 : count;
	}

}
