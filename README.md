# CreativeBox - Minecraft 创意沙盒插件

Paper/Spigot 1.20+ / Leaves Compatible | Java 17 | Zero Dependencies

CreativeBox 是一个轻量级的创意沙盒世界插件，提供完全隔离的沙盒体验。玩家进入后获得创造模式、飞行能力和完整背包快照，退出时完美恢复所有状态。

## 安装步骤

1. **编译**: mvn clean package -DskipTests  
   JAR: target/CreativeBox-1.0-SNAPSHOT.jar
2. **部署**: 放入服务器 plugins/ 目录
3. **配置**: 编辑 plugins/CreativeBox/config.yml

## 命令列表

| 命令 | 权限 | 描述 |
|------|------|------|
| /sb | creativebox.use | 进入/退出沙盒 |
| /sb reload | creativebox.admin | 重载配置 |
| /sb setspawn | creativebox.admin | 设置出生点(当前位置) |
| /sb reset | creativebox.admin | 重置沙盒世界 |
| /sb <玩家> | creativebox.admin | 将玩家送入沙盒 |

## 工作原理

### Enter 流程 (进入)
1. 构造快照保存全部状态到 YAML data/<uuid>.yml
2. 清空背包和末影箱（内容已存盘）
3. 移除所有药水效果
4. 传送到沙盒出生点
5. CREATIVE + allowFlight(true) + flying(true)
6. 重置健康值/饥饿值为满

### Exit 流程 (退出)
1. 传回原位置(origin)
2. 恢复游戏模式（回到进入前）
3. 从 Base64 恢复背包和末影箱
4. 恢复经验、药水效果、属性全部状态
5. 保存最终状态到 YAML

## 配置关键参数

| 路径 | 默认值 | 说明 |
|------|--------|------|
| world.name | sandbox | 沙盒世界名 |
| spawn.y | 65.0 | 出生点 Y 坐标 |
| gamerules.keep-inventory | true | 死亡不掉物品 |
| void-protection.enabled | true | 虚空保护开关 |
| void-protection.threshold | 50 | Y轴阈值(低于回传) |

## 权限节点

- creativebox.use (default: true) - /sb 进出沙盒
- creativebox.admin (default: op) - 管理命令

## 数据持久化

玩家快照存储在 plugins/CreativeBox/data/<uuid>.yml，使用 YAML + Base64 混合序列化。每个玩家约 5KB。

## 性能特点

Java 17 | Zero third-party deps | JAR < 50KB
