<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useDictStore } from '@/stores/dict'
import { LEVEL_TEXT } from '@/api/types'

const dictStore = useDictStore()
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    await dictStore.loadChannels()
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">项目渠道字典</h2>
    <div class="page-desc">
      按照国家、地方、公司级项目所属渠道实现分类管控、分类统计、分类展示。填写规则：
      ① 项目层级立项后原则上不允许变更，特殊情况需走「数据变更」审批流程；
      ② 新增/终止项目渠道需由责任单位科技部提交申请，经总部科技部审批后由超级管理员维护；
      ③ <b>所有项目渠道编码全局唯一，禁止重复</b>。
    </div>

    <a-card :body-style="{ padding: '16px 20px' }">
      <a-spin :spinning="loading">
        <a-table :data-source="dictStore.channels" row-key="id" :pagination="false" :scroll="{ x: 1500 }"
          :columns="[
            { title: '项目层级', dataIndex: 'levelCode', width: 100, fixed: 'left' },
            { title: '渠道编码', dataIndex: 'channelCode', width: 120 },
            { title: '渠道名称', dataIndex: 'channelName', width: 240 },
            { title: '部委/委局', dataIndex: 'channelDept', width: 130 },
            { title: '司局/处室', dataIndex: 'channelOffice', width: 130 },
            { title: '内部管理部门', dataIndex: 'innerDept', width: 130 },
            { title: '内部管理处室', dataIndex: 'innerOffice', width: 130 },
            { title: '全周期流程节点', dataIndex: 'flowNodes' },
            { title: '申报材料', dataIndex: 'declareMaterial', width: 180 },
            { title: '立项材料', dataIndex: 'filingMaterial', width: 180 },
          ]">
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'levelCode'">
              <a-tag :color="record.levelCode === 'NATIONAL' ? 'red' : record.levelCode === 'LOCAL' ? 'orange' : 'blue'">
                {{ LEVEL_TEXT[record.levelCode] }}
              </a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'channelName'">
              <b>{{ record.channelName }}</b>
            </template>
          </template>
        </a-table>
      </a-spin>
    </a-card>
  </div>
</template>
