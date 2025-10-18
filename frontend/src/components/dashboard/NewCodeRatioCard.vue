<template>
  <div class="card">
    <div class="card-header">
      <span>新代码提交占比</span>
      <span class="total">总行数：{{ formattedTotalLines }}</span>
    </div>
    <e-charts-wrapper :option="chartOption" />
  </div>
</template>

<script>
import EChartsWrapper from '@/components/charts/EChartsWrapper.vue';

export default {
  name: 'NewCodeRatioCard',
  components: {
    EChartsWrapper,
  },
  props: {
    ratio: {
      type: Number,
      default: 0,
    },
    totalLines: {
      type: Number,
      default: 0,
    },
  },
  computed: {
    percentage() {
      return Math.round((this.ratio || 0) * 100);
    },
    formattedTotalLines() {
      if (!this.totalLines) {
        return '-';
      }
      return `${this.totalLines.toLocaleString()} 行`;
    },
    chartOption() {
      return {
        series: [
          {
            type: 'gauge',
            startAngle: 210,
            endAngle: -30,
            radius: '90%',
            progress: {
              show: true,
              width: 12,
            },
            pointer: {
              show: true,
              length: '80%',
              width: 4,
            },
            axisLine: {
              lineStyle: {
                width: 12,
                color: [
                  [0.3, '#f87171'],
                  [0.6, '#fbbf24'],
                  [1, '#34d399'],
                ],
              },
            },
            detail: {
              valueAnimation: true,
              formatter: '{value}%',
              color: '#111827',
              fontSize: 24,
            },
            data: [
              {
                value: this.percentage,
                name: '新代码占比',
              },
            ],
          },
        ],
      };
    },
  },
};
</script>

<style scoped lang="scss">
.card {
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(31, 56, 88, 0.08);
  padding: 16px;
  height: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  font-weight: 600;
  color: #1f2933;
  margin-bottom: 12px;
}

.total {
  font-size: 13px;
  font-weight: 400;
  color: #6b7280;
}
</style>
