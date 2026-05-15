package com.toolgateway.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对 ToolRegistryMapper 的薄封装 —— 负责设置默认值、日志。
 */
@Repository
public class ToolRegistryRepository {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistryRepository.class);

    private final ToolRegistryMapper mapper;

    public ToolRegistryRepository(ToolRegistryMapper mapper) {
        this.mapper = mapper;
    }

    public List<ToolRegistryEntity> findAll() {
        return mapper.findAll();
    }

    public List<ToolRegistryEntity> findBySource(String source) {
        return mapper.findBySource(source);
    }

    public ToolRegistryEntity findByName(String name) {
        return mapper.findByName(name);
    }

    public void insert(ToolRegistryEntity entity) {
        if (entity.getVersion() == null) entity.setVersion("1.0.0");
        if (entity.getTimeoutMs() == null) entity.setTimeoutMs(30_000L);
        if (entity.getEnabled() == null) entity.setEnabled(true);
        if (entity.getReleaseChannel() == null) entity.setReleaseChannel("stable");
        if (entity.getCanaryWeight() == null) entity.setCanaryWeight(0);
        if (entity.getMinStableWeight() == null) entity.setMinStableWeight(100);
        LocalDateTime now = LocalDateTime.now();
        if (entity.getCreatedAt() == null) entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        mapper.insert(entity);
        log.info("DB insert: name={}, source={}", entity.getName(), entity.getSource());
    }

    public void update(ToolRegistryEntity entity) {
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.update(entity);
        log.info("DB update: name={}", entity.getName());
    }

    public void deleteByName(String name) {
        mapper.deleteByName(name);
        log.info("DB delete: name={}", name);
    }

    public void updateEnabled(String name, boolean enabled) {
        mapper.updateEnabled(name, enabled);
        log.info("DB updateEnabled: name={}, enabled={}", name, enabled);
    }
}
