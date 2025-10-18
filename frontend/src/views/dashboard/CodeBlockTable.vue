<template>
  <div class="code-block-table dense-table">
    <el-table
      ref="tableRef"
      :data="groupedTableData"
      border
      stripe
      :row-key="rowKey"
      :row-class-name="rowClassName"
      :tree-props="{ children: 'children' }"
      :indent="16"
      :expand-row-keys="expandedGroupIds"
      v-loading="loading"
      @expand-change="onExpandChange"
      @row-dblclick="handleRowDblClick"
    >
      <el-table-column label="" width="56">
        <template #header>
          <el-checkbox
            :indeterminate="isIndeterminate"
            :value="isAllSelected"
            @change="handleHeaderToggle"
          />
        </template>
        <template #default="{ row }">
          <el-checkbox
            v-if="row.isGroup"
            :indeterminate="isGroupIndeterminate(row)"
            :value="isGroupFullySelected(row)"
            @change="(checked) => handleGroupToggle(row, checked)"
          />
          <el-checkbox
            v-else
            :value="isRowSelected(row.id)"
            @change="(checked) => handleRowToggle(row, checked)"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="文件路径"
        min-width="320"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <div
            class="file-cell"
            :class="{
              'group-cell': row.isGroup,
              'leaf-cell': !row.isGroup,
              'is-expanded': row.isGroup && isGroupExpanded(row),
            }"
          >
            <span
              v-if="row.isGroup"
              class="toggle-icon"
              :class="{ 'is-expanded': isGroupExpanded(row) }"
              @click.stop="toggleGroup(row)"
            >
              <i
                :class="[
                  isGroupExpanded(row)
                    ? 'el-icon-folder-opened'
                    : 'el-icon-folder',
                ]"
              />
            </span>
            <span v-else class="toggle-icon leaf-icon">
              <i class="el-icon-document" />
            </span>
            <span class="file-path">{{ row.filePath }}</span>
            <span v-if="row.isGroup" class="file-meta">
              共 {{ row.totalBlocks }} 个差异块
            </span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="行号" width="112" align="center">
        <template #default="{ row }">
          <span v-if="!row.isGroup">{{ formatLineRange(row) }}</span>
          <span v-else class="placeholder">--</span>
        </template>
      </el-table-column>
      <el-table-column
        label="代码摘要"
        min-width="280"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <template v-if="row.isGroup">
            <span class="group-summary">
              包含 {{ row.totalBlocks }} 个差异块
            </span>
          </template>
          <template v-else>
            <code class="snippet">{{ row.codeSnippet }}</code>
          </template>
        </template>
      </el-table-column>
      <el-table-column
        label="状态标签"
        min-width="180"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <el-tag
            v-for="item in resolveCategories(row)"
            :key="`${row.id}-${item.value}`"
            effect="dark"
            :style="{
              backgroundColor: item.color,
              borderColor: item.color,
            }"
            class="category-tag"
          >
            {{ item.label }}
          </el-tag>
          <span v-if="!resolveCategories(row).length" class="placeholder"
            >--</span
          >
        </template>
      </el-table-column>
      <el-table-column label="操作" width="128" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="!row.isGroup"
            type="text"
            size="small"
            @click="viewDetail(row.id)"
          >
            查看详情
          </el-button>
          <span v-else class="placeholder">--</span>
        </template>
      </el-table-column>
    </el-table>
    <div class="pagination">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next, jumper"
        :current-page="pagination.page"
        :page-size="pagination.size"
        :page-sizes="[10, 20, 50]"
        :total="pagination.total"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>
  </div>
</template>

<script>
export default {
  name: 'CodeBlockTable',
  props: {
    codeBlocks: {
      type: Array,
      default: () => [],
    },
    categoryOptions: {
      type: Array,
      default: () => [],
    },
    pagination: {
      type: Object,
      default: () => ({
        page: 1,
        size: 20,
        total: 0,
      }),
    },
    loading: {
      type: Boolean,
      default: false,
    },
    selectedIds: {
      type: Array,
      default: () => [],
    },
  },
  data() {
    return {
      expandedGroupIds: [],
    };
  },
  watch: {
    codeBlocks: {
      handler() {
        this.resetExpansion();
      },
      deep: true,
      immediate: true,
    },
  },
  computed: {
    groupedTableData() {
      const blocks = Array.isArray(this.codeBlocks) ? this.codeBlocks : [];
      if (!blocks.length) {
        return [];
      }
      const groups = [];
      const groupMap = new Map();

      blocks.forEach((block) => {
        const filePath = block.filePath || '未命名文件';
        let group = groupMap.get(filePath);
        if (!group) {
          group = {
            id: `group-${groups.length}-${filePath}`,
            isGroup: true,
            filePath,
            totalBlocks: 0,
            categories: new Set(),
            children: [],
          };
          groupMap.set(filePath, group);
          groups.push(group);
        }
        group.totalBlocks += 1;

        const categories = this.normalizeCategoryInput(block);
        categories.forEach((entry) => {
          if (!entry) {
            return;
          }
          if (typeof entry === 'string') {
            group.categories.add(entry);
          } else if (typeof entry === 'object') {
            const key = entry.key || entry.value || entry.label;
            if (key) {
              group.categories.add(key);
            }
          }
        });

        group.children.push({
          ...block,
          id: block.id,
          isGroup: false,
          parentId: group.id,
        });
      });

      return groups.map((group) => ({
        id: group.id,
        isGroup: true,
        filePath: group.filePath,
        totalBlocks: group.totalBlocks,
        categories: Array.from(group.categories),
        children: group.children,
      }));
    },
    leafRows() {
      return this.groupedTableData.reduce((acc, group) => {
        if (Array.isArray(group.children)) {
          group.children.forEach((child) => acc.push(child));
        }
        return acc;
      }, []);
    },
    selectedSet() {
      const source = Array.isArray(this.selectedIds) ? this.selectedIds : [];
      return new Set(source);
    },
    isAllSelected() {
      const leaves = this.leafRows;
      if (!leaves.length) {
        return false;
      }
      return leaves.every((row) => this.selectedSet.has(row.id));
    },
    isIndeterminate() {
      const leaves = this.leafRows;
      if (!leaves.length) {
        return false;
      }
      const selectedCount = leaves.filter((row) =>
        this.selectedSet.has(row.id)
      ).length;
      return selectedCount > 0 && selectedCount < leaves.length;
    },
  },
  methods: {
    rowKey(row) {
      return row.id;
    },
    rowClassName({ row }) {
      return row.isGroup ? 'group-row' : 'block-row';
    },
    resetExpansion() {
      this.expandedGroupIds = [];
    },
    isGroupExpanded(row) {
      if (!row || !row.isGroup) {
        return false;
      }
      return this.expandedGroupIds.includes(row.id);
    },
    formatLineRange(row) {
      if (row.isGroup) {
        return '--';
      }
      const start = row.startLine ?? '-';
      const end = row.endLine ?? '-';
      return `L${start}-${end}`;
    },
    onPageChange(page) {
      this.$emit('page-change', page);
    },
    onSizeChange(size) {
      this.$emit('size-change', size);
    },
    viewDetail(id) {
      this.$emit('view-detail', id);
    },
    isRowSelected(id) {
      return this.selectedSet.has(id);
    },
    getGroupChildren(row) {
      if (!row || !row.isGroup) {
        return [];
      }
      return Array.isArray(row.children)
        ? row.children.filter((item) => item && !item.isGroup)
        : [];
    },
    isGroupFullySelected(row) {
      const children = this.getGroupChildren(row);
      if (!children.length) {
        return false;
      }
      return children.every((child) => this.selectedSet.has(child.id));
    },
    isGroupIndeterminate(row) {
      const children = this.getGroupChildren(row);
      if (!children.length) {
        return false;
      }
      const selectedCount = children.filter((child) =>
        this.selectedSet.has(child.id)
      ).length;
      return selectedCount > 0 && selectedCount < children.length;
    },
    handleGroupToggle(row, checked) {
      const children = this.getGroupChildren(row);
      if (!children.length) {
        return;
      }
      const next = new Set(this.selectedIds || []);
      children.forEach((child) => {
        if (!child?.id) {
          return;
        }
        if (checked) {
          next.add(child.id);
        } else {
          next.delete(child.id);
        }
      });
      this.emitSelection(Array.from(next));
    },
    handleRowToggle(row, checked) {
      if (!row || row.isGroup) {
        return;
      }
      const next = new Set(this.selectedIds || []);
      if (checked) {
        next.add(row.id);
      } else {
        next.delete(row.id);
      }
      this.emitSelection(Array.from(next));
    },
    handleHeaderToggle(checked) {
      const leaves = this.leafRows;
      if (!leaves.length) {
        return;
      }
      const next = new Set(this.selectedIds || []);
      if (checked) {
        leaves.forEach((row) => next.add(row.id));
      } else {
        leaves.forEach((row) => next.delete(row.id));
      }
      this.emitSelection(Array.from(next));
    },
    emitSelection(ids) {
      const list = Array.isArray(ids) ? ids : [];
      this.$emit('selection-change', list);
    },
    onExpandChange(row, expandedRows) {
      if (!row || !row.isGroup) {
        return;
      }
      const expanded = Array.isArray(expandedRows)
        ? expandedRows
            .filter((item) => item && item.isGroup && item.id)
            .map((item) => item.id)
        : this.expandedGroupIds;
      this.expandedGroupIds = Array.from(new Set(expanded));
    },
    toggleGroup(row, expand) {
      if (!row || !row.isGroup) {
        return;
      }
      const target =
        typeof expand === 'boolean' ? expand : !this.isGroupExpanded(row);
      const table = this.$refs.tableRef;
      if (table && typeof table.toggleRowExpansion === 'function') {
        table.toggleRowExpansion(row, target);
      }
      const next = new Set(this.expandedGroupIds);
      if (target) {
        next.add(row.id);
      } else {
        next.delete(row.id);
      }
      this.expandedGroupIds = Array.from(next);
    },
    handleRowDblClick(row) {
      // 双击整行时同步展开/收起，避免只能点击角落的小图标
      this.toggleGroup(row);
    },
    resolveCategories(row) {
      const lookup = (this.categoryOptions || []).reduce((acc, item) => {
        acc[item.value] = item;
        return acc;
      }, {});
      const source = this.normalizeCategoryInput(row);
      const resolved = source
        .map((entry) => {
          if (!entry) {
            return null;
          }
          if (typeof entry === 'string') {
            const matched = lookup[entry];
            if (matched) {
              return matched;
            }
            return {
              value: entry,
              label: entry,
              color: row.categoryColor || '#9ca3af',
            };
          }
          if (typeof entry === 'object') {
            const key = entry.key || entry.value || entry.label;
            const matched = key ? lookup[key] : null;
            return {
              value: key || entry.label,
              label: entry.label || matched?.label || key || '',
              color: entry.color || matched?.color || '#9ca3af',
            };
          }
          return null;
        })
        .filter((item) => item && item.label);
      if (resolved.length > 0) {
        return resolved;
      }
      if (row.categoryLabel) {
        return [
          {
            value: row.categoryKey || row.categoryLabel,
            label: row.categoryLabel,
            color: row.categoryColor || '#9ca3af',
          },
        ];
      }
      if (row.statusLabel) {
        return [
          {
            value: row.status || row.statusLabel,
            label: row.statusLabel,
            color: row.statusColor || '#9ca3af',
          },
        ];
      }
      return [];
    },
    normalizeCategoryInput(row) {
      if (Array.isArray(row.categories)) {
        return row.categories;
      }
      if (Array.isArray(row.categorySummary)) {
        return row.categorySummary;
      }
      if (Array.isArray(row.categoryKeys)) {
        return row.categoryKeys;
      }
      if (Array.isArray(row.categoryLabels)) {
        return row.categoryLabels;
      }
      if (row.categoryKey) {
        return [row.categoryKey];
      }
      if (row.category) {
        return [row.category];
      }
      return [];
    },
  },
};
</script>

<style scoped lang="scss">
.code-block-table {
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(31, 56, 88, 0.08);
  padding: 12px 16px;
}

.dense-table ::v-deep .el-table__cell {
  padding: 8px 12px;
  font-size: 13px;
}

.dense-table ::v-deep .el-table__row {
  height: 44px;
}

.dense-table ::v-deep .group-row > td {
  background: #f9fafb;
  font-weight: 600;
  color: #1f2933;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.dense-table ::v-deep .group-row:hover > td {
  background: #eef2ff;
}

.file-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.file-cell .file-path {
  flex: 1;
  font-weight: 500;
  color: #1f2933;
}

.file-cell.group-cell .file-path {
  font-weight: 600;
}

.file-cell.group-cell.is-expanded .file-path {
  color: #1d4ed8;
}

.file-cell .file-meta {
  font-size: 12px;
  color: #6b7280;
}

.toggle-icon {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #4b5563;
  transition: color 0.2s ease, transform 0.2s ease;
}

.toggle-icon i {
  font-size: 18px;
}

.toggle-icon.is-expanded {
  color: #1d4ed8;
}

.toggle-icon.leaf-icon {
  color: #9ca3af;
  cursor: default;
}

.group-summary {
  font-size: 13px;
  color: #4b5563;
}

.snippet {
  font-family: 'Fira Code', monospace;
  font-size: 13px;
  color: #1f2933;
  white-space: pre-wrap;
}

.category-tag {
  margin-right: 4px;
  margin-bottom: 4px;
}

.category-tag:last-child {
  margin-right: 0;
}

.placeholder {
  color: #9ca3af;
  font-size: 12px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}

.dense-table ::v-deep .el-table__expand-icon {
  display: none;
}
</style>
