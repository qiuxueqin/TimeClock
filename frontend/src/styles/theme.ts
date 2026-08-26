import type { ThemeConfig } from 'antd';

/** 品牌渐变：淡紫 → 天蓝，用于 Logo、登录页标题区、强调卡片。 */
export const BRAND_GRADIENT = 'linear-gradient(135deg, #8B7CF6 0%, #4DA8F5 100%)';
export const BRAND_PURPLE = '#7666F0';
export const BRAND_SKY = '#4DA8F5';

const sharedTokens = {
  fontFamily:
    "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'PingFang SC', 'Microsoft YaHei', sans-serif",
  borderRadius: 12,
  controlHeight: 36,
};

export const lightTheme: ThemeConfig = {
  token: {
    ...sharedTokens,
    colorPrimary: BRAND_PURPLE,
    colorInfo: '#3FA0F0',
    colorLink: '#3FA0F0',
    colorBgLayout: '#F5F6FB',
    colorBorderSecondary: '#ECEDF4',
    colorTextSecondary: '#5A5D73',
    boxShadowTertiary: '0 1px 3px rgba(80, 80, 140, 0.08)',
    wireframe: false,
  },
  components: {
    Card: { borderRadiusLG: 16 },
    Button: { borderRadius: 10, controlHeight: 36 },
    Progress: { remainingColor: '#E7E9F2' },
  },
};

export const darkTheme: ThemeConfig = {
  token: {
    ...sharedTokens,
    colorPrimary: '#9083FF',
    colorInfo: '#5BB6FF',
    colorLink: '#5BB6FF',
  },
  components: {
    Card: { borderRadiusLG: 16 },
    Button: { borderRadius: 10, controlHeight: 36 },
  },
};
