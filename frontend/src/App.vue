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
    colorTextTertiary: '#BFBFBF',
    colorBorder: '#E8E8E8',
    colorBorderSecondary: '#F0F0F0',
    colorBgLayout: '#F5F7FA',
    colorBgContainer: '#FFFFFF',
    colorBgElevated: '#FFFFFF',
    colorFillAlter: '#FAFAFA',
    colorFillSecondary: '#F5F7FA',
    colorSplit: '#E8E8E8',
    borderRadius: 4,
    borderRadiusLG: 6,
    fontSize: 14,
    fontSizeHeading3: 20,
    fontSizeHeading5: 16,
    lineHeight: 1.5715,
    controlHeight: 32,
    controlHeightLG: 40,
    controlHeightSM: 28,
    padding: 16,
    paddingLG: 20,
    paddingSM: 12,
    paddingXS: 8,
    margin: 16,
    marginLG: 20,
    marginXS: 8,
    boxShadowSecondary: '0 6px 20px rgba(31, 35, 41, 0.08)',
  },
  components: {
    Layout: {
      headerBg: '#0048A0',
      headerHeight: 64,
      siderBg: '#FFFFFF',
      bodyBg: '#F5F7FA',
    },
    Menu: {
      itemHeight: 40,
      itemMarginInline: 8,
      itemBorderRadius: 4,
      itemSelectedBg: '#E8F1FF',
      itemSelectedColor: '#0064EF',
      itemHoverBg: '#F5F7FA',
      itemHoverColor: '#0064EF',
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
      rowHoverBg: '#F7FAFF',
      headerSortHoverBg: '#F0F5FF',
    },
    Card: {
      headerBg: '#FFFFFF',
      headerFontSize: 16,
    },
    Tabs: {
      itemActiveColor: '#0064EF',
      itemHoverColor: '#1A75F0',
      itemSelectedColor: '#0064EF',
      inkBarColor: '#0064EF',
    },
    Form: {
      labelColor: '#595959',
      labelFontSize: 14,
    },
    Modal: {
      titleFontSize: 16,
      contentBg: '#FFFFFF',
      headerBg: '#FFFFFF',
      footerBg: '#FFFFFF',
    },
    Drawer: {
      colorBgElevated: '#FFFFFF',
    },
  },
}
</script>

<template>
  <a-config-provider :theme="themeConfig" :locale="zhCN">
    <router-view />
  </a-config-provider>
</template>
