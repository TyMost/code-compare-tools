<template>
  <div class="block-mapping-viewer">
    <div class="block-mapping-viewer__header">
      <div class="header-title">
        <i class="el-icon-connection"></i>
        <span>块级映射关系（无阈值算法）</span>
      </div>
      <div class="header-stats">
        <span class="stat-item">
          <span class="stat-label">覆盖率:</span>
          <span class="stat-value">{{ formatPercent(blockMapping.coverage) }}</span>
        </span>
        <span class="stat-item">
          <span class="stat-label">总块数:</span>
          <span class="stat-value">{{ blockMapping.totalOracleCount }}</span>
        </span>
        <span class="stat-item">
          <span class="stat-label">平均相似度:</span>
          <span class="stat-value">{{ formatPercent(averageSimilarity) }}</span>
        </span>
      </div>
    </div>

    <div class="block-mapping-viewer__content">
      <!-- 相似度分布图 -->
      <div v-if="allBlocks.length > 0" class="similarity-distribution">
        <div class="section-title">
          <i class="el-icon-data-analysis"></i>
          <span>相似度分布</span>
        </div>
        <div class="distribution-chart">
          <div class="chart-bars">
            <div 
              v-for="(bucket, index) in similarityBuckets" 
              :key="'bucket-' + index"
              class="chart-bar"
              :style="{ height: bucket.height + '%', backgroundColor: bucket.color }"
              :title="`${bucket.range}: ${bucket.count}个块`"
            ></div>
          </div>
          <div class="chart-labels">
            <span v-for="(bucket, index) in similarityBuckets" :key="'label-' + index">
              {{ bucket.range }}
            </span>
          </div>
        </div>
      </div>

      <!-- 所有块（按相似度排序） -->
      <div v-if="allBlocks.length > 0" class="all-blocks-section">
        <div class="section-title">
          <i class="el-icon-list"></i>
          <span>所有ΔO块（按相似度排序）</span>
          <div class="section-controls">
            <el-button size="mini" @click="sortBySimilarity" :icon="sortDesc ? 'el-icon-sort-down' : 'el-icon-sort-up'">
              相似度 {{ sortDesc ? '降序' : '升序' }}
            </el-button>
          </div>
        </div>
        <div class="all-blocks">
          <div 
            v-for="(block, index) in sortedBlocks" 
            :key="'block-' + index"
            class="block-item"
            :class="getBlockClass(block)"
          >
            <div class="block-header">
              <span class="block-type">ΔO</span>
              <span class="block-line">行 {{ block.oracleBlock.startLineTo }}-{{ block.oracleBlock.endLineTo }}</span>
              <span class="similarity-indicator" :style="{ backgroundColor: getSimilarityColor(block.similarity) }">
                {{ formatPercent(block.similarity) }}
              </span>
            </div>
            
            <!-- 匹配的块显示连接关系 -->
            <div v-if="block.gaussBlock" class="match-relationship">
              <div class="block-side oracle-side">
                <div class="block-content">
                  <pre>{{ block.oracleBlock.contentTo || block.oracleBlock.contentFrom }}</pre>
                </div>
              </div>

              <!-- 连接线 -->
              <div class="connection-line">
                <div class="connection-arrow">
                  <i class="el-icon-arrow-right"></i>
                </div>
                <div class="similarity-detail">
                  <span class="similarity-value">{{ formatPercent(block.similarity) }}</span>
                  <span class="similarity-label">{{ getSimilarityLevel(block.similarity) }}</span>
                </div>
              </div>

              <!-- ΔG块 -->
              <div class="block-side gauss-side">
                <div class="block-content">
                  <pre>{{ block.gaussBlock.contentTo || block.gaussBlock.contentFrom }}</pre>
                </div>
              </div>
            </div>
            
            <!-- 未匹配的块只显示内容 -->
            <div v-else class="unmatched-content">
              <div class="block-content">
                <pre>{{ block.oracleBlock.contentTo || block.oracleBlock.contentFrom }}</pre>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="isEmpty" class="empty-state">
        <i class="el-icon-document-remove"></i>
        <div>暂无块映射数据</div>
        <div class="empty-tip">请确保已选择文件并生成了覆盖率分析</div>
      </div>
    </div>

    <!-- 展开/收起控制 -->
    <div class="block-mapping-viewer__footer">
      <el-button 
        size="mini" 
        @click="toggleExpanded"
        :icon="isExpanded ? 'el-icon-arrow-up' : 'el-icon-arrow-down'"
      >
        {{ isExpanded ? '收起详情' : '展开详情' }}
      </el-button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'BlockMappingViewer',
  props: {
    blockMapping: {
      type: Object,
      default: () =>({
        coverage: 0,
        matchedCount: 0,
        totalOracleCount: 0,
        totalGaussCount: 0,
        matchedOracleBlocks: [],
        matchedGaussBlocks: [],
        unmatchedOracle: [],
        unmatchedGauss: [],
        matchDetails: []
      })
    },
    loading: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      isExpanded: true,
      sortDesc: true, // 默认降序
    };
  },
  computed: {
    // 所有块（匹配的 + 未匹配的）
    allBlocks() {
      const blocks = [];
      
      // 添加匹配的块
      if (this.blockMapping.matchDetails) {
        this.blockMapping.matchDetails.forEach(detail => {
          blocks.push({
            oracleBlock: detail.oracleBlock,
            gaussBlock: detail.gaussBlock,
            similarity: detail.similarity || 0,
            isMatched: true
          });
        });
      }
      
      // 添加未匹配的块
      if (this.blockMapping.unmatchedOracle) {
        this.blockMapping.unmatchedOracle.forEach(block => {
          blocks.push({
            oracleBlock: block,
            gaussBlock: null,
            similarity: 0,
            isMatched: false
          });
        });
      }
      
      return blocks;
    },
    
    // 按相似度排序的块
    sortedBlocks() {
      const sorted = [...this.allBlocks];
      sorted.sort((a, b) => {
        return this.sortDesc ? b.similarity - a.similarity : a.similarity - b.similarity;
      });
      return sorted;
    },
    
    // 平均相似度
    averageSimilarity() {
      if (this.allBlocks.length === 0) return 0;
      const total = this.allBlocks.reduce((sum, block) => sum + block.similarity, 0);
      return total / this.allBlocks.length;
    },
    
    // 相似度分布桶
    similarityBuckets() {
      const buckets = [
        { range: '0-20%', min: 0, max: 0.2, count: 0, color: '#f56c6c' },
        { range: '20-40%', min: 0.2, max: 0.4, count: 0, color: '#e6a23c' },
        { range: '40-60%', min: 0.4, max: 0.6, count: 0, color: '#f7ba2a' },
        { range: '60-80%', min: 0.6, max: 0.8, count: 0, color: '#67c23a' },
        { range: '80-100%', min: 0.8, max: 1.0, count: 0, color: '#409eff' }
      ];
      
      // 统计每个桶的数量
      this.allBlocks.forEach(block => {
        for (let bucket of buckets) {
          if (block.similarity >= bucket.min && block.similarity < bucket.max) {
            bucket.count++;
            break;
          }
        }
      });
      
      // 计算高度百分比
      const maxCount = Math.max(...buckets.map(b => b.count), 1);
      buckets.forEach(bucket => {
        bucket.height = (bucket.count / maxCount) * 100;
      });
      
      return buckets;
    },
    
    isEmpty() {
      return this.blockMapping.totalOracleCount === 0 && this.blockMapping.totalGaussCount === 0;
    }
  },
  methods: {
    formatPercent(value) {
      if (value === undefined || value === null) {
        return '--';
      }
      return `${(value * 100).toFixed(1)}%`;
    },
    
    toggleExpanded() {
      this.isExpanded = !this.isExpanded;
    },
    
    sortBySimilarity() {
      this.sortDesc = !this.sortDesc;
    },
    
    // 根据相似度获取颜色
    getSimilarityColor(similarity) {
      if (similarity >= 0.8) return '#67c23a';
      if (similarity >= 0.6) return '#409eff';
      if (similarity >= 0.4) return '#e6a23c';
      if (similarity >= 0.2) return '#f7ba2a';
      return '#f56c6c';
    },
    
    // 根据相似度获取级别描述
    getSimilarityLevel(similarity) {
      if (similarity >= 0.8) return '高度相似';
      if (similarity >= 0.6) return '较相似';
      if (similarity >= 0.4) return '中等相似';
      if (similarity >= 0.2) return '低相似';
      return '极低相似';
    },
    
    // 获取块的样式类
    getBlockClass(block) {
      const classes = ['block-item'];
      if (!block.gaussBlock) {
        classes.push('unmatched');
      } else {
        if (block.similarity >= 0.8) classes.push('high-similarity');
        else if (block.similarity >= 0.6) classes.push('medium-similarity');
        else if (block.similarity >= 0.4) classes.push('low-similarity');
        else classes.push('very-low-similarity');
      }
      return classes.join(' ');
    }
  }
};
</script>

<style scoped>
.block-mapping-viewer {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: white;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}

.block-mapping-viewer__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: #f8f9fa;
  border-bottom: 1px solid #ebeef5;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #303133;
}

.header-title i {
  color: #409eff;
}

.header-stats {
  display: flex;
  gap: 16px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
}

.stat-label {
  color: #909399;
}

.stat-value {
  font-weight: 600;
  color: #303133;
}

.block-mapping-viewer__content {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 16px;
  font-weight: 600;
  color: #303133;
}

.section-title i {
  font-size: 16px;
}

.section-controls {
  display: flex;
  gap: 8px;
}

/* 相似度分布图 */
.similarity-distribution {
  margin-bottom: 24px;
}

.distribution-chart {
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 16px;
}

.chart-bars {
  display: flex;
  align-items: flex-end;
  height: 80px;
  gap: 8px;
  margin-bottom: 8px;
}

.chart-bar {
  flex: 1;
  min-height: 4px;
  border-radius: 2px 2px 0 0;
  transition: height 0.3s ease;
  cursor: pointer;
}

.chart-bar:hover {
  opacity: 0.8;
}

.chart-labels {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #909399;
}

/* 所有块展示 */
.all-blocks-section {
  margin-bottom: 24px;
}

.all-blocks {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.block-item {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: white;
  overflow: hidden;
  transition: all 0.3s ease;
}

.block-item:hover {
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.block-item.high-similarity {
  border-left: 4px solid #67c23a;
}

.block-item.medium-similarity {
  border-left: 4px solid #409eff;
}

.block-item.low-similarity {
  border-left: 4px solid #e6a23c;
}

.block-item.very-low-similarity {
  border-left: 4px solid #f56c6c;
}

.block-item.unmatched {
  border-left: 4px solid #909399;
  background: #f5f7fa;
}

.block-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #fafafa;
  border-bottom: 1px solid #ebeef5;
  flex-wrap: wrap;
  gap: 8px;
}

.block-type {
  font-weight: 600;
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 3px;
  background: #409eff;
  color: white;
}

.block-line {
  font-size: 12px;
  color: #606266;
  font-family: 'Fira Code', 'Courier New', monospace;
}

.similarity-indicator {
  font-weight: 600;
  color: white;
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 3px;
  min-width: 50px;
  text-align: center;
}

.match-relationship {
  display: flex;
  align-items: stretch;
}

.unmatched-content {
  padding: 16px;
}

.block-side {
  flex: 1;
  padding: 16px;
  min-width: 0;
}

.oracle-side {
  background: #fdf6ec;
  border-right: 1px solid #e4e7ed;
}

.gauss-side {
  background: #f0f9eb;
}

.block-content {
  background: white;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px;
  max-height: 400px;
  overflow-y: auto;
}

.block-content pre {
  margin: 0;
  font-family: 'Fira Code', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.4;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}

.connection-line {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 16px 8px;
  background: #f5f7fa;
  min-width: 120px;
}

.connection-arrow {
  color: #409eff;
  font-size: 16px;
  margin-bottom: 8px;
}

.similarity-detail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.similarity-value {
  font-weight: 600;
  font-size: 12px;
}

.similarity-label {
  font-size: 10px;
  color: #909399;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #909399;
  text-align: center;
}

.empty-state i {
  font-size: 32px;
  margin-bottom: 16px;
  opacity: 0.6;
}

.empty-state div {
  margin-bottom: 8px;
  font-size: 14px;
}

.empty-tip {
  font-size: 12px;
  color: #c0c4cc;
  max-width: 300px;
  text-align: center;
}

.block-mapping-viewer__footer {
  padding: 12px 16px;
  background: #f8f9fa;
  border-top: 1px solid #ebeef5;
  text-align: center;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .match-relationship {
    flex-direction: column;
  }
  
  .oracle-side,
  .gauss-side {
    border-right: none;
    border-bottom: 1px solid #e4e7ed;
  }
  
  .connection-line {
    padding: 12px;
    min-width: auto;
    flex-direction: row;
  }
  
  .connection-arrow {
    margin-bottom: 0;
    margin-right: 8px;
  }
  
  .block-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }
  
  .header-stats {
    flex-wrap: wrap;
    gap: 8px;
  }
  
  .chart-bars {
    height: 60px;
  }
  
  .block-content {
    max-height: 300px;
  }
}
</style>
