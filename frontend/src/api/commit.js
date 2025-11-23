import request from './request'

/**
 * Git提交历史API
 */
export const commitApi = {
  /**
   * 获取文件提交历史
   * @param {Object} params 参数对象
   * @param {string} params.taskId 任务ID
   * @param {string} params.filePath 文件路径
   * @returns {Promise} 提交历史数据
   */
  getFileCommitHistory(params) {
    return request({
      url: '/api/scan/commit-history',
      method: 'post',
      data: params
    })
  },

  /**
   * 格式化提交时间
   * @param {string} isoTime ISO时间字符串
   * @returns {string} 格式化后的时间
   */
  formatCommitTime(isoTime) {
    if (!isoTime) return ''
    try {
      const date = new Date(isoTime)
      return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
      })
    } catch (error) {
      console.warn('时间格式化失败:', isoTime, error)
      return isoTime
    }
  },

  /**
   * 格式化作者信息
   * @param {string} authorName 作者姓名
   * @param {string} authorEmail 作者邮箱
   * @returns {string} 格式化后的作者信息
   */
  formatAuthor(authorName, authorEmail) {
    if (!authorName && !authorEmail) return '未知作者'
    if (authorName && authorEmail) {
      return `${authorName} <${authorEmail}>`
    }
    return authorName || authorEmail
  },

  /**
   * 格式化提交信息
   * @param {string} message 提交信息
   * @param {number} maxLength 最大长度
   * @returns {string} 格式化后的提交信息
   */
  formatCommitMessage(message, maxLength = 80) {
    if (!message) return '无提交信息'
    
    // 移除多余的空白字符
    const cleanMessage = message.trim()
    
    // 如果消息太长，截断并添加省略号
    if (cleanMessage.length > maxLength) {
      return cleanMessage.substring(0, maxLength) + '...'
    }
    
    return cleanMessage
  },

  /**
   * 格式化文件变更统计
   * @param {Object} stats 统计信息
   * @returns {string} 格式化后的统计信息
   */
  formatChangeStats(stats) {
    if (!stats) return '0'
    
    const added = stats.added || 0
    const removed = stats.removed || 0
    const modified = stats.modified || 0
    
    if (added === 0 && removed === 0 && modified === 0) {
      return '0'
    }
    
    const parts = []
    if (added > 0) parts.push(`+${added}`)
    if (removed > 0) parts.push(`-${removed}`)
    if (modified > 0) parts.push(`~${modified}`)
    
    return parts.join('/')
  },

  /**
   * 获取仓库类型显示名称
   * @param {string} repoType 仓库类型
   * @returns {string} 显示名称
   */
  getRepoTypeDisplayName(repoType) {
    const typeMap = {
      'oracle': 'Oracle',
      'gauss': 'Gauss',
      'ORACLE': 'Oracle',
      'GAUSS': 'Gauss'
    }
    return typeMap[repoType] || repoType || '未知'
  },

  /**
   * 获取仓库类型样式类
   * @param {string} repoType 仓库类型
   * @returns {string} CSS类名
   */
  getRepoTypeClass(repoType) {
    const type = (repoType || '').toLowerCase()
    return `repo-type-${type}`
  },

  /**
   * 生成提交URL
   * @param {string} url 提交URL
   * @param {string} commitHash 提交哈希
   * @returns {string} 完整的提交URL
   */
  generateCommitUrl(url, commitHash) {
    if (!url && !commitHash) return ''
    
    if (url && url.includes('{commitHash}')) {
      return url.replace('{commitHash}', commitHash)
    }
    
    if (url) return url
    
    // 如果没有URL但有哈希，返回哈希
    return commitHash || ''
  },

  /**
   * 解析提交历史数据
   * @param {Object} data API返回的数据
   * @returns {Object} 解析后的提交历史数据
   */
  parseCommitHistory(data) {
    if (!data) {
      return {
        filePath: '',
        oracleCommits: [],
        gaussCommits: [],
        totalCount: 0,
        oracleCount: 0,
        gaussCount: 0,
        hasOracleCommits: false,
        hasGaussCommits: false
      }
    }

    const result = {
      filePath: data.filePath || '',
      oracleCommits: (data.oracleCommits || []).map(commit => ({
        ...commit,
        repoType: 'oracle',
        formattedTime: this.formatCommitTime(commit.commitTime),
        formattedAuthor: this.formatAuthor(commit.authorName, commit.authorEmail),
        formattedMessage: this.formatCommitMessage(commit.message),
        formattedStats: this.formatChangeStats(commit.stats),
        repoTypeClass: this.getRepoTypeClass('oracle'),
        repoTypeDisplayName: this.getRepoTypeDisplayName('oracle'),
        commitUrl: this.generateCommitUrl(commit.url, commit.shortHash)
      })),
      gaussCommits: (data.gaussCommits || []).map(commit => ({
        ...commit,
        repoType: 'gauss',
        formattedTime: this.formatCommitTime(commit.commitTime),
        formattedAuthor: this.formatAuthor(commit.authorName, commit.authorEmail),
        formattedMessage: this.formatCommitMessage(commit.message),
        formattedStats: this.formatChangeStats(commit.stats),
        repoTypeClass: this.getRepoTypeClass('gauss'),
        repoTypeDisplayName: this.getRepoTypeDisplayName('gauss'),
        commitUrl: this.generateCommitUrl(commit.url, commit.shortHash)
      })),
      totalCount: data.totalCount || 0,
      oracleCount: data.oracleCount || 0,
      gaussCount: data.gaussCount || 0,
      hasOracleCommits: !!data.hasOracleCommits,
      hasGaussCommits: !!data.hasGaussCommits
    }

    return result
  },

  /**
   * 合并Oracle和Gauss提交历史
   * @param {Array} oracleCommits Oracle提交历史
   * @param {Array} gaussCommits Gauss提交历史
   * @returns {Array} 合并后的提交历史（按时间排序）
   */
  mergeCommitHistories(oracleCommits, gaussCommits) {
    const allCommits = [
      ...(oracleCommits || []).map(commit => ({ ...commit, repoType: 'oracle' })),
      ...(gaussCommits || []).map(commit => ({ ...commit, repoType: 'gauss' }))
    ]

    // 按提交时间倒序排序
    return allCommits.sort((a, b) => {
      const timeA = new Date(a.commitTime).getTime()
      const timeB = new Date(b.commitTime).getTime()
      return timeB - timeA
    })
  },

  /**
   * 过滤提交历史
   * @param {Array} commits 提交历史数组
   * @param {Object} filters 过滤条件
   * @returns {Array} 过滤后的提交历史
   */
  filterCommits(commits, filters = {}) {
    if (!commits || commits.length === 0) return []

    return commits.filter(commit => {
      // 按仓库类型过滤
      if (filters.repoType && commit.repoType !== filters.repoType) {
        return false
      }

      // 按作者过滤
      if (filters.author && !commit.authorName?.toLowerCase().includes(filters.author.toLowerCase())) {
        return false
      }

      // 按提交信息过滤
      if (filters.message && !commit.message?.toLowerCase().includes(filters.message.toLowerCase())) {
        return false
      }

      // 按时间范围过滤
      if (filters.startDate) {
        const commitTime = new Date(commit.commitTime).getTime()
        const startTime = new Date(filters.startDate).getTime()
        if (commitTime < startTime) return false
      }

      if (filters.endDate) {
        const commitTime = new Date(commit.commitTime).getTime()
        const endTime = new Date(filters.endDate).getTime()
        if (commitTime > endTime) return false
      }

      return true
    })
  }
}

export default commitApi
