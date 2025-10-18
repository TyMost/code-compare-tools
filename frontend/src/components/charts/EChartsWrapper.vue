<template>
  <div ref="canvas" class="echarts-wrapper"></div>
</template>

<script>
import * as echarts from 'echarts/core';
import { PieChart, GaugeChart, BarChart } from 'echarts/charts';
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

echarts.use([
  PieChart,
  GaugeChart,
  BarChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  CanvasRenderer,
]);

export default {
  name: 'EChartsWrapper',
  props: {
    option: {
      type: Object,
      required: true,
    },
    theme: {
      type: String,
      default: null,
    },
    autoresize: {
      type: Boolean,
      default: true,
    },
  },
  data() {
    return {
      chart: null,
    };
  },
  mounted() {
    this.initChart();
    if (this.autoresize) {
      window.addEventListener('resize', this.handleResize);
    }
  },
  beforeDestroy() {
    if (this.autoresize) {
      window.removeEventListener('resize', this.handleResize);
    }
    if (this.chart) {
      this.chart.dispose();
      this.chart = null;
    }
  },
  watch: {
    option: {
      deep: true,
      handler(val) {
        if (this.chart) {
          this.chart.setOption(val, true);
        }
      },
    },
  },
  methods: {
    initChart() {
      if (this.chart) {
        this.chart.dispose();
      }
      this.chart = echarts.init(this.$refs.canvas, this.theme || undefined);
      this.chart.setOption(this.option);
    },
    handleResize() {
      if (this.chart) {
        this.chart.resize();
      }
    },
  },
};
</script>

<style scoped>
.echarts-wrapper {
  width: 100%;
  height: 320px;
}
</style>
