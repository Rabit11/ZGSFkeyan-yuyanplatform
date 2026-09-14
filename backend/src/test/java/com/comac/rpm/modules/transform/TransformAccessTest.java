package com.comac.rpm.modules.transform;

import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.modules.project.entity.*;
import com.comac.rpm.modules.project.mapper.*;
import com.comac.rpm.modules.system.entity.SysUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransformAccessTest {
 private final FlowAuditGuard guard=mock(FlowAuditGuard.class);
 private final ProjInfoMapper projects=mock(ProjInfoMapper.class);
 private final ProjTeamMemberMapper members=mock(ProjTeamMemberMapper.class);
 private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 private final TransformAccess access=new TransformAccess();
 private final SysUser user=new SysUser();
 private final ProjInfo project=new ProjInfo();
 TransformAccessTest() {
  user.setId(7L);user.setStatus(1);user.setIdentityCode("owner");user.setOrgId(1L);user.setEmployeeNo("A007");user.setRealName("张三");
  project.setId(1L);project.setOrgId(1L);project.setOwnerName("李四");
  when(guard.currentUser()).thenReturn(user);when(projects.selectById(1L)).thenReturn(project);when(members.selectList(any())).thenReturn(List.of());
  ReflectionTestUtils.setField(access,"guard",guard);ReflectionTestUtils.setField(access,"projects",projects);ReflectionTestUtils.setField(access,"members",members);ReflectionTestUtils.setField(access,"jdbc",jdbc);
 }
 @Test void ownerIdentityAndSameUnitAreNotOwnership() {assertThrows(BusinessException.class,()->access.requireOwner(1L));}
 @Test void administratorIsNotAnImplicitOwner() {user.setIdentityCode("admin");assertThrows(BusinessException.class,()->access.requireOwner(1L));}
 @Test void employeeNumbersAreExactAndDoNotStripLetters() {
  var m=new ProjTeamMember();m.setRoleCode("PROJECT_LEADER");m.setEmployeeNo("B007");m.setUserName("张三");when(members.selectList(any())).thenReturn(List.of(m));
  assertThrows(BusinessException.class,()->access.requireOwner(1L));m.setEmployeeNo("A007");assertDoesNotThrow(()->access.requireOwner(1L));
 }
 @Test void explicitNamedOwnerOverridesLooseProjectLabel() {
  project.setOwnerName("张三(A007)");var m=new ProjTeamMember();m.setRoleCode("PROJECT_LEADER");m.setEmployeeNo("B008");when(members.selectList(any())).thenReturn(List.of(m));
  assertThrows(BusinessException.class,()->access.requireOwner(1L));
 }
 @Test void sameNameIsAcceptedOnlyWhenUnique() {
  project.setOwnerName("张三");when(jdbc.queryForObject(anyString(),eq(Integer.class),eq("张三"))).thenReturn(2);assertThrows(BusinessException.class,()->access.requireOwner(1L));
  when(jdbc.queryForObject(anyString(),eq(Integer.class),eq("张三"))).thenReturn(1);assertDoesNotThrow(()->access.requireOwner(1L));
 }
 @Test void selfReviewAndCrossUnitReviewAreDenied() {
  user.setIdentityCode("unitHead");project.setOwnerName("张三(A007)");assertThrows(BusinessException.class,()->access.requireAction(1L,"audit"));
  project.setOwnerName("李四");user.setOrgId(2L);assertThrows(BusinessException.class,()->access.requireAction(1L,"audit"));user.setOrgId(1L);assertDoesNotThrow(()->access.requireAction(1L,"audit"));
 }
 @Test void appointedAuditorCannotBeReplacedByAnotherSameUnitHead() {
  user.setIdentityCode("unitHead");var m=new ProjTeamMember();m.setRoleCode("UNIT_MINISTER");m.setEmployeeNo("A008");when(members.selectList(any())).thenReturn(List.of(m));assertThrows(BusinessException.class,()->access.requireAction(1L,"audit"));
 }
 @Test void headquartersRecorderCannotBeProjectOwner() {
  user.setIdentityCode("hqStaff");project.setOwnerName("张三(A007)");assertThrows(BusinessException.class,()->access.requireAction(1L,"record"));project.setOwnerName("李四");assertDoesNotThrow(()->access.requireAction(1L,"record"));
 }
 @Test void sameDisplayNameCannotGrantPrivateFileReadWithConflictingEmployeeId() {
  var m=new ProjTeamMember();m.setRoleCode("PROJECT_CONTACT");m.setEmployeeNo("B007");m.setUserName("张三");when(members.selectList(any())).thenReturn(List.of(m));
  assertThrows(BusinessException.class,()->access.requireReadable(1L));m.setEmployeeNo("A007");assertDoesNotThrow(()->access.requireReadable(1L));
 }
 @Test void inactiveUsersCannotReadPrivateFilesEvenWithAdminRole() {
  user.setIdentityCode("admin");user.setStatus(0);assertThrows(BusinessException.class,()->access.requireReadable(1L));
 }
 @Test void orphanedHistoricalProjectIsReadableOnlyByAdminAndNeverWritable() {
  when(projects.selectById(1L)).thenReturn(null);user.setIdentityCode("admin");
  assertDoesNotThrow(()->access.requireReadable(1L));
  assertThrows(BusinessException.class,()->access.requireOwner(1L));assertThrows(BusinessException.class,()->access.requireAction(1L,"audit"));
  user.setIdentityCode("hqStaff");user.setDataScope("COMPANY");assertThrows(BusinessException.class,()->access.requireReadable(1L));
  user.setIdentityCode("admin");user.setStatus(0);assertThrows(BusinessException.class,()->access.requireReadable(1L));
 }
 @Test void deletedHistoricalProjectIsAdminReadOnly() {
  project.setDeleted(1);user.setIdentityCode("admin");assertDoesNotThrow(()->access.requireReadable(1L));assertThrows(BusinessException.class,()->access.requireOwner(1L));
  user.setIdentityCode("owner");assertThrows(BusinessException.class,()->access.requireReadable(1L));
 }
 @Test void companyScopeListIsLimitedToExistingProjectsRatherThanIncludingOrphans() {
  user.setIdentityCode("hqStaff");user.setDataScope("COMPANY");when(projects.selectList(any())).thenReturn(List.of(project));
  assertEquals(List.of(1L),access.visibleProjectIds());
 }
}
