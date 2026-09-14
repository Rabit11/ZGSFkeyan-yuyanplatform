package com.comac.rpm.modules.change.service;

import static org.junit.jupiter.api.Assertions.*;

import com.comac.rpm.common.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChangePolicyTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "04ZXJX", "ZDYFJH", "XX25", "ZRJJ", "SHJBGS", "YYGD", "XJQX", "FGGGXJC", "ZDZX", "KJZ",
        "DFJYJY"
      })
  void generalChannelsRequireTwoLevels(String channel) {
    assertEquals(
        List.of("UNIT_REVIEW", "HQ_REVIEW"), ChangePolicy.route("PROJECT", "INDICATOR", channel));
  }

  @Test
  void shanghaiUnitFinal() {
    assertEquals(List.of("UNIT_FINAL"), ChangePolicy.route("PROJECT", "PAYMENT", "SHKJCX"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"CLM", "BOKH"})
  void allianceHasSeparateInternalConfirmation(String channel) {
    assertEquals(
        List.of("UNIT_REVIEW", "UNIT_CONFIRM"),
        ChangePolicy.route("PROJECT", "INDICATOR", channel));
  }

  @Test
  void mjkyRequiresOfflineArchive() {
    assertEquals(
        List.of("UNIT_REVIEW", "HQ_REVIEW", "EXTERNAL_ARCHIVE"),
        ChangePolicy.route("PROJECT", "INDICATOR", "MJKY"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"FUND", "PERIOD", "OUTSOURCE"})
  void majorChangesAlwaysIncludeLegalBeforeFinal(String category) {
    for (String channel : List.of("MJKY", "SHKJCX", "CLM", "BOKH", "04ZXJX")) {
      var route = ChangePolicy.route("PROJECT", category, channel);
      assertEquals("LEGAL", route.get(1));
      assertTrue(route.size() >= 3);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"MJKY", "SHKJCX", "CLM", "BOKH", "04ZXJX"})
  void dataRequiresHqConfirmation(String channel) {
    assertEquals(
        List.of("UNIT_REVIEW", "HQ_CONFIRM"), ChangePolicy.route("DATA", "BASIC", channel));
  }

  @Test
  void invalidTypeRejected() {
    assertEquals(
        422,
        assertThrows(BusinessException.class, () -> ChangePolicy.route("OTHER", "FUND", "MJKY"))
            .getCode());
  }

  @Test
  void onlyEditableStatesAcceptChanges() {
    for (String status : List.of("APPROVING", "AWAITING_ARCHIVE", "APPROVED"))
      assertThrows(BusinessException.class, () -> ChangePolicy.editable(status));
    assertDoesNotThrow(() -> ChangePolicy.editable("DRAFT"));
    assertDoesNotThrow(() -> ChangePolicy.editable("REJECTED"));
  }

  @Test
  void missingAndStaleVersionsRejected() {
    assertThrows(BusinessException.class, () -> ChangePolicy.revision(null, 1));
    assertThrows(BusinessException.class, () -> ChangePolicy.revision(0, 1));
    assertDoesNotThrow(() -> ChangePolicy.revision(1, 1));
  }

  @Test
  void requiredTextTrimsAndRejectsBlankAndOverflow() {
    assertEquals("原因", ChangePolicy.required(" 原因 ", "原因", 2));
    assertThrows(BusinessException.class, () -> ChangePolicy.required("  ", "原因", 2));
    assertThrows(BusinessException.class, () -> ChangePolicy.required("123", "原因", 2));
  }
}
