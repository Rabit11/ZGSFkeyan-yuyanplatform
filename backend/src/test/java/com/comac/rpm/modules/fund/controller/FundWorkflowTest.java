package com.comac.rpm.modules.fund.controller;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.modules.fund.entity.*;
import com.comac.rpm.modules.fund.mapper.*;
import com.comac.rpm.modules.project.entity.ProjInfo;
import com.comac.rpm.modules.project.mapper.ProjInfoMapper;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class FundWorkflowTest {
 final FundController controller=new FundController();
 final FundBudgetMapper budgets=mock(FundBudgetMapper.class);
 final FundPaymentMapper payments=mock(FundPaymentMapper.class);
 final ProjInfoMapper projects=mock(ProjInfoMapper.class);
 final FlowAuditGuard guard=mock(FlowAuditGuard.class);
 FundWorkflowTest(){
  ReflectionTestUtils.setField(controller,"budgetMapper",budgets);ReflectionTestUtils.setField(controller,"paymentMapper",payments);
  ReflectionTestUtils.setField(controller,"projectMapper",projects);ReflectionTestUtils.setField(controller,"flowAuditGuard",guard);
  ProjInfo p=new ProjInfo();p.setId(1L);p.setTotalFund(new BigDecimal("100"));when(projects.selectById(1L)).thenReturn(p);
  when(budgets.selectCount(any())).thenReturn(0L);when(budgets.selectList(any())).thenReturn(List.of());
  when(payments.selectCount(any())).thenReturn(0L);
 }
 FundBudget budget(){FundBudget b=new FundBudget();b.setProjectId(1L);b.setYear(2026);b.setMilestoneName("年度试验费");b.setAmount(BigDecimal.TEN);b.setStatus("DRAFT");return b;}
 @Test void budgetWithoutAnyMilestoneCanBeSaved(){assertDoesNotThrow(()->controller.createBudget(budget()));verify(budgets).insert(any(FundBudget.class));}
 @Test void forgedApprovedCreationIsDenied(){FundBudget b=budget();b.setStatus("APPROVED");assertThrows(BusinessException.class,()->controller.createBudget(b));verify(budgets,never()).insert(any(FundBudget.class));}
 @Test void reviewCannotRewriteBudgetAmount(){FundBudget old=budget();old.setId(2L);old.setStatus("PENDING");when(budgets.selectById(2L)).thenReturn(old);FundBudget patch=budget();patch.setAmount(new BigDecimal("99"));patch.setStatus("UNIT_OK");assertThrows(BusinessException.class,()->controller.updateBudget(2L,patch));}
 @Test void reviewCannotSkipUnitApproval(){FundBudget old=budget();old.setId(2L);old.setStatus("PENDING");when(budgets.selectById(2L)).thenReturn(old);FundBudget patch=budget();patch.setStatus("APPROVED");assertThrows(BusinessException.class,()->controller.updateBudget(2L,patch));}
 @Test void projectCannotBeChangedInBudgetUpdate(){FundBudget old=budget();old.setId(2L);when(budgets.selectById(2L)).thenReturn(old);FundBudget patch=budget();patch.setProjectId(7L);assertThrows(BusinessException.class,()->controller.updateBudget(2L,patch));}
 @Test void draftPaymentHasNoMilestoneDependency(){FundPayment p=new FundPayment();p.setProjectId(1L);p.setAmount(BigDecimal.ONE);p.setFlowType("WRITEOFF");p.setWriteoffStatus("DRAFT");p.setOccurDate(LocalDate.now());assertDoesNotThrow(()->controller.createPayment(p));}
 @Test void submittedBudgetCannotBeDeleted(){FundBudget old=budget();old.setId(2L);old.setStatus("PENDING");when(budgets.selectById(2L)).thenReturn(old);assertThrows(BusinessException.class,()->controller.deleteBudget(2L));}
 @Test void unitApprovalAndHqApprovalPreserveContent(){
  FundBudget old=budget();old.setId(2L);old.setStatus("PENDING");when(budgets.selectById(2L)).thenReturn(old);
  FundBudget patch=new FundBudget();patch.setStatus("UNIT_OK");assertDoesNotThrow(()->controller.updateBudget(2L,patch));
  old.setStatus("UNIT_OK");patch.setStatus("APPROVED");assertDoesNotThrow(()->controller.updateBudget(2L,patch));
  verify(budgets,times(2)).updateById(any(FundBudget.class));
 }
 @Test void budgetNameAliasReadsClientPayload() throws Exception {
  var b=new com.fasterxml.jackson.databind.ObjectMapper().readValue("{\"budgetName\":\"试验费\"}",FundBudget.class);
  assertEquals("试验费",b.getMilestoneName());assertEquals("试验费",b.getBudgetName());
 }
 @Test void paymentDraftIsUpdatedInsteadOfDuplicated(){
  FundPayment p=new FundPayment();p.setId(4L);p.setProjectId(1L);p.setWriteoffStatus("DRAFT");p.setAmount(BigDecimal.ONE);
  when(payments.selectById(4L)).thenReturn(p);
  var user=new com.comac.rpm.modules.system.entity.SysUser();user.setRealName("财务");when(guard.currentUser()).thenReturn(user);
  assertDoesNotThrow(()->controller.updatePayment(4L,p));verify(payments).updateById(any(FundPayment.class));verify(payments,never()).insert(any(FundPayment.class));
 }
 @Test void writtenPaymentCannotBeEdited(){
  FundPayment p=new FundPayment();p.setId(4L);p.setWriteoffStatus("WRITTEN");when(payments.selectById(4L)).thenReturn(p);
  assertThrows(BusinessException.class,()->controller.updatePayment(4L,new FundPayment()));
 }
}
