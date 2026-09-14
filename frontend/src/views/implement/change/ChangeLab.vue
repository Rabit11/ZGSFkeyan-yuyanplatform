<script setup lang="ts">
import { computed, ref } from "vue";
import { message } from "ant-design-vue";
import { useUserStore } from "@/stores/user";
import scenarios from "./scenarios.json";
import { labSession, loginChangeLab } from "@/api/changeSession";
const emit = defineEmits<{ start: [scenario: (typeof scenarios)[number]] }>();
const formalUser = useUserStore();
const user = computed(() => labSession.value || formalUser);
const expanded = ref(false),
  selected = ref("S01"),
  switching = ref(false);
const scenario = computed(
  () => scenarios.find((s) => s.id === selected.value)!,
);
const roles = [
  ["100012", "项目负责人"],
  ["100014", "技术负责人"],
  ["100005", "单位主管"],
  ["100006", "单位科技主管"],
  ["100004", "总部科技主管"],
  ["100009", "演练法务"],
  ["100001", "管理员（只读）"],
];
async function switchRole(employee: string) {
  switching.value = true;
  try {
    // A normal login against the isolated backend, with its own JWT and Redis database.
    if (labSession.value) await loginChangeLab(employee);
    else await formalUser.login(employee, employee);
    sessionStorage.setItem("change-lab-scenario", selected.value);
    location.reload();
  } catch (e: any) {
    message.error(e.message || "演练身份登录失败");
  } finally {
    switching.value = false;
  }
}
function selectScenario(id: string) {
  selected.value = id;
  sessionStorage.setItem("change-lab-scenario", id);
}
const previous = sessionStorage.getItem("change-lab-scenario");
if (scenarios.some((s) => s.id === previous)) {
  selected.value = previous!;
  expanded.value = true;
}
</script>
<template>
  <section class="lab" data-testid="change-lab">
    <div class="lab-bar">
      <span class="lab-badge">TEST</span>
      <div>
        <strong>项目变更 · 隔离演练</strong>
        <p>
          演练项目、审批和附件独立保存；当前身份：{{ user.realName }}（{{
            user.employeeNo
          }}）
        </p>
      </div>
      <a-button @click="expanded = !expanded">{{
        expanded ? "收起场景" : "使用场景与操作脚本"
      }}</a-button>
    </div>
    <div v-if="expanded" class="lab-content">
      <nav aria-label="演练场景">
        <button
          v-for="s in scenarios"
          :key="s.id"
          :class="{ active: selected === s.id }"
          @click="selectScenario(s.id)"
        >
          <small>{{ s.id }}</small
          >{{ s.title }}
        </button>
      </nav>
      <article>
        <h3>{{ scenario.title }}</h3>
        <p><b>场景目标：</b>{{ scenario.goal }}</p>
        <p class="roles">办理顺序：{{ scenario.roles }}</p>
        <ol>
          <li v-for="step in scenario.steps" :key="step">{{ step }}</li>
        </ol>
        <div class="expected"><b>验收目标</b> {{ scenario.expected }}</div>
        <a-button
          type="primary"
          :disabled="user.employeeNo !== scenario.actor"
          @click="emit('start', scenario)"
          >开始此场景</a-button
        >
        <span class="hint"
          >先切换到
          {{ scenario.actor }}；仅预填草稿，需亲自保存、上传和审批。</span
        >
        <div class="role-switch">
          <b>切换演练身份</b
          ><a-button
            v-for="[employee, label] in roles"
            :key="employee"
            size="small"
            :loading="switching"
            :disabled="user.employeeNo === employee"
            @click="switchRole(employee)"
            >{{ label }} · {{ employee }}</a-button
          >
        </div>
        <p class="hint">
          演练账号密码与工号相同。演练法务为测试配置，不代表正式人员资质。场景不会自动判定人工验收通过。
        </p>
      </article>
    </div>
  </section>
</template>
<style scoped>
.lab {
  border: 1px solid #d4b976;
  background: #fffcf4;
  margin-bottom: 16px;
  border-radius: 3px;
  color: #5e4b26;
}
.lab-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 18px;
}
.lab-bar > div {
  flex: 1;
}
.lab-bar p {
  margin: 3px 0 0;
  font-size: 12px;
}
.lab-badge {
  font-size: 11px;
  letter-spacing: 1px;
  color: white;
  background: #9b721b;
  padding: 5px 8px;
  font-weight: 700;
}
.lab-content {
  display: grid;
  grid-template-columns: 240px 1fr;
  border-top: 1px solid #e6d9b8;
}
.lab-content nav {
  padding: 10px;
  border-right: 1px solid #e6d9b8;
}
.lab-content nav button {
  display: block;
  width: 100%;
  border: 0;
  background: transparent;
  padding: 11px 8px;
  text-align: left;
  font-size: 12px;
  color: #5e4b26;
  cursor: pointer;
  border-left: 3px solid transparent;
}
.lab-content nav button.active {
  background: #f0e6cb;
  border-left-color: #997220;
}
.lab-content small {
  margin-right: 8px;
  opacity: 0.65;
}
.lab-content article {
  padding: 18px 22px;
  font-size: 13px;
  min-width: 0;
}
.lab-content h3 {
  margin: 0 0 10px;
  color: #483c22;
}
.lab-content li {
  padding-bottom: 7px;
  line-height: 1.6;
}
.lab-content ol {
  padding-left: 22px;
}
.roles,
.expected {
  background: #f4eddc;
  padding: 9px 12px;
}
.expected {
  margin-bottom: 12px;
}
.hint {
  font-size: 12px;
  color: #796b4e;
  margin-left: 8px;
}
.role-switch {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
  border-top: 1px solid #e6d9b8;
  margin-top: 18px;
  padding-top: 14px;
}
.role-switch b {
  font-size: 12px;
  width: 100%;
}
@media (max-width: 1000px) {
  .lab-content {
    grid-template-columns: 1fr;
  }
  .lab-content nav {
    display: flex;
    overflow: auto;
    border-right: 0;
  }
  .lab-content nav button {
    min-width: 190px;
  }
  .lab-bar {
    flex-wrap: wrap;
  }
  .hint {
    display: block;
    margin: 8px 0;
  }
}
</style>
