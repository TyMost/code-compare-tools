<template>
  <div class="async-export-task-manager">
    <div class="task-manager__header">
      <h3>异步导出任务</h3>
      <div class="header-actions">
        <el-button
          size="small"
          type="primary"
          :loading="loadingTasks"
          @click="refreshTasks"
        >
          刷新任务
        </el-button>
        <el-button
          size="small"
          type="warning"
          @click="cleanupExpiredTasks"
        >
          清理过期任务
        </el-button>
      </div>
    </div>

    <div class="task-manager__content">
      <el-table
        v-loading="loadingTasks"
        :data="asyncTasks"
        size="small"
        stripe
        empty-text="暂无异步导出任务"
      >
        <el-table-column prop="taskId" label="任务ID" width="200" show-overflow-tooltip>
          <template slot-scope="{ row }">
            <el-link
              type="primary"
              @click="copyTaskId(row.taskId)"
              :underline="false"
            >
              {{ row.taskId }}
            </el-link>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="100">
          <template slot-scope="{ row }">
            <el-tag
              :type="getStatusTagType(row.status)"
              size="mini"
            >
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="progressPercentage" label="进度" width="120">
          <template slot-scope="{ row }">
            <el-progress
              v-if="row.isProcessing"
              :percentage="row.progressPercentage"
              :stroke-width="6"
              :show-text="false"
            />
            <span v-else-if="row.isFinished">
              {{ row.isDownloadable ? '已完成' : '已结束' }}
            </span>
            <span v-else>等待中</span>
          </template>
        </el-table-column>

        <el-table-column prop="currentStage" label="当前阶段" min-width="120" show-overflow-tooltip />

        <el-table-column prop="fileName" label="文件名" min-width="150" show-overflow-tooltip />

        <el-table-column prop="fileSize" label="文件大小" width="100">
          <template slot-scope="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>

        <el-table-column prop="createdAt" label="创建时间" width="150">
          <template slot-scope="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="150" fixed="right">
          <template slot-scope="{ row }">
            <el-button
              v-if="row.isDownloadable"
              size="mini"
              type="success"
              @click="downloadFile(row.taskId)"
            >
              下载
            </el-button>
            <el-button
              v-if="row.isProcessing"
              size="mini"
              type="warning"
              @click="cancelTask(row.taskId)"
            >
              取消
            </el-button>
            <el-button
              v-if="row.errorMessage"
              size="mini"
              type="info"
              @click="showError(row.errorMessage)"
            >
              查看错误
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 当前任务详情 -->
    <div v-if="currentAsyncTask" class="current-task-detail">
      <el-card header="当前任务详情">
        <div class="task-detail">
          <div class="detail-item">
            <label>任务ID:</label>
            <span>{{ currentAsyncTask.taskId }}</span>
          </div>
          <div class="detail-item">
            <label>状态:</label>
            <el-tag :type="getStatusTagType(currentAsyncTask.status)" size="mini">
              {{ getStatusText(currentAsyncTask.status) }}
            </el-tag>
          </div>
          <div v-if="currentAsyncTask.progressMessage" class="detail-item">
            <label>进度信息:</label>
            <span>{{ currentAsyncTask.progressMessage }}</span>
          </div>
          <div v-if="currentAsyncTask.currentStage" class="detail-item">
            <label>当前阶段:</label>
            <span>{{ currentAsyncTask.currentStage }}</span>
          </div>
          <div v-if="currentAsyncTask.progressPercentage > 0" class="detail-item">
            <label>进度:</label>
            <el-progress
              :percentage="currentAsyncTask.progressPercentage"
              :stroke-width="8"
            />
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script>
import { mapState, mapActions } from 'vuex';

export default {
  name: 'AsyncExportTaskManager',
  data() {
    return {
      pollingTimer: null,
    };
  },
  computed: {
    ...mapState('diff', [
      'asyncExportTasks',
      'loadingAsyncExportTasks',
      'currentAsyncTask',
    ]),
    asyncTasks() {
      return Array.isArray(this.asyncExportTasks) ? this.asyncExportTasks : [];
    },
  },
  mounted() {
    this.refreshTasks();
    this.startPolling();
  },
  beforeDestroy() {
    this.stopPolling();
  },
  methods: {
    ...mapActions('diff', [
      'fetchAllAsyncExportTasks',
      'downloadAsyncExportFile',
      'cancelAsyncExportTask',
      'cleanupExpiredAsyncExportTasks',
    ]),
    
    async refreshTasks() {
      try {
        await this.fetchAllAsyncExportTasks();
      } catch (error) {
        this.$message.error('刷新任务列表失败: ' + (error.message || error));
      }
    },

    startPolling() {
      // 每10秒轮询一次任务状态
      this.pollingTimer = setInterval(() => {
        this.refreshTasks();
      }, 10000);
    },

    stopPolling() {
      if (this.pollingTimer) {
        clearInterval(this.pollingTimer);
        this.pollingTimer = null;
      }
    },

    async downloadFile(taskId) {
      try {
        await this.downloadAsyncExportFile(taskId);
        this.$message.success('文件下载成功');
      } catch (error) {
        this.$message.error('下载失败: ' + (error.message || error));
      }
    },

    async cancelTask(taskId) {
      try {
        await this.$confirm('确定要取消这个导出任务吗？', '确认取消', {
          type: 'warning',
        });
        
        await this.cancelAsyncExportTask(taskId);
        this.$message.success('任务已取消');
        this.refreshTasks();
      } catch (error) {
        if (error !== 'cancel') {
          this.$message.error('取消任务失败: ' + (error.message || error));
        }
      }
    },

    async cleanupExpiredTasks() {
      try {
        await this.$confirm('确定要清理所有过期的导出任务吗？', '确认清理', {
          type: 'warning',
        });
        
        await this.cleanupExpiredAsyncExportTasks();
        this.$message.success('过期任务清理完成');
        this.refreshTasks();
      } catch (error) {
        if (error !== 'cancel') {
          this.$message.error('清理失败: ' + (error.message || error));
        }
      }
    },

    copyTaskId(taskId) {
      if (navigator.clipboard) {
        navigator.clipboard.writeText(taskId).then(() => {
          this.$message.success('任务ID已复制到剪贴板');
        });
      } else {
        // 降级方案
        const textArea = document.createElement('textarea');
        textArea.value = taskId;
        document.body.appendChild(textArea);
        textArea.select();
        document.execCommand('copy');
        document.body.removeChild(textArea);
        this.$message.success('任务ID已复制到剪贴板');
      }
    },

    showError(errorMessage) {
      this.$alert(errorMessage, '错误详情', {
        type: 'error',
        customClass: 'error-dialog',
      });
    },

    getStatusTagType(status) {
      const statusMap = {
        'PENDING': 'info',
        'PROCESSING': 'warning',
        'COMPLETED': 'success',
        'FAILED': 'danger',
        'CANCELLED': 'info',
      };
      return statusMap[status] || 'info';
    },

    getStatusText(status) {
      const statusMap = {
        'PENDING': '等待中',
        'PROCESSING': '处理中',
        'COMPLETED': '已完成',
        'FAILED': '失败',
        'CANCELLED': '已取消',
      };
      return statusMap[status] || status;
    },

    formatFileSize(size) {
      if (!size || size === 0) return '--';
      
      const units = ['B', 'KB', 'MB', 'GB'];
      let unitIndex = 0;
      let fileSize = size;
      
      while (fileSize >= 1024 && unitIndex < units.length - 1) {
        fileSize /= 1024;
        unitIndex++;
      }
      
      return `${fileSize.toFixed(1)} ${units[unitIndex]}`;
    },

    formatTime(timeStr) {
      if (!timeStr) return '--';
      
      try {
        const date = new Date(timeStr);
        if (Number.isNaN(date.getTime())) return timeStr;
        
        return date.toLocaleString();
      } catch (error) {
        return timeStr;
      }
    },
  },
};
</script>

<style scoped>
.async-export-task-manager {
  padding: 16px;
}

.task-manager__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.task-manager__header h3 {
  margin: 0;
  color: #303133;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.task-manager__content {
  margin-bottom: 20px;
}

.current-task-detail {
  margin-top: 20px;
}

.task-detail {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 12px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-item label {
  font-weight: 500;
  color: #606266;
  font-size: 12px;
}

.detail-item span {
  color: #303133;
  font-size: 14px;
}

/* 错误对话框样式 */
::v-deep .error-dialog {
  max-width: 600px;
}

::v-deep .error-dialog .el-message-box__content {
  max-height: 300px;
  overflow-y: auto;
  white-space: pre-wrap;
  font-family: 'Courier New', monospace;
  font-size: 12px;
}
</style>
