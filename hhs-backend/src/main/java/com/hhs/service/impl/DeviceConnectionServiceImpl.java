package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.entity.DeviceConnection;
import com.hhs.mapper.DeviceConnectionMapper;
import com.hhs.service.DeviceConnectionService;
import com.hhs.vo.DeviceConnectionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备连接服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceConnectionServiceImpl implements DeviceConnectionService {

    private final DeviceConnectionMapper deviceConnectionMapper;

    @Override
    public List<DeviceConnectionVO> getConnections(Long userId) {
        LambdaQueryWrapper<DeviceConnection> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DeviceConnection::getUserId, userId);
        queryWrapper.orderByDesc(DeviceConnection::getCreateTime);

        List<DeviceConnection> connections = deviceConnectionMapper.selectList(queryWrapper);
        List<DeviceConnectionVO> result = new ArrayList<>();

        // 添加所有支持的平台，未连接的显示为 disconnected
        String[] platforms = {"huawei", "xiaomi", "wechat", "apple"};
        for (String platform : platforms) {
            DeviceConnectionVO vo = new DeviceConnectionVO();
            vo.setPlatform(platform);
            vo.setPlatformName(getPlatformName(platform));

            DeviceConnection conn = connections.stream()
                    .filter(c -> c.getPlatform().equals(platform))
                    .findFirst()
                    .orElse(null);

            if (conn != null) {
                vo.setStatus(conn.getStatus());
                vo.setStatusName(getStatusName(conn.getStatus()));
                vo.setLastSyncAt(conn.getLastSyncAt());
                vo.setSyncEnabled(conn.getSyncEnabled());
                vo.setPlatformUserId(conn.getPlatformUserId());
            } else {
                vo.setStatus("disconnected");
                vo.setStatusName("未连接");
                vo.setSyncEnabled(false);
            }

            result.add(vo);
        }

        return result;
    }

    @Override
    public DeviceConnection getConnection(Long userId, String platform) {
        LambdaQueryWrapper<DeviceConnection> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DeviceConnection::getUserId, userId);
        queryWrapper.eq(DeviceConnection::getPlatform, platform);
        return deviceConnectionMapper.selectOne(queryWrapper);
    }

    @Override
    @Transactional
    public DeviceConnection saveConnection(DeviceConnection connection) {
        DeviceConnection existing = getConnection(connection.getUserId(), connection.getPlatform());

        if (existing != null) {
            connection.setId(existing.getId());
            deviceConnectionMapper.updateById(connection);
        } else {
            connection.setStatus("connected");
            connection.setSyncEnabled(true);
            connection.setCreateTime(LocalDateTime.now());
            deviceConnectionMapper.insert(connection);
        }

        return connection;
    }

    @Override
    @Transactional
    public boolean disconnect(Long userId, String platform) {
        DeviceConnection connection = getConnection(userId, platform);
        if (connection == null) {
            return true; // 已经断开
        }

        connection.setStatus("disconnected");
        connection.setAccessToken(null);
        connection.setRefreshToken(null);
        connection.setTokenExpireAt(null);

        return deviceConnectionMapper.updateById(connection) > 0;
    }

    @Override
    @Transactional
    public void updateLastSyncAt(Long userId, String platform) {
        DeviceConnection connection = getConnection(userId, platform);
        if (connection != null) {
            connection.setLastSyncAt(LocalDateTime.now());
            deviceConnectionMapper.updateById(connection);
        }
    }

    @Override
    public boolean isConnected(Long userId, String platform) {
        DeviceConnection connection = getConnection(userId, platform);
        if (connection == null) {
            return false;
        }

        if (!"connected".equals(connection.getStatus())) {
            return false;
        }

        // 检查 token 是否过期
        if (connection.getTokenExpireAt() != null
                && connection.getTokenExpireAt().isBefore(LocalDateTime.now())) {
            return false;
        }

        return true;
    }

    private String getPlatformName(String platform) {
        return switch (platform) {
            case "huawei" -> "华为运动健康";
            case "xiaomi" -> "小米运动";
            case "wechat" -> "微信运动";
            case "apple" -> "Apple Health";
            default -> platform;
        };
    }

    private String getStatusName(String status) {
        return switch (status) {
            case "connected" -> "已连接";
            case "expired" -> "已过期";
            case "disconnected" -> "已断开";
            default -> status;
        };
    }
}