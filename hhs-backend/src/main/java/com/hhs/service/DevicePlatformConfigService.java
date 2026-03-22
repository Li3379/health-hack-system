package com.hhs.service;

import com.hhs.entity.DevicePlatformConfig;
import com.hhs.vo.DevicePlatformConfigRequest;
import com.hhs.vo.DevicePlatformConfigStatusVO;

import java.util.List;

/**
 * 设备平台配置服务接口
 */
public interface DevicePlatformConfigService {

    /**
     * 获取所有平台的配置状态
     *
     * @return 平台配置状态列表
     */
    List<DevicePlatformConfigStatusVO> getAllPlatformStatus();

    /**
     * 获取指定平台的配置状态
     *
     * @param platform 平台标识
     * @return 平台配置状态
     */
    DevicePlatformConfigStatusVO getPlatformStatus(String platform);

    /**
     * 检查加密密钥是否已配置
     *
     * @return true 已配置
     */
    boolean isEncryptionKeyConfigured();

    /**
     * 获取平台配置（包含敏感信息，仅供内部使用）
     *
     * @param platform 平台标识
     * @return 平台配置实体
     */
    DevicePlatformConfig getPlatformConfig(String platform);

    /**
     * 保存或更新平台配置（管理员操作）
     *
     * @param request 配置请求
     * @return 保存后的配置
     */
    DevicePlatformConfig saveConfig(DevicePlatformConfigRequest request);

    /**
     * 测试平台配置
     *
     * @param platform 平台标识
     * @return 测试结果
     */
    DevicePlatformConfigStatusVO testConfig(String platform);

    /**
     * 删除平台配置
     *
     * @param platform 平台标识
     */
    void deleteConfig(String platform);

    /**
     * 初始化默认平台配置
     */
    void initializeDefaultConfigs();
}