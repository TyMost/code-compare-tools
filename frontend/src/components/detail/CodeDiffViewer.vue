<template>
  <div class="diff-viewer" ref="container"></div>
</template>

<script>
import * as monaco from 'monaco-editor';

export default {
  name: 'CodeDiffViewer',
  props: {
    original: {
      type: String,
      default: '',
    },
    modified: {
      type: String,
      default: '',
    },
    language: {
      type: String,
      default: 'javascript',
    },
    readOnly: {
      type: Boolean,
      default: true,
    },
  },
  data() {
    return {
      editor: null,
      originalModel: null,
      modifiedModel: null,
    };
  },
  mounted() {
    this.createEditor();
  },
  beforeDestroy() {
    if (this.editor) {
      this.editor.dispose();
    }
    if (this.originalModel) {
      this.originalModel.dispose();
    }
    if (this.modifiedModel) {
      this.modifiedModel.dispose();
    }
  },
  watch: {
    original(val) {
      if (this.originalModel && this.originalModel.getValue() !== val) {
        this.originalModel.setValue(val || '');
      }
    },
    modified(val) {
      if (this.modifiedModel && this.modifiedModel.getValue() !== val) {
        this.modifiedModel.setValue(val || '');
      }
    },
  },
  methods: {
    createEditor() {
      this.originalModel = monaco.editor.createModel(
        this.original || '',
        this.language
      );
      this.modifiedModel = monaco.editor.createModel(
        this.modified || '',
        this.language
      );
      this.editor = monaco.editor.createDiffEditor(this.$refs.container, {
        readOnly: this.readOnly,
        minimap: {
          enabled: false,
        },
        automaticLayout: true,
        renderSideBySide: true,
        renderMarginRevertIcon: false,
      });
      this.editor.setModel({
        original: this.originalModel,
        modified: this.modifiedModel,
      });
    },
  },
};
</script>

<style scoped>
.diff-viewer {
  height: 560px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  overflow: hidden;
}
</style>
