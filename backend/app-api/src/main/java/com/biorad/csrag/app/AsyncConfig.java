package com.biorad.csrag.app;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /**
     * KB 인덱싱용 Virtual Thread Executor.
     * t4g.small (2 vCPU, 2GB RAM) 환경에서 동시 OpenAI API 호출 제한.
     * concurrencyLimit=4: 임베딩/인리치먼트 동시 처리 상한.
     */
    @Bean(name = "kbIndexingExecutor")
    public Executor kbIndexingExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("kb-indexing-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(4);
        return executor;
    }

    /**
     * 문의 문서 인덱싱용 Virtual Thread Executor.
     * concurrencyLimit=4: KB 인덱싱과 독립적으로 동시 4개 처리.
     */
    @Bean(name = "docIndexingExecutor")
    public Executor docIndexingExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("doc-indexing-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(4);
        return executor;
    }
}
