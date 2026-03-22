package com.hhs.service;

import com.hhs.entity.DeviceConnection;
import com.hhs.vo.DeviceConnectionVO;

import java.util.List;

/**
 * 设备连接服务接口
 */
public interface DeviceConnectionService {

    /**
     * 获取用户所有设备连接
     *
     * @param userId 用户ID
     * @return 设备连接列表
     */
    List<DeviceConnectionVO> getConnections(Long userId);

    /**
     * 获取用户指定平台的连接
     *
     * @param userId   用户ID
     * @param platform 平台标识
     * @return 设备连接
     */
    DeviceConnection getConnection(Long userId, String platform);

    /**
     * 保存或更新设备连接
     *
     * @param connection 设备连接信息
     * @return 保存后的连接
     */
    DeviceConnection saveConnection(DeviceConnection connection);

    /**
     * 断开设备连接
     *
     * @param userId   用户ID
     * @param platform 平台标识
     * @return 是否成功
     */
    boolean disconnect(Long userId, String platform);

    /**
     * 更新最后同步时间
     *
     * @param userId   用户ID
     * @param platform 平台标识
     */
    void updateLastSyncAt(Long userId, String platform);

    /**
     * 检查连接是否有效
     *
     * @param userId   用户ID
     * @param platform 平台标识
     * @return 是否有效
     */
    boolean isConnected(Long userId, String platform);
}