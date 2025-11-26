# Git 内容获取流程对比图

## 原始流程 (提交 9f107ed5 之前)

### 完整序列图

```mermaid
sequenceDiagram
    participant Client as 客户端
    participant ScanApp as ScanAppService
    participant DiffApp as DiffAppService
    participant GitHelper as GitRepositoryHelper
    participant Snapshot as SnapshotLocator
    participant JGit as JGit API

    Client->>ScanApp: scan(input)
    ScanApp->>DiffApp: generateDiff(oracle)
    ScanApp->>DiffApp: generateDiff(gauss)
    
    DiffApp->>Snapshot: locate(repo, startTime, endTime)
    Snapshot->>GitHelper: openRepository(repoConfig)
    GitHelper->>JGit: new FileRepositoryBuilder()
    JGit-->>GitHelper: Repository instance
    GitHelper-->>Snapshot: Repository
    
    loop 处理每个引用 (串行)
        Snapshot->>Snapshot: 检查 ref 是否有效
        alt ref 有效
            Snapshot->>JGit: try (RevWalk revWalk = new RevWalk(repository))
            Snapshot->>JGit: revWalk.sort(RevSort.COMMIT_TIME_DESC)
            Snapshot->>JGit: revWalk.markStart(parseCommit(ref))
            
            loop 遍历提交
                JGit-->>Snapshot: RevCommit
                Snapshot->>Snapshot: 检查时间范围
                alt 在时间范围内
                    Snapshot->>Snapshot: 更新最早/最新候选
                end
            end
        else ref 无效
            Snapshot->>Snapshot: 跳过此引用
        end
    end
    
    GitHelper->>JGit: repository.close()
    Snapshot-->>DiffApp: SnapshotPair
    DiffApp-->>ScanApp: DiffSummary
    ScanApp-->>Client: ScanReport
```

### 流程架构图

```mermaid
graph TD
    A[ScanAppService.scan] --> B[DiffAppService.generateDiff]
    B --> C[SnapshotLocator.locate]
    C --> D[GitRepositoryHelper.openRepository]
    D --> E[创建新 Repository 实例]
    E --> F[收集所有引用]
    F --> G{遍历引用}
    G --> H[创建新 RevWalk]
    H --> I[解析提交]
    I --> J[检查时间范围]
    J --> K[更新候选]
    K --> L[关闭 RevWalk]
    L --> M{还有引用?}
    M -->|是| G
    M -->|否| N[关闭 Repository]
    N --> O[返回 SnapshotPair]
    O --> P[生成 DiffSummary]
    P --> Q[返回 ScanReport]
    
    style E fill:#e1f5fe
    style H fill:#e1f5fe
    style L fill:#fce4ec
    style N fill:#fce4ec
```

## 并行处理流程 (提交 9f107ed5 之后)

### 完整序列图

```mermaid
sequenceDiagram
    participant Client as 客户端
    participant ScanApp as ScanAppService
    participant DiffApp as DiffAppService
    participant GitHelper as GitRepositoryHelper
    participant RepoPool as RepositoryPool
    participant RevPool as RevWalkPool
    participant Snapshot as SnapshotLocator
    participant Executor as 线程池
    participant JGit as JGit API

    Client->>ScanApp: scan(input)
    
    alt 并行扫描启用
        ScanApp->>ScanApp: 判断 enableParallelScan
        ScanApp->>ScanApp: forkJoinPool.submit(parallel tasks)
        par Oracle 仓库处理
            ScanApp->>DiffApp: generateDiff(oracle)
        and Gauss 仓库处理
            ScanApp->>DiffApp: generateDiff(gauss)
        end
        Note over ScanApp: 并行执行两个仓库的差异生成
    else 顺序扫描
        ScanApp->>DiffApp: generateDiff(oracle)
        ScanApp->>DiffApp: generateDiff(gauss)
    end
    
    DiffApp->>Snapshot: locate(repo, startTime, endTime)
    Snapshot->>GitHelper: openRepository(repoConfig)
    GitHelper->>RepoPool: borrowRepository(repoPath)
    
    alt Repository池命中
        RepoPool-->>GitHelper: cached Repository
        Note over RepoPool: 引用计数+1
    else Repository池未命中
        RepoPool->>JGit: new FileRepositoryBuilder()
        JGit-->>RepoPool: new Repository
        RepoPool->>RepoPool: 缓存 Repository
        RepoPool-->>GitHelper: Repository
        Note over RepoPool: 引用计数=1
    end
    
    GitHelper-->>Snapshot: Repository
    
    alt 多引用 && 并行处理启用
        Snapshot->>Snapshot: 判断 enableParallelProcessing
        Snapshot->>Snapshot: 分批处理引用 (maxConcurrentRefs)
        
        loop 并行处理每个批次
            Snapshot->>Executor: CompletableFuture.runAsync()
            par 线程1处理引用
                Executor->>RevPool: borrowRevWalk(repository)
                alt RevWalk池命中
                    RevPool-->>Executor: cached RevWalk
                else RevWalk池未命中
                    RevPool->>JGit: new RevWalk(repository)
                    RevPool-->>Executor: RevWalk
                end
                
                Executor->>JGit: revWalk.sort(RevSort.COMMIT_TIME_DESC)
                Executor->>JGit: revWalk.markStart(parseCommit(ref))
                
                loop 遍历提交
                    JGit-->>Executor: RevCommit
                    Executor->>Executor: 检查时间范围
                    alt 在时间范围内
                        Executor->>Executor: 原子更新候选
                    end
                end
                
                alt 异常处理
                    alt MissingObjectException
                        Executor->>Executor: handleMissingObjectException()
                        Note over Executor: 记录警告，继续处理
                    else 其他异常
                        Executor->>Executor: 设置 exceptionHolder
                    end
                end
                
                Executor->>RevPool: returnRevWalk(revWalk)
            and 线程2处理引用
                Executor->>RevPool: borrowRevWalk(repository)
                Note over Executor: 类似处理流程
                Executor->>RevPool: returnRevWalk(revWalk)
            end
        end
        
        Snapshot->>Snapshot: CompletableFuture.allOf().join()
        Note over Snapshot: 等待所有并行任务完成
        
        alt 有异常
            Snapshot->>Snapshot: 抛出 RuntimeException
        else 无异常
            Snapshot->>Snapshot: 汇总候选结果
        end
    else 单引用或顺序处理
        Snapshot->>RevPool: borrowRevWalk(repository)
        Snapshot->>JGit: 串行处理所有引用
        Snapshot->>RevPool: returnRevWalk(revWalk)
    end
    
    GitHelper->>RepoPool: returnRepository(repoPath, repository)
    RepoPool->>RepoPool: 引用计数-1
    
    Snapshot-->>DiffApp: SnapshotPair
    DiffApp-->>ScanApp: DiffSummary
    ScanApp-->>Client: ScanReport
    
    Note over RepoPool,RevPool: 后台定期清理空闲对象
```

### 并行处理架构图

```mermaid
graph TD
    A[ScanAppService.scan] --> B{enableParallelScan?}
    B -->|是| C[并行扫描]
    B -->|否| D[顺序扫描]
    
    C --> E[CompletableFuture.supplyAsync Oracle]
    C --> F[CompletableFuture.supplyAsync Gauss]
    E --> G[DiffAppService.generateDiff]
    F --> H[DiffAppService.generateDiff]
    
    D --> I[DiffAppService.generateDiff Oracle]
    D --> J[DiffAppService.generateDiff Gauss]
    
    G --> K[SnapshotLocator.locate]
    H --> K
    I --> K
    J --> K
    
    K --> L[GitRepositoryHelper.openRepository]
    L --> M[RepositoryPool.borrowRepository]
    
    M --> N{Repository 池命中?}
    N -->|是| O[返回缓存的 Repository]
    N -->|否| P[创建新 Repository]
    P --> Q[缓存到池中]
    Q --> R[返回新 Repository]
    
    O --> S[SnapshotLocator 处理引用]
    R --> S
    
    S --> T{多引用 && enableParallelProcessing?}
    T -->|是| U[并行处理引用]
    T -->|否| V[串行处理引用]
    
    U --> W[分批处理]
    W --> X[线程池执行]
    X --> Y[RevWalkPool.borrowRevWalk]
    
    Y --> Z{RevWalk 池命中?}
    Z -->|是| AA[返回缓存的 RevWalk]
    Z -->|否| BB[创建新 RevWalk]
    BB --> CC[缓存到池中]
    CC --> DD[返回新 RevWalk]
    
    AA --> EE[并行处理引用]
    DD --> EE
    
    EE --> FF[异常处理]
    FF --> GG[RevWalkPool.returnRevWalk]
    GG --> HH[汇总结果]
    
    V --> II[RevWalkPool.borrowRevWalk]
    II --> JJ[串行处理所有引用]
    JJ --> KK[RevWalkPool.returnRevWalk]
    
    HH --> LL[RepositoryPool.returnRepository]
    KK --> LL
    LL --> MM[返回 SnapshotPair]
    
    style M fill:#e8f5e8
    style Y fill:#e8f5e8
    style X fill:#fff3e0
    style EE fill:#fff3e0
    style FF fill:#ffebee
```

## 关键差异对比

### 1. 资源管理对比

```mermaid
graph LR
    subgraph "原始流程"
        A1[创建 Repository] --> B1[使用 Repository]
        B1 --> C1[关闭 Repository]
        
        D1[创建 RevWalk] --> E1[使用 RevWalk]
        E1 --> F1[关闭 RevWalk]
    end
    
    subgraph "并行流程"
        A2[借用 Repository] --> B2[使用 Repository]
        B2 --> C2[归还 Repository]
        
        D2[借用 RevWalk] --> E2[使用 RevWalk]
        E2 --> F2[归还 RevWalk]
        
        G2[Repository Pool] --> A2
        H2[RevWalk Pool] --> D2
        C2 --> G2
        F2 --> H2
    end
    
    style G2 fill:#e1f5fe
    style H2 fill:#e1f5fe
```

### 2. 并发处理对比

```mermaid
graph TD
    subgraph "原始流程 - 串行"
        A1[处理引用1] --> A2[处理引用2]
        A2 --> A3[处理引用3]
        A3 --> A4[...]
    end
    
    subgraph "并行流程 - 并发"
        B1[处理引用1] --> B5[汇总结果]
        B2[处理引用2] --> B5
        B3[处理引用3] --> B5
        B4[处理引用4] --> B5
        
        B1 -.-> B2
        B2 -.-> B3
        B3 -.-> B4
    end
    
    style B1 fill:#e8f5e8
    style B2 fill:#e8f5e8
    style B3 fill:#e8f5e8
    style B4 fill:#e8f5e8
    style B5 fill:#fff3e0
```

## 问题点可视化

### RevWalk 线程安全问题

```mermaid
graph TD
    A[RevWalk 实例] --> B[线程1 使用]
    A --> C[线程2 使用]
    A --> D[线程3 使用]
    
    B --> E[修改内部状态]
    C --> F[修改内部状态]
    D --> G[修改内部状态]
    
    E --> H[状态冲突]
    F --> H
    G --> H
    
    H --> I[数据损坏]
    H --> J[异常抛出]
    
    style A fill:#ffebee
    style H fill:#ffcdd2
    style I fill:#f44336
    style J fill:#f44336
```

### 资源池竞争

```mermaid
sequenceDiagram
    participant T1 as 线程1
    participant T2 as 线程2
    participant Pool as RevWalkPool
    participant RevWalk as RevWalk实例

    T1->>Pool: borrowRevWalk()
    T2->>Pool: borrowRevWalk()
    Pool-->>T1: RevWalk实例A
    Pool-->>T2: RevWalk实例A (问题!)
    
    T1->>RevWalk: sort(COMMIT_TIME_DESC)
    T2->>RevWalk: reset()
    
    Note over T1,RevWalk: 线程1的操作被线程2干扰
    
    T1->>RevWalk: markStart(commit)
    T1->>RevWalk: next()
    RevWalk-->>T1: 错误结果或异常
    
    T1->>Pool: returnRevWalk(RevWalk)
    T2->>Pool: returnRevWalk(RevWalk)
```

## 配置影响流程图

```mermaid
graph TD
    A[应用启动] --> B[读取配置]
    B --> C{enable-repository-pool?}
    C -->|true| D[初始化 RepositoryPool]
    C -->|false| E[使用传统方式]
    
    B --> F{enable-revwalk-pool?}
    F -->|true| G[初始化 RevWalkPool]
    F -->|false| H[每次创建新 RevWalk]
    
    B --> I{parallel-scan?}
    I -->|true| J[创建并行线程池]
    I -->|false| K[使用串行处理]
    
    B --> L{parallel-ref-processing?}
    L -->|true| M[启用引用并行处理]
    L -->|false| N[串行处理引用]
    
    D --> O[缓存配置生效]
    G --> O
    J --> O
    M --> O
    
    E --> P[传统配置生效]
    H --> P
    K --> P
    N --> P
```

这个可视化分析清楚地展示了提交 `9f107ed5` 引入的并行处理机制的复杂性，以及为什么会导致 fetch 代码报错。核心问题在于 JGit 对象的线程安全特性和不恰当的资源共享机制。
