package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.dto.HealthProfileRequest;
import com.hhs.entity.HealthProfile;
import com.hhs.exception.BusinessException;
import com.hhs.mapper.HealthProfileMapper;
import com.hhs.service.HealthProfileService;
import com.hhs.service.domain.ProfileCompletenessCalculator;
import com.hhs.vo.HealthProfileVO;
import com.hhs.vo.ProfileCompletenessVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class HealthProfileServiceImpl implements HealthProfileService {

    private final HealthProfileMapper healthProfileMapper;
    private final ProfileCompletenessCalculator completenessCalculator;

    /**
     * Create a new health profile for a user
     *
     * @param userId User ID
     * @param request Health profile request containing gender, birth date, height, weight, etc.
     * @return HealthProfileVO containing the created profile
     * @throws BusinessException if user already has a health profile
     */
    @Override
    @Transactional(timeout = 30)
    public HealthProfileVO create(Long userId, HealthProfileRequest request) {
        LambdaQueryWrapper<HealthProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HealthProfile::getUserId, userId);
        if (healthProfileMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(400, "该用户已存在健康档案，请使用更新接口");
        }
        HealthProfile entity = toEntity(null, userId, request);
        healthProfileMapper.insert(entity);
        return toVO(entity);
    }

    /**
     * Get health profile by user ID
     *
     * @param userId User ID
     * @return HealthProfileVO if exists, null otherwise
     */
    @Override
    @Transactional(timeout = 30, readOnly = true)
    public HealthProfileVO getByUserId(Long userId) {
        LambdaQueryWrapper<HealthProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HealthProfile::getUserId, userId);
        HealthProfile profile = healthProfileMapper.selectOne(wrapper);
        return profile != null ? toVO(profile) : null;
    }

    /**
     * Get the authoritative completeness of a user's health profile.
     *
     * <p>Uses the same single-row wrapper as {@link #getByUserId(Long)} so the profile header
     * and the completeness score can never disagree about which row is current. Uniqueness is
     * enforced upstream by {@link #create(Long, HealthProfileRequest)}, which rejects a second
     * row for the same user, so the user-scoped wrapper matches at most one row and needs no
     * ordering. The absent row is delegated to the calculator, which owns the 0% answer.</p>
     *
     * @param userId User ID
     * @return the completeness payload; never {@code null}
     */
    @Override
    @Transactional(timeout = 30, readOnly = true)
    public ProfileCompletenessVO getCompleteness(Long userId) {
        LambdaQueryWrapper<HealthProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HealthProfile::getUserId, userId);
        HealthProfile profile = healthProfileMapper.selectOne(wrapper);
        return completenessCalculator.calculate(profile);
    }

    /**
     * Update an existing health profile
     *
     * @param id Profile ID to update
     * @param userId User ID (for authorization)
     * @param request Health profile request containing updated fields
     * @return HealthProfileVO containing the updated profile
     * @throws BusinessException if profile not found or user not authorized
     */
    @Override
    @Transactional(timeout = 30)
    public HealthProfileVO update(Long id, Long userId, HealthProfileRequest request) {
        LambdaQueryWrapper<HealthProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HealthProfile::getUserId, userId);
        HealthProfile existing = healthProfileMapper.selectOne(wrapper);
        if (existing == null || !existing.getId().equals(id)) {
            throw new BusinessException(404, "健康档案不存在");
        }
        HealthProfile entity = toEntity(id, userId, request);
        healthProfileMapper.updateById(entity);
        return toVO(entity);
    }

    private HealthProfile toEntity(Long id, Long userId, HealthProfileRequest request) {
        HealthProfile e = new HealthProfile();
        if (id != null) e.setId(id);
        e.setUserId(userId);
        e.setGender(request.getGender());
        e.setBirthDate(request.getBirthDate());
        e.setHeightCm(request.getHeightCm());
        e.setWeightKg(request.getWeightKg());
        e.setBloodType(request.getBloodType());
        e.setAllergyHistory(request.getAllergyHistory());
        e.setFamilyHistory(request.getFamilyHistory());
        e.setLifestyleHabits(request.getLifestyleHabits());
        if (request.getHeightCm() != null && request.getHeightCm().compareTo(BigDecimal.ZERO) > 0
                && request.getWeightKg() != null && request.getWeightKg().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal heightM = request.getHeightCm().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            e.setBmi(request.getWeightKg().divide(heightM.multiply(heightM), 2, RoundingMode.HALF_UP));
        }
        return e;
    }

    private static HealthProfileVO toVO(HealthProfile e) {
        return new HealthProfileVO(
                e.getId(), e.getUserId(), e.getGender(), e.getBirthDate(),
                e.getHeightCm(), e.getWeightKg(), e.getBmi(), e.getBloodType(),
                e.getAllergyHistory(), e.getFamilyHistory(), e.getLifestyleHabits(),
                e.getCreateTime(), e.getUpdateTime()
        );
    }
}
