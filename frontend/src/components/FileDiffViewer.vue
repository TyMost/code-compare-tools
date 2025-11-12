<template>
  <div class="file-diff-viewer">
    <div class="file-diff-viewer__header">
      <span class="file-diff-viewer__title">{{ headerTitle }}</span>
      <span class="file-diff-viewer__path">{{ filePath }}</span>
    </div>
    <div ref="editor" class="file-diff-viewer__editor"></div>
  </div>
</template>

<script>
import * as monaco from 'monaco-editor/esm/vs/editor/editor.api';
import 'monaco-editor/min/vs/editor/editor.main.css';

const globalScope =
  typeof self !== 'undefined'
    ? self
    : typeof window !== 'undefined'
    ? window
    : undefined;

const workerUrl = (label) => {
  switch (label) {
    case 'json':
      return new URL('../workers/json.worker.js', import.meta.url);
    case 'css':
    case 'less':
    case 'scss':
      return new URL('../workers/css.worker.js', import.meta.url);
    case 'html':
    case 'handlebars':
    case 'razor':
    case 'xml':
      return new URL('../workers/html.worker.js', import.meta.url);
    case 'typescript':
    case 'javascript':
      return new URL('../workers/ts.worker.js', import.meta.url);
    default:
      return new URL('../workers/editor.worker.js', import.meta.url);
  }
};

const createWorker = (label) => new Worker(workerUrl(label), { type: 'module' });

if (globalScope && !globalScope.MonacoEnvironment) {
  globalScope.MonacoEnvironment = {
    getWorker(_, label) {
      return createWorker(label);
    },
  };
}

export default {
  name: 'FileDiffViewer',
  props: {
    filePath: {
      type: String,
      default: '',
    },
    mode: {
      type: String,
      default: 'deltaO',
    },
    oracleDiff: {
      type: Object,
      default: () => ({
        before: '',
        after: '',
      }),
    },
    gaussDiff: {
      type: Object,
      default: () => ({
        before: '',
        after: '',
      }),
    },
    migrationDiff: {
      type: String,
      default: '',
    },
    options: {
      type: Object,
      default: () => ({
        inlineView: false,
        ignoreWhitespace: false,
        collapseUnchanged: false,
      }),
    },
  },
  data() {
    return {
      editor: null,
      models: {
        original: null,
        modified: null,
      },
      layoutFrame: null,
      layoutRetries: 0,
      layoutTimeouts: [],
    };
  },
  computed: {
    diffContent() {
      const oracle = this.oracleDiff || {};
      const gauss = this.gaussDiff || {};
      const migration = this.migrationDiff || '';
      switch (this.mode) {
        case 'deltaG':
          return {
            original: gauss.before || '',
            modified: gauss.after || '',
            title: 'ΔG(g1→g2)',
          };
        case 'deltaCompare':
          return {
            original: oracle.after || '',
            modified: gauss.after || '',
            title: 'ΔO vs ΔG',
          };
        case 'migration':
          return {
            original: gauss.after || '',
            modified: migration || '',
            title: '迁移结果',
          };
        case 'deltaO':
        default:
          return {
            original: oracle.before || '',
            modified: oracle.after || '',
            title: 'ΔO(o1→o2)',
          };
      }
    },
    headerTitle() {
      return this.diffContent.title;
    },
  },
  watch: {
    diffContent: {
      deep: true,
      handler() {
        this.updateModel();
      },
    },
    options: {
      deep: true,
      handler() {
        this.updateOptions();
      },
    },
  },
  mounted() {
    this.initEditor();
    window.addEventListener('resize', this.handleResize);
    if (window.visualViewport) {
      window.visualViewport.addEventListener('resize', this.handleViewportChange);
      window.visualViewport.addEventListener('scroll', this.handleViewportChange);
    }
    window.addEventListener('orientationchange', this.handleViewportChange);
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.handleResize);
    window.removeEventListener('orientationchange', this.handleViewportChange);
    if (window.visualViewport) {
      window.visualViewport.removeEventListener('resize', this.handleViewportChange);
      window.visualViewport.removeEventListener('scroll', this.handleViewportChange);
    }
    this.cancelLayoutFrame();
    this.clearLayoutTimeouts();
    this.disposeEditor();
  },
  methods: {
    initEditor() {
      if (this.editor) {
        this.layoutEditor();
        return;
      }
      this.$nextTick(() => {
        if (this.editor) {
          this.layoutEditor();
          return;
        }
        this.ensureContainerSize();
        this.editor = monaco.editor.createDiffEditor(
          this.$refs.editor,
          this.buildEditorOptions()
        );
        monaco.editor.setTheme('vs');
        this.updateModel();
        this.scheduleLayout(true);
      });
    },
    updateModel() {
      if (!this.editor) {
        return;
      }
      this.disposeModels();
      const language = this.resolveLanguage(this.filePath);
      const originalModel = monaco.editor.createModel(
        this.diffContent.original || '',
        language
      );
      const modifiedModel = monaco.editor.createModel(
        this.diffContent.modified || '',
        language
      );
      this.models = {
        original: originalModel,
        modified: modifiedModel,
      };
      this.editor.setModel({
        original: originalModel,
        modified: modifiedModel,
      });
      this.$nextTick(() => this.scheduleLayout(true));
    },
    updateOptions() {
      if (!this.editor) {
        return;
      }
      this.editor.updateOptions(this.buildEditorOptions());
      this.$nextTick(() => this.scheduleLayout(true));
    },
    disposeModels() {
      if (this.models.original) {
        this.models.original.dispose();
      }
      if (this.models.modified) {
        this.models.modified.dispose();
      }
      this.models = {
        original: null,
        modified: null,
      };
    },
    disposeEditor() {
      this.cancelLayoutFrame();
      this.clearLayoutTimeouts();
      this.disposeModels();
      if (this.editor) {
        this.editor.dispose();
        this.editor = null;
      }
    },
    layoutEditor() {
      const container = this.$refs.editor;
      const size = this.ensureContainerSize();
      if (this.editor) {
        const width =
          (size && size.width) ||
          (container ? container.clientWidth || container.offsetWidth : 0) ||
          0;
        const height =
          (size && size.height) ||
          (container ? container.clientHeight || container.offsetHeight : 0) ||
          0;
        if (width > 0 && height > 0) {
          this.layoutRetries = 0;
          this.editor.layout({ width, height });
        } else if (this.layoutRetries < 5) {
          this.layoutRetries += 1;
          this.scheduleLayout();
        }
      }
    },
    handleResize() {
      this.layoutRetries = 0;
      this.scheduleLayout(true);
      this.schedulePostResizeRefresh();
    },
    ensureContainerSize() {
      const container = this.$refs.editor;
      if (!container) {
        return null;
      }
      const rect = container.getBoundingClientRect();
      const width =
        (rect && rect.width) ||
        container.clientWidth ||
        container.offsetWidth ||
        0;
      let height = container.clientHeight || container.offsetHeight || 0;
      if (
        rect &&
        typeof rect.top === 'number' &&
        !Number.isNaN(rect.top) &&
        typeof window !== 'undefined'
      ) {
        const viewportHeight =
          window.innerHeight || document.documentElement.clientHeight || 0;
        const available = viewportHeight - rect.top - 32;
        const targetHeight = Math.max(available, 320);
        if (Math.abs(height - targetHeight) > 1) {
          height = targetHeight;
          container.style.height = `${targetHeight}px`;
        }
      }
      return { width, height };
    },
    scheduleLayout(resetRetries = false, delay = 0) {
      if (resetRetries) {
        this.layoutRetries = 0;
        this.clearLayoutTimeouts();
      }
      if (
        typeof window === 'undefined' ||
        !window.requestAnimationFrame ||
        !window.cancelAnimationFrame
      ) {
        if (delay > 0) {
          const timeout = setTimeout(() => {
            this.layoutTimeouts = this.layoutTimeouts.filter((id) => id !== timeout);
            this.layoutEditor();
          }, delay);
          this.layoutTimeouts.push(timeout);
        } else {
          this.layoutEditor();
        }
        return;
      }
      this.cancelLayoutFrame();
      if (delay > 0) {
        const timeout = window.setTimeout(() => {
          this.layoutTimeouts = this.layoutTimeouts.filter((id) => id !== timeout);
          this.layoutEditor();
        }, delay);
        this.layoutTimeouts.push(timeout);
      } else {
        this.layoutFrame = window.requestAnimationFrame(() => {
          this.layoutFrame = null;
          this.layoutEditor();
        });
      }
    },
    cancelLayoutFrame() {
      if (
        this.layoutFrame &&
        typeof window !== 'undefined' &&
        window.cancelAnimationFrame
      ) {
        window.cancelAnimationFrame(this.layoutFrame);
        this.layoutFrame = null;
      }
    },
    clearLayoutTimeouts() {
      if (!this.layoutTimeouts.length) {
        return;
      }
      while (this.layoutTimeouts.length) {
        const timeout = this.layoutTimeouts.pop();
        if (timeout) {
          clearTimeout(timeout);
        }
      }
    },
    schedulePostResizeRefresh() {
      this.scheduleLayout(false, 120);
      this.scheduleLayout(false, 320);
      this.scheduleLayout(false, 640);
    },
    handleViewportChange() {
      this.layoutRetries = 0;
      this.scheduleLayout(true);
      this.schedulePostResizeRefresh();
    },
    buildEditorOptions() {
      return {
        automaticLayout: true,
        readOnly: true,
        renderSideBySide: !this.options.inlineView,
        ignoreTrimWhitespace: this.options.ignoreWhitespace,
        renderIndicators: !this.options.collapseUnchanged,
        scrollBeyondLastLine: false,
        wordWrap: 'on',
        minimap: {
          enabled: false,
        },
        originalEditable: false,
      };
    },
    resolveLanguage(path) {
      if (!path) {
        return 'plaintext';
      }
      const ext = path.split('.').pop();
      switch (ext) {
        case 'js':
        case 'jsx':
        case 'ts':
        case 'tsx':
          return 'javascript';
        case 'vue':
          return 'html';
        case 'java':
          return 'java';
        case 'py':
          return 'python';
        case 'sql':
          return 'sql';
        case 'json':
          return 'json';
        case 'xml':
          return 'xml';
        case 'yml':
        case 'yaml':
          return 'yaml';
        default:
          return 'plaintext';
      }
    },
  },
};
</script>

<style scoped>
.file-diff-viewer {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.file-diff-viewer__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.file-diff-viewer__title {
  font-weight: 600;
}

.file-diff-viewer__path {
  font-family: 'Fira Code', 'Courier New', monospace;
  color: #909399;
  font-size: 12px;
}

.file-diff-viewer__editor {
  position: relative;
  flex: 1;
  min-height: 520px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
}
</style>
