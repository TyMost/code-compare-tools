package com.example.migratediff.infrastructure.git;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 命名线程工厂，用于创建具有可识别名称的线程
 * 支持JDK 8兼容性实现
 */
public class NamedThreadFactory implements ThreadFactory {

    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;
    private final ThreadGroup group;

    public NamedThreadFactory(String namePrefix) {
        SecurityManager s = System.getSecurityManager();
        this.group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
        this.namePrefix = namePrefix != null ? namePrefix : "pool";
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(group, r, namePrefix + "-" + threadNumber.getAndIncrement(), 0);
        if (t.isDaemon()) {
            t.setDaemon(false);
        }
        if (t.getPriority() != Thread.NORM_PRIORITY) {
            t.setPriority(Thread.NORM_PRIORITY);
        }
        return t;
    }
}
