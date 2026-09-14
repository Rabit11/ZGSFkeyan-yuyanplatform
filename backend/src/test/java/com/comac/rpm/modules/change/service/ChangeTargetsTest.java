package com.comac.rpm.modules.change.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.comac.rpm.common.BusinessException;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class ChangeTargetsTest {
  final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  final ChangeTargets targets = new ChangeTargets(jdbc);
  final Map<String, Object> project =
      Map.of(
          "id",
          1L,
          "start_date",
          "2026-01-01",
          "end_date",
          "2027-12-31",
          "national_fund",
          100,
          "self_fund",
          20,
          "expense_total",
          130);

  @Test
  void arbitrarySqlIdentifiersCannotBeSelected() {
    assertThrows(BusinessException.class, () -> targets.field("name=1;DELETE FROM proj_info"));
    assertThrows(BusinessException.class, () -> targets.field(null));
  }

  @Test
  void missingOrForeignTargetRejected() {
    assertThrows(
        BusinessException.class, () -> targets.read(targets.field("milestoneDate"), 999, 1, true));
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-02-30", "2025-12-31", "2028-01-01", "bad", "10000-01-01"})
  void invalidAndOutOfPeriodDatesRejected(String date) {
    assertThrows(
        BusinessException.class,
        () ->
            targets.validate(
                targets.field("milestoneDate"), date, Map.of("plan_date", "2026-03-01"), project));
  }

  @Test
  void milestoneMustActuallyBeDelayed() {
    assertThrows(
        BusinessException.class,
        () ->
            targets.validate(
                targets.field("milestoneDate"),
                "2026-02-01",
                Map.of("plan_date", "2026-03-01"),
                project));
    assertEquals(
        "2026-04-01",
        targets.validate(
            targets.field("milestoneDate"),
            "2026-04-01",
            Map.of("plan_date", "2026-03-01"),
            project));
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "129.99", "1.234", "10000000000000000", "NaN"})
  void moneyLimitsAndCommittedFloor(String amount) {
    assertThrows(
        BusinessException.class,
        () ->
            targets.validate(
                targets.field("totalFund"), amount, Map.of("total_fund", 200), project));
  }

  @Test
  void normalizesMoneyAndRejectsNoop() {
    assertEquals(
        "250.5",
        targets.validate(
            targets.field("totalFund"),
            "250.50",
            Map.of("total_fund", new BigDecimal("200.00")),
            project));
    assertThrows(
        BusinessException.class,
        () ->
            targets.validate(
                targets.field("totalFund"),
                "200.00",
                Map.of("total_fund", new BigDecimal("200.00")),
                project));
  }

  @Test
  void textCannotExceedActualStorage() {
    assertThrows(
        BusinessException.class,
        () ->
            targets.validate(
                targets.field("partnerName"), "a".repeat(129), Map.of("org_name", "old"), project));
  }

  @Test
  void channelsRemainConsistent() {
    assertEquals("channel_id", targets.field("levelChannel").column());
    assertEquals("DATA", targets.field("levelChannel").type());
  }
}
