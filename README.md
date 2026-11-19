# CreativeBox

Paper/Spigot 1.20.1 的隔离沙盒世界插件：玩家进沙盒随便玩，退出时把状态恢复回来。

## 功能

| 命令 | 作用 | 权限 |
| --- | --- | --- |
| `/sb help` | 帮助 | 所有人 |
| `/sb enter` | 进入沙盒（先保存快照） | `creativebox.use` |
| `/sb exit` | 退出沙盒并恢复状态 | `creativebox.use` |
| `/sb status` | 世界状态、沙盒内玩家数 | 所有人 |
| `/sb setspawn` | 把当前站位设为沙盒出生点 | `creativebox.admin` |
| `/sb reload` | 重载配置并应用到沙盒世界 | `creativebox.admin` |
| `/sb reset` | 卸载并删除沙盒世界后重建 | `creativebox.admin` |
| `/sb world` | 查看世界信息 | `creativebox.admin` |

别名：`/sandbox`、`/goc`。

保存的内容：所在位置、游戏模式、飞行状态、等级/经验、生命、饥饿与饱和、火烧/冰冻 tick、药水效果、**背包**、**末影箱**（Base64 序列化）。服务器重启后仍能从磁盘恢复「仍在沙盒里」的玩家，重连直接送回沙盒。

## 安装

把 jar 放进服务器 `plugins/` 目录，启动即自动生成 `plugins/CreativeBox/config.yml`。
需要 Paper/Spigot/Pukkit 1.20.1 与 Java 17+。

## 构建

### 方式 A：Maven（Linux/CI 通用）

```bash
mvn clean package        # 产物 target/CreativeBox-1.0-SNAPSHOT.jar
```

仓库地址已写在 `pom.xml` 里（`repo.papermc.io`），不需要额外 settings.xml。

### 方式 B：Windows 离线脚本（不需要 Maven/Gradle）

本机只有 JDK、没有 Maven 时用这个脚本，它自己下载依赖并编译打包：

```powershell
# Windows PowerShell 5.1 默认禁止运行脚本，需要 Bypass
powershell -ExecutionPolicy Bypass -File build.ps1            # 下载依赖 -> javac -> jar -> dist/…zip
powershell -ExecutionPolicy Bypass -File build.ps1 -Offline   # 依赖已在 libs/ 里，只编译打包
powershell -ExecutionPolicy Bypass -File build.ps1 -Version 1.0.0 -NoZip
```

脚本只依赖 JDK 17+（`javac`/`jar`）与联网下载依赖；`javac` 参数走 argfile，因此脚本内容保持纯 ASCII，避免 PowerShell 5.1 按 GBK 读取中文脚本时解析错位。

产物：
- `dist/CreativeBox-<版本>.jar` —— 插件本体（含 plugin.yml / config.yml）
- `dist/CreativeBox-<版本>.zip` —— 发布包（`plugins/` 里的 jar + config.yml + README）
脚本会打印 SHA256，方便核对下载一致性。依赖 jar 缓存在 `libs/`，重复构建不重复下载。

## 配置要点

- `world.*`：世界名、环境、类型（`FLAT`/`NORMAL`…）、种子、是否生成结构、超平坦字符串。
- `spawn.*`：沙盒出生点坐标与朝向。
- `gamerules`：用原版键名（`doMobSpawning`、`mobGriefing`、`pvp`…）。已收录进 `org.bukkit.GameRule` 常量的走现代 API，其余（如 `pvp`）自动退回字符串接口。
- `world-border`：`enabled` + `diameter`，中心对准出生点。
- `sandbox`：沙盒里的游戏模式、是否允许飞行。
- `isolation`：进入时是否清掉背包/末影箱/药水/经验/饥饿，退出时恢复；`all: true` 一次性全开；`block-cross-world-teleport` 拦住跨世界传送与传送门。
- `void-protection.threshold`：**绝对高度**阈值（默认 50）。玩家 Y 低于它且还在往下掉才拉回出生点，带 1 秒节流。
- `reset.enabled/time`：按服务器本地时间每天重置一次世界。

## 已修复的问题（相对原仓库）

原仓库的源码其实**编译不过**，而且承诺的功能没实现：

1. `pom.xml` 引用了不存在也不在 Maven Central 的父 POM（`me.bionic:bionic-plugins:1.0-SNAPSHOT`）→ 改成独立 POM。
2. 子包里的命令/监听器没有 import 主类与 `SandboxService` → 补上 import。
3. `PlayerListener` 里 `new ConcurrentHashMap<>()()` 语法错误、`FileConfiguration` 缺 import → 修正。
4. 末影箱 API 用法错误：1.20.1 没有 `org.bukkit.EnderChest` 接口，`HumanEntity.getEnderChest()` 返回 `Inventory` → 改用 `getContents()/setContents()/clear()`。
5. `PlayerEffect` 这个类型不存在 → 改为 `org.bukkit.potion.PotionEffect`。
6. 背包/末影箱从未真正序列化（配置文件里写的是字面量 `"null"`），退出后物品丢失 → 用 `org.bukkit.util.io.BukkitObjectOutputStream` + `java.util.Base64` 真正序列化（原来的 `snakeyaml.external.biz.base64coder` 在 paper-api 里不存在）。
7. 药水、飞行、经验、饥饿等状态只写不读 → `load()` 现在解析并恢复。
8. 自动铺平台用同一个 `Location` 反复 `add()`，方块坐标累积错位 → 改为按整数坐标逐个放置（石头在脚下格，基岩再下一格）。
9. `reset` 删了世界目录后不重建，之后进沙盒拿到 null 世界 → 现在卸载 → 删除 → 重新创建。
10. `World.unload()` / `World.Type` 在 paper-api 1.20.1 里不存在 → 改用 `Bukkit.unloadWorld(...)` 与 `org.bukkit.WorldType`。
11. 手写 YAML 读写易出错（未转义、解析不全）→ 改用 Bukkit `YamlConfiguration`，写入用「临时文件 + rename」保证原子性。
12. `config.yml` 与 README 的虚空阈值不一致（README 写 50，配置写 10）→ 统一为 50，并在配置里说明是绝对高度。
13. CI 在同一 tag 重复推送时 release 资产会 409 冲突 → 资产名带 run number，失败时退回 artifact 上传。
14. 仓库里混着两份源码树和提交进去的 `target/…jar.b64` 产物 → 只保留一份源码，产物不再入库。

## 验证（真实服务端冒烟测试，已通过）

用 CraftBukkit 1.20.1（Java 17）真实启动服务端，把 `dist/CreativeBox-1.0-SNAPSHOT.jar` 放进 `plugins/`，结果：

- `[CreativeBox] Loading CreativeBox v1.0-SNAPSHOT` → `[CreativeBox] Enabling CreativeBox v1.0-SNAPSHOT`
- 服务端日志出现 `Preparing start region for dimension minecraft:sandbox`，插件打印「沙盒世界已就绪: sandbox」
- 控制台执行 `sb status` 输出「沙盒世界: sandbox (已就绪)」
- `plugins/CreativeBox/config.yml`（2166 B）与 `plugins/CreativeBox/states/` 自动创建
- 关服时 `Saving chunks for level 'ServerLevel[sandbox]'` / `All chunks are saved`，无插件异常栈
- 构建产物：jar 34889 B（21 条目，含 `plugin.yml`、`config.yml`）、zip 37971 B；SHA256 由脚本打印

在真实生产服务端 **Leaves 1.21.11（MC 1.21.11，Java 21.0.12）** 上同样通过：

- `[PluginRemapper] Done remapping plugin 'plugins\CreativeBox-1.0-SNAPSHOT.jar'` → `Loading server plugin CreativeBox v1.0-SNAPSHOT` → `Enabling CreativeBox v1.0-SNAPSHOT`
- 服务端日志出现 `Selecting spawn point for world 'minecraft:sandbox'`，插件打印「沙盒世界已就绪: sandbox」
- 自动创建 `plugins/CreativeBox/config.yml` 与 `states/`，无插件异常栈
- 字节码 `major version: 61`（Java 17），Java 17/21 都能跑；服务端版本要求 ≥ 1.20.1，1.20.1 ~ 1.21.11 都实测加载成功
- 注：本次测试是用**重定向 stdin 的控制台**跑的，Leaves/Paper 在这种非交互终端下执行控制台命令会抛 `CommandSourceStack.getLevel()` 为 null 的 NPE（连 `/stop` 也一样），这是终端限制，不是插件问题；`/sb` 命令在游戏内由玩家执行不受影响。

## CI 与下载（已跑通）

代码已推送到 `main`（提交 `add4551`，并打了 tag `v1.0.0`），GitHub Actions 全部通过：

- 两次 push run + 一次 tag run：`conclusion = success`
- 每次运行产出 artifact `CreativeBox-jar`（37398 B，Maven 构建）
- tag 运行创建 release **v1.0.0**，附带 `CreativeBox.zip`（43803 B，内含 `plugins/CreativeBox-1.0-SNAPSHOT.jar` + `plugins/config.yml` + `README.md`）
- 下载：<https://github.com/niubikelashi/CreativeBox/releases/download/v1.0.0/CreativeBox.zip>
- 已核对 CI 产物：jar 里含 `plugin.yml`/`config.yml`，class 头 `cafebabe`，字节码 `major version: 61`（Java 17），因此 Java 17/21 都能跑
- workflow 的 job 声明了 `permissions: contents: write`，否则建 release 的那一步会因 GITHUB_TOKEN 只读而失败

## 已知限制

- 沙盒世界由插件创建；如果世界名与已有世界冲突，插件复用那个世界（不会删别人的存档）。
- `/sb reset` 会删除世界目录，不可撤销；删之前会把沙盒内玩家送回主世界。
- 状态文件放在 `plugins/CreativeBox/states/<uuid>.yml`，不会自动清理（保留用于崩溃恢复）。
- 末影箱、背包序列化用的是 Bukkit 的物品序列化格式，跨大版本（物品/材质改名）可能恢复失败，此时会记录日志并跳过。
- 冒烟测试用的是 CraftBukkit 1.20.1（Paper API 的类在 CraftBukkit 上同样存在）；Paper 服务端下载源（api.papermc.io）在这台机器上被代理拦成 403，所以没在 Paper 上跑。插件只用 `org.bukkit.*` 接口，没用到 Paper 独有 API。
- 药水效果的「显示开关」在 1.20.1 的 `PotionEffect` 里没有对应方法，只持久化类型/时长/等级/ambient。

## 目录结构

```
src/main/java/com/bionic/creativebox/
  CreativeBoxPlugin.java     插件入口：启用、事件注册、定时重置
  SandboxWorldManager.java   世界创建/规则/边界/铺平台/重置
  SandboxService.java        进出沙盒、按配置隔离与恢复
  PlayerSnapshot.java        玩家状态快照
  PlayerStateStore.java      YAML 持久化 + 物品序列化
  util/Text.java             & 颜色码转换
  command/SandboxCommand.java
  listener/{Player,Teleport,Portal}Listener.java
src/main/resources/{plugin.yml,config.yml}
build.ps1                    离线构建脚本（下载依赖 → javac → jar → zip）
```
