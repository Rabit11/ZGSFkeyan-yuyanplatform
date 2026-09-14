<script setup lang="ts">
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { theme } from 'ant-design-vue'

/**
 * 《经营管理类系统统一UI风格规范》
 * - 标题栏商飞蓝 #0048A0
 * - 品牌主色 #0064EF
 * - 控件默认高 32、圆角 4
 */
const themeConfig = {
  algorithm: theme.defaultAlgorithm,
  token: {
    colorPrimary: '#0064EF',
    colorInfo: '#0064EF',
    colorLink: '#0064EF',
    colorSuccess: '#52C41A',
    colorWarning: '#FAAD14',
    colorError: '#F5222D',
    colorText: '#262626',
    colorTextSecondary: '#8C8C8C',
    colorBorder: '#E8E8E8',
    colorBgLayout: '#F5F7FA',
    colorBgContainer: '#FFFFFF',
    borderRadius: 4,
    fontSize: 14,
    controlHeight: 32,
    controlHeightLG: 40,
    controlHeightSM: 28,
  },
  components: {
    Layout: {
      headerBg: '#0048A0',
      headerHeight: 64,
      siderBg: '#FFFFFF',
      bodyBg: '#F5F7FA',
    },
    Menu: {
      itemSelectedBg: '#E8F1FF',
      itemSelectedColor: '#0064EF',
    },
    Button: {
      controlHeight: 32,
      borderRadius: 4,
    },
    Input: {
      controlHeight: 32,
      borderRadius: 4,
    },
    Select: {
      controlHeight: 32,
      borderRadius: 4,
    },
    Table: {
      headerBg: '#FAFAFA',
      headerColor: '#262626',
      borderColor: '#E8E8E8',
    },
  },
}
</script>

<template>
  <a-config-provider :theme="themeConfig" :locale="zhCN">
    <router-view />
  </a-config-provider>
</template>
