<template>
  <div class="card">
    <div class="card-header">
      <span>代码迁移类别占比</span>
    </div>
    <e-charts-wrapper :option="chartOption" />
  </div>
</template>

<script>
import EChartsWrapper from '@/components/charts/EChartsWrapper.vue';

export default {
  name: 'CategoryDonut',
  components: {
    EChartsWrapper,
  },
  props: {
    stats: {
      type: Array,
      default: () => [],
    },
  },
  computed: {
    chartOption() {
      const seriesData = this.stats.map((item) => ({
        name: item.label,
        value: item.lineCount,
        itemStyle: {
          color: item.color,
        },
      }));
      return {
        tooltip: {
          trigger: 'item',
          formatter: '{b}: {c} 行 ({d}%)',
        },
        legend: {
          orient: 'vertical',
          left: 'left',
        },
        series: [
          {
            name: '迁移占比',
            type: 'pie',
            radius: ['50%', '75%'],
            avoidLabelOverlap: false,
            itemStyle: {
              borderRadius: 4,
              borderColor: '#fff',
              borderWidth: 2,
            },
            label: {
              show: false,
            },
            emphasis: {
              label: {
                show: true,
                fontSize: 16,
                fontWeight: 'bold',
              },
            },
            labelLine: {
              show: false,
            },
            data: seriesData,
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
  font-weight: 600;
  margin-bottom: 12px;
  color: #1f2933;
}
</style>
