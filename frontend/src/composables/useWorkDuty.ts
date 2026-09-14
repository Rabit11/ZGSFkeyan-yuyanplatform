import { computed, type MaybeRef, unref } from 'vue'
import { message } from 'ant-design-vue'
import { WORK_ACTION_LABEL, type WorkAction, type WorkDutyCode } from '@/constants/workDuty'
import { useUserStore } from '@/stores/user'
import { myDutyHint, resolveWorkDuty } from '@/utils/workDuty'

export function useWorkDuty(code: MaybeRef<WorkDutyCode>, project: MaybeRef<any>) {
  const user = useUserStore()
  const duty = computed(() =>
    resolveWorkDuty(unref(code), unref(project), {
      employeeNo: user.employeeNo,
      realName: user.realName,
      identityCode: user.identityCode,
      roles: user.roles,
    }),
  )
  const hint = computed(() => myDutyHint(duty.value))
  const can = computed(() => ({
    fill: duty.value.fill.can,
    submit: duty.value.submit.can,
    audit: duty.value.audit.can,
    view: duty.value.view.can,
    edit: duty.value.edit.can,
  }))

  function guard(action: WorkAction) {
    const cell = duty.value[action]
    if (cell.can) return true
    message.warning(cell.reason || `当前账号不能${WORK_ACTION_LABEL[action]}`)
    return false
  }

  return { duty, can, hint, guard }
}
