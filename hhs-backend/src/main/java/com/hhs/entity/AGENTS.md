<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-05-29 | Updated: 2026-05-29 -->

# entity/

**Purpose**: MyBatis-Plus 实体类，映射数据库表 (35 张表)。

**For AI Agents**:
- 使用 `@TableName("table_name")` 映射表名
- 主键: `@TableId(type = IdType.AUTO)` 自增
- 字段: `@TableField("column_name")` 映射列名
- 逻辑删除: `@TableLogic` 注解
- 自动填充: `@TableField(fill = FieldFill.INSERT)` 创建时间等
- Lombok: `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- 驼峰自动映射下划线，无需每个字段都加 @TableField
