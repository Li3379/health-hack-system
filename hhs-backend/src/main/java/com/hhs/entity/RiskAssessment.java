package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 风险评估记录实体
 */
@Data
@TableName("risk_assessment")
public class RiskAssessment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long profileId;

    /**
     * 批次号：同一次评估产生的多条疾病记录共享。
     *
     * <p>历史记录不再删除重写，因此需要批次来区分「哪几条属于同一次评估」，
     * 列表才能按批次折叠并计算「本次 vs 上次」的变化。</p>
     */
    private String batchId;

    private String diseaseName;
    private String riskLevel;
    private Integer riskScore;
    private String suggestion;

    /**
     * 评分构成快照（JSON），创建时冻结，读取时原样返回。
     *
     * <p>存快照而不是读时重算，是为了让历史记录永远是「当时的真实依据」——
     * 重算会让用户补填档案后，旧记录出现与分数对不上的明细。
     * 存量行为 {@code null}：它们创建时从未保存过归因，不伪造。</p>
     */
    private String contributors;

    private LocalDateTime createTime;
}
