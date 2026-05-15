package com.toolgateway.persistence;

import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * tool_registry 表 MyBatis Mapper。
 * 只操作 ADMIN 来源的工具 —— CODE/YAML 来源由各自的加载器管理。
 */
@Mapper
public interface ToolRegistryMapper {

    @Select("SELECT * FROM tool_registry")
    @Results(id = "toolResult", value = {
            @Result(property = "name", column = "name"),
            @Result(property = "version", column = "version"),
            @Result(property = "description", column = "description"),
            @Result(property = "source", column = "source"),
            @Result(property = "runnerType", column = "runner_type"),
            @Result(property = "target", column = "target"),
            @Result(property = "timeoutMs", column = "timeout_ms"),
            @Result(property = "tags", column = "tags"),
            @Result(property = "extra", column = "extra"),
            @Result(property = "enabled", column = "enabled"),
            @Result(property = "releaseChannel", column = "release_channel"),
            @Result(property = "canaryWeight", column = "canary_weight"),
            @Result(property = "routeRule", column = "route_rule"),
            @Result(property = "minStableWeight", column = "min_stable_weight"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    List<ToolRegistryEntity> findAll();

    @Select("SELECT * FROM tool_registry WHERE name = #{name}")
    @ResultMap("toolResult")
    ToolRegistryEntity findByName(@Param("name") String name);

    @Select("SELECT * FROM tool_registry WHERE source = #{source}")
    @ResultMap("toolResult")
    List<ToolRegistryEntity> findBySource(@Param("source") String source);

    @Insert("INSERT INTO tool_registry (name, version, description, source, runner_type, target, " +
            "timeout_ms, tags, extra, enabled, release_channel, canary_weight, route_rule, " +
            "min_stable_weight, created_at, updated_at) " +
            "VALUES (#{name}, #{version}, #{description}, #{source}, #{runnerType}, #{target}, " +
            "#{timeoutMs}, #{tags}, #{extra}, #{enabled}, #{releaseChannel}, #{canaryWeight}, " +
            "#{routeRule}, #{minStableWeight}, #{createdAt}, #{updatedAt})")
    int insert(ToolRegistryEntity entity);

    @Update("UPDATE tool_registry SET version = #{version}, description = #{description}, " +
            "target = #{target}, timeout_ms = #{timeoutMs}, tags = #{tags}, extra = #{extra}, " +
            "enabled = #{enabled}, updated_at = #{updatedAt} WHERE name = #{name}")
    int update(ToolRegistryEntity entity);

    @Delete("DELETE FROM tool_registry WHERE name = #{name}")
    int deleteByName(@Param("name") String name);

    @Update("UPDATE tool_registry SET enabled = #{enabled}, updated_at = NOW() WHERE name = #{name}")
    int updateEnabled(@Param("name") String name, @Param("enabled") boolean enabled);
}
