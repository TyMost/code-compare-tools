<template>
  <div class="file-diff-page">
    <!-- 初始加载状态 -->
    <div v-if="!initialized && (loadingDetail || loadingMatrix)" class="initial-loading">
      <el-card>
        <div class="loading-content">
          <i class="el-icon-loading loading-icon"></i>
          <div class="loading-text">正在加载数据，请稍候...</div>
          <div class="loading-tips">如果长时间无响应，请检查网络连接或刷新页面</div>
        </div>
      </el-card>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="loadError" class="error-state">
      <el-card>
        <div class="error-content">
          <i class="el-icon-warning-outline error-icon"></i>
          <div class="error-message">{{ loadError }}</div>
          <el-button type="primary" @click="retryLoad">重试</el-button>
        </div>
      </el-card>
    </div>

    <!-- 主要内容 -->
    <el-row v-else :gutter="16">
      <el-col :span="24">
        <el-card>
          <div class="file-diff-page__info">
            <div>
              <div class="file-diff-page__path">{{ currentFile.filePath || '请选择文件' }}</div>
              <div class="file-diff-page__meta">
                <span>模块：{{ currentFile.module || '--' }}</span>
                <span>覆盖率：{{ formatPercent(currentFile.coverage) }}</span>
                <span>ΔO：+{{ currentFile.stats.oracleAdded }}/-{{ currentFile.stats.oracleRemoved }}</span>
                <span>ΔG：+{{ currentFile.stats.gaussAdded }}/-{{ currentFile.stats.gaussRemoved }}</span>
              </div>
            </div>
            <div class="file-diff-page__actions">
              <el-button-group>
                <el-button size="mini" @click="navigatePrev" :disabled="!hasPrev">
                  上一文件
                </el-button>
                <el-button size="mini" @click="navigateNext" :disabled="!hasNext">
                  下一文件
                </el-button>
              </el-button-group>
              
              <!-- 返回总览刷新按钮 -->
              <el-button 
                size="mini" 
                type="info" 
                icon="el-icon-back"
                @click="goToDashboard"
                title="返回总览页面进行数据刷新"
              >
                🏠 返回总览
              </el-button>
            </div>
          </div>
          
          
          <!-- 提交历史组件 -->
          <div 
            v-if="shouldShowCommitHistory" 
            class="commit-history-container"
          >
            <div class="commit-history-header" @click="toggleCommitHistory">
              <div class="commit-history-title">
                <i class="el-icon-time"></i>
                <span>OG 提交历史</span>
                <span v-if="commitHistoryStats.total > 0" class="commit-count">
                  {{ commitHistoryStats.total }}次
                </span>
              </div>
              <span class="expand-btn">
                {{ commitHistoryExpanded ? '收起' : '展开' }}
                <i :class="commitHistoryExpanded ? 'el-icon-arrow-up' : 'el-icon-arrow-down'"></i>
              </span>
            </div>
            
            <el-collapse-transition>
              <div v-show="commitHistoryExpanded" class="commit-history-content">
                <div v-loading="loadingCommitHistory" class="commit-timeline">
                  <div 
                    v-for="(commit, index) in mergedCommitHistory" 
                    :key="commit.hash + index"
                    class="timeline-item"
                    @click="openCommitInBrowser(commit)"
                  >
                    <div class="timeline-dot" :class="commit.type"></div>
                    <div class="commit-item">
                      <div class="commit-header">
                        <span class="commit-type" :class="commit.type">
                          {{ commit.type === 'oracle' ? '🟠 O:' : '🟢 G:' }}
                        </span>
                        <a 
                          :href="commit.url" 
                          target="_blank" 
                          class="commit-hash"
                          @click.stop
                        >
                          {{ commit.shortHash }}
                        </a>
                        <span class="commit-time">{{ formatRelativeTime(commit.time) }}</span>
                      </div>
                      <div class="commit-author">
                        <span class="author-name">{{ commit.author }}</span>
                      </div>
                      <div class="commit-message" v-html="commit.formattedMessage"></div>
                      <div class="commit-stats" v-if="commit.stats">
                        <span class="stat-item stat-added">+{{ commit.stats.added || 0 }}</span>
                        <span class="stat-item stat-removed">-{{ commit.stats.removed || 0 }}</span>
                      </div>
                    </div>
                  </div>
                   
                  <!-- 空状态 -->
                  <div v-if="mergedCommitHistory.length === 0 && !loadingCommitHistory" class="empty-state">
                    <i class="el-icon-document-remove"></i>
                    <div>该文件暂无提交历史</div>
                  </div>
                </div>
              </div>
            </el-collapse-transition>
          </div>
          
          <diff-toolbar
            :value="diffMode"
            :loading="migrating"
            :disable-generate="!canOperate"
            :disable-apply="!canApply"
            :disable-revert="!canApply"
            :default-options="viewerOptions"
            @input="handleModeChange"
            @options-change="handleOptionsChange"
            @generate="handleGenerate"
            @apply="handleApply"
            @revert="handleRevert"
            @toggle-block-mapping="toggleBlockMapping"
            :show-block-mapping="showBlockMapping"
          />
          <!-- 块级映射视图 -->
          <div v-if="showBlockMapping" class="block-mapping-section">
            <el-divider content-position="left">
              <span class="section-title">
                <i class="el-icon-connection"></i>
                块级映射关系
              </span>
            </el-divider>
            <block-mapping-viewer
              :block-mapping="blockMappingData"
              :loading="loadingBlockMapping"
            />
          </div>

          <file-diff-viewer
            :file-path="currentFile.filePath"
            :mode="diffMode"
            :oracle-diff="currentFile.oracleDiff"
            :gauss-diff="currentFile.gaussDiff"
            :migration-diff="currentFile.migrationDiff"
            :options="viewerOptions"
          />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import { mapState, mapActions, mapMutations, mapGetters } from 'vuex';
import DiffToolbar from '../components/DiffToolbar.vue';
import FileDiffViewer from '../components/FileDiffViewer.vue';
import BlockMappingViewer from '../components/BlockMappingViewer.vue';
import commitApi from '../api/commit';

export default {
  name: 'FileDiffPage',
  components: {
    DiffToolbar,
    FileDiffViewer,
    BlockMappingViewer,
  },
  props: {
    taskId: {
      type: String,
      default: '',
    },
    filePath: {
      type: String,
      default: '',
    },
    oracleDelta: {
      type: String,
      default: '',
    },
    gaussDelta: {
      type: String,
      default: '',
    },
  },
  data() {
    return {
      initialized: false,
      loadError: null,
      retryCount: 0,
      viewerOptions: {
        inlineView: false,
        ignoreWhitespace: false,
        collapseUnchanged: false,
      },
      // 提交历史相关状态
      commitHistoryExpanded: false,
      loadingCommitHistory: false,
      commitHistoryCache: new Map(), // 性能优化：缓存提交历史
      oracleCommits: [],
      gaussCommits: [],
      // 块映射相关状态
      showBlockMapping: false,
      blockMappingCache: new Map(), // 性能优化：缓存块映射数据
      blockMappingData: {
        coverage: 0,
        matchedCount: 0,
        totalOracleCount: 0,
        totalGaussCount: 0,
        matchedOracleBlocks: [],
        matchedGaussBlocks: [],
        unmatchedOracle: [],
        unmatchedGauss: [],
        matchDetails: []
      },
      loadingBlockMapping: false,
    };
  },
  computed: {
    // 简化的状态映射
    ...mapState('diff', [
      'storeTaskId',
      'currentFile',
      'diffMatrix',
      'loadingDetail',
      'loadingMatrix',
      'diffMode',
      'migrating',
    ]),
    ...mapGetters('diff', {
      filteredDiffMatrix: 'filteredDiffMatrix',
    }),
    
    currentIndex() {
      return this.filteredDiffMatrix.findIndex((item) => item.filePath === this.currentFile.filePath);
    },
    hasPrev() {
      return this.currentIndex > 0;
    },
    hasNext() {
      return this.currentIndex >= 0 && this.currentIndex < this.filteredDiffMatrix.length - 1;
    },
    canOperate() {
      return Boolean(this.currentFile.filePath);
    },
    canApply() {
      return Boolean(this.currentFile.migrationDiff);
    },
    // 是否显示提交历史组件
    shouldShowCommitHistory() {
      return Boolean(this.currentFile.filePath);
    },
    // 提交历史统计
    commitHistoryStats() {
      return {
        total: this.oracleCommits.length + this.gaussCommits.length,
        oracle: this.oracleCommits.length,
        gauss: this.gaussCommits.length,
      };
    },
    // 合并的提交历史（按时间排序）
    mergedCommitHistory() {
      const allCommits = [
        ...this.oracleCommits.map(commit => ({ ...commit, type: 'oracle' })),
        ...this.gaussCommits.map(commit => ({ ...commit, type: 'gauss' })),
      ];
      
      return allCommits.sort((a, b) => new Date(b.time) - new Date(a.time));
    },
    
  },
  watch: {
    taskId: {
      immediate: true,
      handler(next) {
        console.log('[FileDiffPage] taskId watch:', { next, storeTaskId: this.storeTaskId });
        if (next && next !== this.storeTaskId) {
          this.setTaskId(next);
          // 添加延迟检查，确保 storeTaskId 更新成功
          this.$nextTick(() => {
            console.log('[FileDiffPage] After setTaskId, storeTaskId:', this.storeTaskId);
            if (!this.storeTaskId) {
              console.error('[FileDiffPage] setTaskId failed, trying again...');
              // 如果仍然没有设置成功，再次尝试
              this.$nextTick(() => {
                this.setTaskId(next);
                console.log('[FileDiffPage] Second attempt, storeTaskId:', this.storeTaskId);
              });
            }
          });
        }
      },
    },
    filePath: {
      immediate: true,
      handler() {
        console.log('[FileDiffPage] filePath watch:', { filePath: this.filePath });
        this.ensureCurrentFile();
      },
    },
    diffMatrix(newValue, oldValue) {
      console.log('[FileDiffPage] diffMatrix watch:', { newValue: newValue?.length, oldValue: oldValue?.length });
      if (!this.initialized && newValue !== oldValue) {
        this.ensureCurrentFile();
      }
    },
    // 监听当前文件变化，重置提交历史状态和块映射状态
    'currentFile.filePath': {
      immediate: true,
      handler(newPath, oldPath) {
        console.log('[🔍 DEBUG] FileDiffPage filePath changed:', { newPath, oldPath });
        if (newPath && newPath !== oldPath) {
          console.log('[🔍 DEBUG] FileDiffPage resetting states for new file');
          this.commitHistoryExpanded = false;
          this.showBlockMapping = false;
          this.loadCommitHistory();
          // 预加载块映射缓存
          this.preloadBlockMappingCache();
        }
      },
    },

    // 监听taskId变化
    'taskId': {
      immediate: true,
      handler(newTaskId, oldTaskId) {
        console.log('[🔍 DEBUG] FileDiffPage taskId changed:', { newTaskId, oldTaskId });
      },
    },

    // 监听diffMatrix变化
    'diffMatrix': {
      immediate: true,
      handler(newMatrix, oldMatrix) {
        console.log('[🔍 DEBUG] FileDiffPage diffMatrix changed:', { 
          newLength: newMatrix?.length || 0, 
          oldLength: oldMatrix?.length || 0 
        });
      },
    },
  },
  created() {
    console.log('[FileDiffPage] created:', {
      taskId: this.taskId,
      storeTaskId: this.storeTaskId,
      route: this.$route?.query
    });
    
    if (this.taskId && this.taskId !== this.storeTaskId) {
      this.setTaskId(this.taskId);
    }
    this.ensureCurrentFile();
  },
  methods: {
    ...mapActions('diff', ['fetchDetail', 'generateMigration', 'applyMigration', 'revertMigration', 'fetchCommitHistory']),
    ...mapMutations('diff', ['setDiffMode', 'setTaskId']),
    
    // 返回总览页面
    goToDashboard() {
      try {
        console.log('[FileDiffPage] 返回总览页面');
        
        // 构建查询参数，保持当前仓库状态
        const query = {};
        if (this.storeTaskId || this.taskId) {
          query.taskId = this.storeTaskId || this.taskId;
        }
        
        // 获取当前仓库ID（从缓存或store中推断）
        const currentRepoId = this.getCurrentRepoId();
        if (currentRepoId) {
          query.repoId = currentRepoId;
        }
        
        this.$router.push({ 
          name: 'Dashboard',
          query: query 
        });
      } catch (error) {
        console.error('[FileDiffPage] 返回总览失败:', error);
        // 备用方案：使用路径导航
        this.$router.push('/dashboard');
      }
    },
    
    // 获取当前仓库ID
    getCurrentRepoId() {
      // 尝试从不同的状态源获取仓库ID
      const sources = [
        this.$store.state.diff.currentRepoId,
        this.$store.state.diff.activeRepoId,
      ];
      
      return sources.find(id => id && typeof id === 'string') || '';
    },
    
    async retryLoad() {
      this.retryCount += 1;
      this.loadError = null;
      await this.ensureCurrentFile(true);
    },
    
    async ensureCurrentFile(force = false) {
      console.log('[FileDiffPage] ensureCurrentFile called:', {
        force,
        filePath: this.filePath,
        taskId: this.taskId,
        storeTaskId: this.storeTaskId,
        route: this.$route?.query,
        currentFilePath: this.currentFile.filePath,
        diffMatrixLength: this.diffMatrix?.length
      });
      
      const routePath = this.normalizeFilePath(this.filePath || (this.$route?.query?.filePath || ''));
      const currentPath = this.normalizeFilePath(this.currentFile.filePath);
      const isBusy = this.loadingDetail || this.loadingMatrix;
      if (!force && isBusy && routePath === currentPath) {
        console.log('[FileDiffPage] Skipping ensureCurrentFile - busy and same path');
        return;
      }
      
      this.loadError = null;
      
      // 临时修复：如果 storeTaskId 为空，尝试使用 props 中的 taskId
      const effectiveTaskId = this.storeTaskId || this.taskId || (this.$route?.query?.taskId || '');
      console.log('[FileDiffPage] Using effectiveTaskId:', effectiveTaskId);
      
      if (!effectiveTaskId) {
        console.warn('[FileDiffPage] No taskId available');
        this.loadError = '缺少扫描任务，请从仪表盘重新进入';
        this.initialized = false;
        return;
      }
      
      if (!Array.isArray(this.diffMatrix) || this.diffMatrix.length === 0) {
        console.warn('[FileDiffPage] No diffMatrix available');
        if (!force) {
          this.$message.info('暂无可用的差异数据，请先在仪表盘执行扫描。');
        }
        this.initialized = false;
        return;
      }
      
      const firstFilteredPath = this.filteredDiffMatrix[0]?.filePath || '';
      const firstMatrixPath = this.diffMatrix[0]?.filePath || '';
      let targetPath = routePath || currentPath || firstFilteredPath || firstMatrixPath;
      
      if (targetPath) {
        const existsInMatrix = this.diffMatrix.some(item => item.filePath === targetPath);
        if (!existsInMatrix) {
          this.$message.warning('路由中的文件不在最新结果中，已自动切换到第一个文件。');
          targetPath = firstFilteredPath || firstMatrixPath;
        } else if (this.filteredDiffMatrix.length > 0) {
          const existsInFiltered = this.filteredDiffMatrix.some(item => item.filePath === targetPath);
          if (!existsInFiltered && firstFilteredPath) {
            this.$message.info('当前文件不在筛选结果中，已切换到筛选列表的第一个文件。');
            targetPath = firstFilteredPath;
          }
        }
      }
      
      if (!targetPath) {
        this.loadError = '无法确定要加载的文件，请返回仪表盘重新选择。';
        this.initialized = false;
        return;
      }
      
      try {
        if (force || targetPath !== this.currentFile.filePath) {
          console.log('[FileDiffPage] Loading file details:', { targetPath, taskId: effectiveTaskId });
          await this.fetchDetail({ filePath: targetPath });
        }
        this.initialized = true;
        this.retryCount = 0;
        this.syncRouteWithFile(targetPath);
      } catch (error) {
        console.error('加载文件详情失败:', error);
        this.loadError = error.message || '加载文件详情失败';
        if (this.retryCount < 3) {
          setTimeout(() => {
            this.retryLoad();
          }, 2000 * (this.retryCount + 1));
        }
      }
    },
    
    handleModeChange(mode) {
      console.log('[FileDiffPage] Mode change requested:', {
        from: this.diffMode,
        to: mode,
        currentFile: this.currentFile.filePath,
        hasOracleDiff: !!this.currentFile.oracleDiff,
        hasGaussDiff: !!this.currentFile.gaussDiff,
        hasMigrationDiff: !!this.currentFile.migrationDiff
      });
      this.setDiffMode(mode);
    },
    handleOptionsChange(options) {
      this.viewerOptions = { ...options };
    },
    buildMigrationOptions() {
      return {
        ignoreWhitespace: this.viewerOptions.ignoreWhitespace,
        collapseUnchanged: this.viewerOptions.collapseUnchanged,
      };
    },
    async handleGenerate() {
      if (!this.currentFile.filePath) {
        this.$message.warning('请选择需要迁移的文件');
        return;
      }
      try {
        const result = await this.generateMigration({
          filePath: this.currentFile.filePath,
          options: this.buildMigrationOptions(),
        });
        this.$message.success(result?.message || '迁移结果已生成');
      } catch (error) {
        this.$message.error(error.message || '生成失败');
      }
    },
    async handleApply() {
      if (!this.currentFile.filePath) {
        this.$message.warning('请选择需要应用的文件');
        return;
      }
      try {
        const result = await this.applyMigration({ filePath: this.currentFile.filePath });
        this.$message.success(result?.message || '迁移结果已应用');
      } catch (error) {
        this.$message.error(error.message || '应用失败');
      }
    },
    async handleRevert() {
      if (!this.currentFile.filePath) {
        this.$message.warning('请选择需要撤销的文件');
        return;
      }
      try {
        const result = await this.revertMigration({ filePath: this.currentFile.filePath });
        this.$message.success(result?.message || '迁移结果已撤销');
      } catch (error) {
        this.$message.error(error.message || '撤销失败');
      }
    },
    async navigatePrev() {
      if (!this.hasPrev) {
        return;
      }
      const prev = this.filteredDiffMatrix[this.currentIndex - 1];
      if (!prev) {
        return;
      }
      await this.fetchDetail({ filePath: prev.filePath });
      this.updateRoute(prev);
    },
    async navigateNext() {
      if (!this.hasNext) {
        return;
      }
      const next = this.filteredDiffMatrix[this.currentIndex + 1];
      if (!next) {
        return;
      }
      await this.fetchDetail({ filePath: next.filePath });
      this.updateRoute(next);
    },
    updateRoute(target) {
      this.syncRouteWithFile(target);
    },
    
    formatPercent(value) {
      if (value === undefined || value === null || value === '') {
        return '--';
      }
      const numeric = Number(value);
      if (Number.isNaN(numeric)) {
        return '--';
      }
      return `${(numeric * 100).toFixed(1)}%`;
    },
    normalizeFilePath(value) {
      return typeof value === 'string' ? value.trim() : '';
    },
    buildRouteQueryPayload(target) {
      if (!target) {
        return null;
      }
      const detail = typeof target === 'string'
        ? this.diffMatrix.find(item => item.filePath === target) || { filePath: target }
        : target;
      if (!detail.filePath) {
        return null;
      }
      const effectiveTaskId = this.storeTaskId || this.taskId || (this.$route?.query?.taskId || '');
      const query = {
        taskId: effectiveTaskId,
        filePath: detail.filePath,
        oracleDelta: detail.oracleDelta || this.oracleDelta || '',
        gaussDelta: detail.gaussDelta || this.gaussDelta || '',
      };
      console.log('[FileDiffPage] Built route query payload:', query);
      return query;
    },
    syncRouteWithFile(target) {
      const query = this.buildRouteQueryPayload(target);
      if (!query) {
        return;
      }
      const currentQuery = this.$route?.query || {};
      const keys = ['taskId', 'filePath', 'oracleDelta', 'gaussDelta'];
      const hasDiff = keys.some(key => (currentQuery[key] || '') !== (query[key] || ''));
      if (!hasDiff) {
        console.log('[FileDiffPage] No route sync needed - query already matches');
        return;
      }
      console.log('[FileDiffPage] Syncing route with file:', query);
      this.$router.replace({
        name: 'FileDiff',
        query,
      });
    },
    
    // 提交历史相关方法
    toggleCommitHistory() {
      this.commitHistoryExpanded = !this.commitHistoryExpanded;
      
      // 性能优化：只在首次展开时加载提交历史
      if (this.commitHistoryExpanded && this.oracleCommits.length === 0 && this.gaussCommits.length === 0) {
        this.loadCommitHistory();
      }
    },
    
    async loadCommitHistory() {
      if (!this.currentFile.filePath) {
        return;
      }
      
      // 性能优化：检查缓存
      const cacheKey = `${this.storeTaskId || this.taskId}:${this.currentFile.filePath}`;
      if (this.commitHistoryCache.has(cacheKey)) {
        const cached = this.commitHistoryCache.get(cacheKey);
        this.oracleCommits = cached.oracle || [];
        this.gaussCommits = cached.gauss || [];
        return;
      }
      
      this.loadingCommitHistory = true;
      
      try {
        // 模拟API调用 - 实际应该调用后端API
        await this.fetchCommitHistoryFromAPI();
        
        // 缓存结果
        this.commitHistoryCache.set(cacheKey, {
          oracle: this.oracleCommits,
          gauss: this.gaussCommits,
        });
        
      } catch (error) {
        console.warn('加载提交历史失败:', error);
        this.$message.warning('加载提交历史失败，请稍后重试');
        // 失败时设置空的提交历史
        this.oracleCommits = [];
        this.gaussCommits = [];
      } finally {
        this.loadingCommitHistory = false;
      }
    },
    
    async fetchCommitHistoryFromAPI() {
      try {
        // 调用真实的API获取提交历史
        const effectiveTaskId = this.storeTaskId || this.taskId;
        const response = await this.fetchCommitHistory({
          taskId: effectiveTaskId,
          filePath: this.currentFile.filePath
        });
        
        // 使用API返回的数据
        this.oracleCommits = response.oracleCommits || [];
        this.gaussCommits = response.gaussCommits || [];
        
      } catch (error) {
        console.warn('获取提交历史失败:', error);
        this.oracleCommits = [];
        this.gaussCommits = [];
        throw error;
      }
    },
    
    openCommitInBrowser(commit) {
      if (commit.url) {
        window.open(commit.url, '_blank');
      }
    },
    
    formatRelativeTime(timestamp) {
      if (!timestamp) return '';
      
      const now = new Date();
      const time = new Date(timestamp);
      const diff = now - time;
      
      const minute = 60 * 1000;
      const hour = 60 * minute;
      const day = 24 * hour;
      const month = 30 * day;
      const year = 365 * day;
      
      if (diff < minute) {
        return '刚刚';
      } else if (diff < hour) {
        return `${Math.floor(diff / minute)}分钟前`;
      } else if (diff < day) {
        return `${Math.floor(diff / hour)}小时前`;
      } else if (diff < month) {
        return `${Math.floor(diff / day)}天前`;
      } else if (diff < year) {
        return `${Math.floor(diff / month)}个月前`;
      } else {
        return `${Math.floor(diff / year)}年前`;
      }
    },
    
    // 块映射相关方法
    toggleBlockMapping() {
      this.showBlockMapping = !this.showBlockMapping;
      
      // 如果展开块映射，加载数据
      if (this.showBlockMapping) {
        // 检查缓存
        const cacheKey = `${this.storeTaskId || this.taskId}:${this.currentFile.filePath}`;
        if (this.blockMappingCache.has(cacheKey)) {
          // 使用缓存数据
          this.blockMappingData = this.blockMappingCache.get(cacheKey);
        } else {
          // 没有缓存，重新加载
          this.loadBlockMapping();
        }
      }
    },
    
    // 预加载块映射缓存
    preloadBlockMappingCache() {
      const cacheKey = `${this.storeTaskId || this.taskId}:${this.currentFile.filePath}`;
      if (this.blockMappingCache.has(cacheKey)) {
        // 如果缓存存在，直接使用缓存数据
        this.blockMappingData = this.blockMappingCache.get(cacheKey);
      }
      // 如果缓存不存在，则在用户展开时再加载
    },
    
    async loadBlockMapping() {
      if (!this.currentFile.filePath) {
        return;
      }
      
      this.loadingBlockMapping = true;
      
      try {
        // 调用真实的后端API获取块映射数据
        const effectiveTaskId = this.storeTaskId || this.taskId;
        const response = await this.$http.post('/api/scan/block-mapping', {
          taskId: effectiveTaskId,
          filePath: this.currentFile.filePath
        });
        
        let blockMappingData;
        if (response && response.data) {
          blockMappingData = response.data || this.getDefaultBlockMapping();
        } else {
          console.warn('块映射API返回异常:', response);
          blockMappingData = this.getDefaultBlockMapping();
        }
        
        // 更新数据
        this.blockMappingData = blockMappingData;
        
        // 缓存结果
        const cacheKey = `${this.storeTaskId || this.taskId}:${this.currentFile.filePath}`;
        this.blockMappingCache.set(cacheKey, blockMappingData);
        
      } catch (error) {
        console.warn('加载块映射失败:', error);
        this.$message.warning('加载块映射失败，请稍后重试');
        this.blockMappingData = this.getDefaultBlockMapping();
      } finally {
        this.loadingBlockMapping = false;
      }
    },
    
    getDefaultBlockMapping() {
      return {
        coverage: 0,
        matchedCount: 0,
        totalOracleCount: 0,
        totalGaussCount: 0,
        matchedOracleBlocks: [],
        matchedGaussBlocks: [],
        unmatchedOracle: [],
        unmatchedGauss: [],
        matchDetails: []
      };
    },
  },
};
</script>

<style scoped>
.file-diff-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 加载状态样式 */
.initial-loading {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}

.loading-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
}

.loading-icon {
  font-size: 48px;
  color: #409eff;
  margin-bottom: 20px;
  animation: rotating 2s linear infinite;
}

@keyframes rotating {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.loading-text {
  font-size: 16px;
  color: #303133;
  margin-bottom: 8px;
}

.loading-tips {
  font-size: 13px;
  color: #909399;
  line-height: 1.4;
}

/* 错误状态样式 */
.error-state {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}

.error-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
}

.error-icon {
  font-size: 48px;
  color: #f56c6c;
  margin-bottom: 20px;
}

.error-message {
  font-size: 16px;
  color: #f56c6c;
  margin-bottom: 20px;
  line-height: 1.4;
}

.file-diff-page__info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.file-diff-page__path {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 8px;
}

.file-diff-page__meta {
  display: flex;
  gap: 16px;
  color: #909399;
  font-size: 13px;
}

.file-diff-page__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}


/* 提交历史样式 */
.commit-history-container {
  margin: 12px 0;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: hidden;
}

.commit-history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.3s;
}

.commit-history-header:hover {
  background: #ecf5ff;
}

.commit-history-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #303133;
}

.commit-history-title i {
  color: #409eff;
}

.commit-count {
  background: #409eff;
  color: white;
  padding: 2px 6px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: normal;
}

.expand-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
}

.commit-history-content {
  background: white;
}

.commit-timeline {
  position: relative;
  padding: 16px;
  max-height: 400px;
  overflow-y: auto;
}

.commit-timeline::before {
  content: '';
  position: absolute;
  left: 16px;
  top: 0;
  bottom: 0;
  width: 2px;
  background: #e4e7ed;
}

.timeline-item {
  position: relative;
  margin-bottom: 16px;
  padding-left: 32px;
}

.timeline-item:last-child {
  margin-bottom: 0;
}

.timeline-dot {
  position: absolute;
  left: 10px;
  top: 8px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: white;
  border: 2px solid #409eff;
  z-index: 1;
}

.timeline-dot.oracle {
  border-color: #e6a23c;
  background: #fdf6ec;
}

.timeline-dot.gauss {
  border-color: #67c23a;
  background: #f0f9eb;
}

.commit-item {
  background: white;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.3s;
}

.commit-item:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
  transform: translateY(-1px);
}

.commit-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.commit-type {
  font-weight: 600;
  font-size: 12px;
  min-width: 35px;
}

.commit-type.oracle {
  color: #e6a23c;
}

.commit-type.gauss {
  color: #67c23a;
}

.commit-hash {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  font-weight: 600;
  color: #409eff;
  text-decoration: none;
}

.commit-hash:hover {
  text-decoration: underline;
}

.commit-time {
  font-size: 11px;
  color: #909399;
  margin-left: auto;
}

.commit-author {
  margin-bottom: 6px;
}

.author-name {
  font-size: 13px;
  color: #303133;
  font-weight: 500;
}

.commit-message {
  font-size: 13px;
  color: #606266;
  line-height: 1.4;
  word-break: break-word;
  margin-bottom: 8px;
}

/* Review链接样式 */
.commit-message :deep(.review-link) {
  color: #409eff;
  text-decoration: none;
  font-weight: 500;
  border-bottom: 1px dotted #409eff;
  transition: all 0.3s;
  padding: 0 2px;
  border-radius: 2px;
}

.commit-message :deep(.review-link):hover {
  color: #66b1ff;
  border-bottom-style: solid;
  background-color: rgba(64, 158, 255, 0.05);
  text-decoration: none;
}

.commit-stats {
  display: flex;
  gap: 12px;
  font-size: 11px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 2px;
}

.stat-added {
  color: #67c23a;
}

.stat-removed {
  color: #f56c6c;
}

.empty-state {
  text-align: center;
  padding: 40px 20px;
  color: #909399;
  font-size: 13px;
}

.empty-state i {
  font-size: 32px;
  margin-bottom: 12px;
  opacity: 0.5;
  display: block;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .file-diff-page__meta {
    flex-wrap: wrap;
    gap: 8px;
  }
  
  .file-diff-page__actions {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  
  .commit-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }
  
  .commit-time {
    margin-left: 0;
    align-self: flex-start;
  }
  
  .commit-stats {
    flex-direction: column;
    gap: 4px;
  }
  
  .timeline-item {
    padding-left: 24px;
  }
  
  .timeline-dot {
    left: 8px;
    width: 10px;
    height: 10px;
  }
  
  .commit-timeline::before {
    left: 12px;
  }
}
</style>
