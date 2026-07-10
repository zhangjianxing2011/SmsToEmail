# SMS Forwarder (短信转发邮箱) Android 应用 

这是一个专门为 Android 11 及以上版本 (API 30+) 设计的轻量级短信转发工具。它能够在后台实时监控手机收到的短信，并通过 SMTP 协议自动将其转发到指定的电子邮箱中。  
[apk下载链接](https://github.com/zhangjianxing2011/SmsToEmail/releases)

---

## 核心功能

1. **邮箱多通道转发**：支持自定义配置 SMTP 服务器、发信端口、发信邮箱及应用授权码，并可通过多邮箱群发机制将短信同时分发至多个邮箱。
2. **新！多邮箱动态增删**：UI 界面提供“+ Add”和“删除”按钮，支持动态增减收件人邮箱，无需再手动书写逗号，且能逐个校验邮箱格式是否正确。
3. **新！Telegram 机器人支持**：新增 Telegram 机器人转发通道（与邮件发信完全解耦，可独立或并存运行），短信到达时即刻通过 Telegram 机器人推送到你的个人聊天、群组或频道。
4. **新！折叠式模块面板**：发信设置与 Telegram 机器人设置卡片全新支持一键展开/收起，极大释放手机屏幕垂直空间，视觉更加清爽。
5. **新！自适应沉浸式状态栏**：状态栏底色可完美融入应用背景，且状态栏上的电量、时间、Wi-Fi 等图标颜色可根据系统浅色/深色（Day/Night）模式自适应调整，杜绝低端斑驳感。
6. **新！一键忽略电池优化**：主界面卡片直观显示“忽略电池优化”黄色提示按钮，可一键将应用加入系统省电白名单，彻底阻断系统后台杀进程，从源头上杜绝重启电量开销。
7. **防抖与去重过滤**：内置 thread-safe 去重机制，过滤 10 秒内重复触发的系统短信广播，防止出现多封重复转发邮件。
8. **后台常驻内存与开机自启**：基于 Android 前台服务（Foreground Service）并配合持久化通知栏运行。同时监听设备启动广播，开机后无需人工干预即可在后台默默提供转发服务。
9. **测试消息一键发送**：支持分别发送测试邮件和测试 Telegram 消息，方便在正式投产前验证连接和密钥是否配置无误。
10. **实时运行日志**：在界面展示最近 50 条短信的收发、去重、发信与失败错误日志，支持一键清理。

---

## 权限说明 (尽最大努力精简)

应用仅申请了实现短信转发和后台保活核心功能所必需的权限，无任何多余权限：

| 权限名称 | 权限作用 | 备注说明 |
| :--- | :--- | :--- |
| `android.permission.RECEIVE_SMS` | 实时接收新到短信的通知 | **运行时权限**，必须手动授予。 |
| `android.permission.READ_SMS` | 读取新收到短信的发件人及内容 | **运行时权限**，必须手动授予。 |
| `android.permission.INTERNET` | 通过 SMTP / Telegram API 发送消息 | 网络访问权限，系统默认授予。 |
| `android.permission.ACCESS_NETWORK_STATE` | 检查当前网络连接状态 | 用于网络状态检测。 |
| `android.permission.RECEIVE_BOOT_COMPLETED` | 监听设备开机广播 | 用于实现开机自动重新启动前台服务。 |
| `android.permission.FOREGROUND_SERVICE` | 运行前台服务 | 维持后台长连接，保证实时性（Android 9.0+）。 |
| `android.permission.FOREGROUND_SERVICE_DATA_SYNC` | 前台数据同步服务类型 | Android 14+ 必须声明的前台服务细分类型权限。 |
| `android.permission.POST_NOTIFICATIONS` | 显示前台服务通知栏 | **运行时权限**，Android 13+ 必须授予，否则前台服务无法启动。 |
| `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 申请加入电池优化白名单 | 允许应用跳转并加入无后台省电限制白名单，实现稳定常驻。 |

---

## SMTP 常用配置指南

现代邮箱由于安全限制，**不能**直接使用登录密码作为发件密码。你必须到邮箱网页端后台开启 **SMTP 服务** 并生成 **授权码 / 应用密码**（App Password）。

### 1. Gmail 配置
- **SMTP 服务器**：`smtp.gmail.com`
- **SMTP 端口**：`465` (勾选 SSL/TLS) 或 `587` (不勾选 SSL/TLS，走 STARTTLS)
- **发件人邮箱**：你的 Gmail 邮箱地址
- **发件密码**：登录 Google 账户 -> 安全 -> 开启“两步验证” -> 搜索“应用专用密码” -> 生成一个 16 位密码填入本应用。

### 2. QQ 邮箱配置
- **SMTP 服务器**：`smtp.qq.com`
- **SMTP 端口**：`465` (必须勾选 SSL/TLS)
- **发件人邮箱**：你的 QQ 邮箱地址 (如 `xxxx@qq.com`)
- **发件密码**：登录 QQ 邮箱网页端 -> 设置 -> 账户 -> 开启 "POP3/IMAP/SMTP/Exchange/CardDAV/CalDAV服务" -> 按照提示发送短信获取 **授权码** 并填入本应用。

### 3. 163 网易邮箱配置
- **SMTP 服务器**：`smtp.163.com`
- **SMTP 端口**：`465` (必须勾选 SSL/TLS)
- **发件人邮箱**：你的 163 邮箱地址
- **发件密码**：登录 163 邮箱网页端 -> 设置 -> POP3/SMTP/IMAP -> 开启 "POP3/SMTP服务" -> 新增 **授权密码** 并填入本应用。

---

## 编译与打包指南

你可以直接使用命令行工具，或者使用 Android Studio 进行导入和打包。

### 准备环境
- **JDK 17** 或更高版本。
- 安装有 Android SDK (支持 API 30/Android 11 以上)。

### 使用命令行打包 (Windows)

1. 打开 Windows PowerShell，定位到本项目根目录：
   ```powershell
   cd c:\Users\rock\workspace\gemini\sms_forward_email
   ```
2. 使用自带的 Gradle Wrapper 进行编译打包：
   ```powershell
   .\gradlew.bat assembleDebug
   ```
3. 编译成功后，生成的测试版 APK 文件路径为：
   `app/build/outputs/apk/debug/app-debug.apk`
4. 如果需要打包正式的 Release 版本，请先在 `app/build.gradle` 中配置你的签名密钥（Keystore），然后运行：
   ```powershell
   .\gradlew.bat assembleRelease
   ```
   生成的正式版 APK 文件路径为：
   `app/build/outputs/apk/release/app-release-unsigned.apk` (注：未签名包无法直接安装，需使用 `apksigner` 进行签名)。

### 使用 Android Studio 导入与打包

1. 启动 **Android Studio**。
2. 选择 **Open** (打开项目)，然后选择根文件夹 `sms_forward_email`。
3. Android Studio 会自动下载所需的依赖项（如 Gradle 依赖包、AndroidX 库等）。
4. 菜单栏中依次点击：**Build** -> **Build Bundle(s) / APK(s)** -> **Build APK(s)**。
5. 打包完成后，点击右下角弹窗中的 **Locate** 即可找到编译生成的 `app-debug.apk`。

---

## Keystore 证书获取与签名打包指南

要在真机上顺利安装打包的正式版 APK，Android 要求该包必须拥有合法的数字签名（通过 `.jks` 格式的 Keystore 密钥文件实现）。以下是创建证书和签名的完整指南：

### 1. 如何创建并生成新的 Keystore 证书

#### 方法 A：使用命令行（最快速）
如果你已配置好 JDK 环境变量，可以在电脑的 PowerShell 或终端中直接运行 JDK 自带的 `keytool` 命令：
```powershell
keytool -genkey -v -keystore sms_forwarder.jks -keyalg RSA -keysize 2048 -validity 10000 -alias smskey
```
**命令参数说明**：
- `-keystore sms_forwarder.jks`：生成并命名为 `sms_forwarder.jks` 密钥库。
- `-alias smskey`：证书别名（Alias）设为 `smskey`（后面打包配置要使用）。
- `-keyalg RSA`：加密算法使用 RSA。
- `-validity 10000`：证书有效时长为 10000 天（约 27 年）。

**控制台交互提示**：
1. 会提示设置并确认密钥库的**密码**（输入时界面不会显示字符，例如输入 `123456`，请务必牢记）。
2. 会要求输入你的姓名、单位名称、城市、省份以及双位国家代码（如 `CN`）。
3. 最后询问证书的密钥密码，直接按**回车**（代表和密钥库密码一致）即可。
4. 生成成功后，会在当前执行路径下生成一个 `sms_forwarder.jks` 证书文件。

#### 方法 B：使用 Android Studio 可视化创建
1. 打开 Android Studio 并打开本项目。
2. 依次点击顶部菜单：**Build** (构建) -> **Generate Signed Bundle / APK...** (生成签名的 Bundle / APK)。
3. 选择 **APK** 并点击 **Next**。
4. 在 Key store path 选项下方，点击 **Create new...**。
5. 填写新建密钥库的相关信息：
   - **Key store path**：点击文件夹图标选择输出路径，并保存为 `sms_forwarder.jks`。
   - **Password**：设置密钥库的访问密码。
   - **Alias**：为证书密钥指定一个别名（例如 `smskey`）。
   - **Password** (Key 密码)：设置该密钥的别名密码（建议与密钥库密码保持相同）。
   - **Validity (years)**：有效期通常设置为 `25` 年及以上。
   - **Certificate**：在 `First and Last Name` 栏里输入你的姓名，其他选填。
6. 点击 **OK**，Android Studio 会在指定目录生成 `sms_forwarder.jks` 文件。

---

### 2. 配置 Gradle 自动化签名打包

将创建的 `sms_forwarder.jks` 复制放置在项目的 `app/` 目录下（即与 `app/build.gradle` 处于同一文件夹内）。

修改 `app/build.gradle` 文件，在 `android { ... }` 作用域内加入以下签名配置，并将其绑定到 `release` 编译类型中：

```groovy
android {
    ...
    signingConfigs {
        release {
            storeFile file("sms_forwarder.jks")
            storePassword "123456"
            keyAlias "smskey"
            keyPassword "123456"
        }
    }

    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

配置完毕后，在项目根路径直接运行 Gradle 打包命令即可：
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.19"; .\gradlew.bat assembleRelease
```
编译完成后，已签名的正式安装包将输出在：
`app/build/outputs/apk/release/app-release.apk`
你可以直接将此 APK 传送到手机上双击安装使用！

---

## 常见问题与后台优化 (重要)

由于各大手机厂商（如华为、小米、OPPO、VIVO等）推行了极度严格的后台省电策略，前台服务依然有可能被系统强行终止。为了保证转发的 100% 实时与稳定，请按照以下步骤对手机进行设置：

1. **关闭电池优化**：
   - 手机系统设置 -> 电池/省电管理 -> 电池优化/省电优化 -> 找到“SMS Forwarder”并选择“允许后台活动”或“无限制”。
2. **锁定后台任务**：
   - 打开多任务管理卡片界面，向下拖拽“SMS Forwarder”卡片并点击“加锁”图标，防止一键清理时应用被关闭。
3. **开机自启权限**：
   - 手机系统设置 -> 权限管理 -> 自启动管理 -> 允许“SMS Forwarder”自启动和关联启动。
4. **授予通知与短信权限**：
   - 首次打开应用，务必点击“Grant Permissions”按钮，并在弹窗中允许“读取/接收短信”和“通知”权限。

---

### 常见问题与解答 (Q&A)

#### Q1: 为什么在 Wi-Fi 模式下发送邮件会超时（Timeout 10000），但切换到手机流量（4G/5G）就可以成功发送？
**A**: 这通常是由于 Wi-Fi 局域网限制或服务屏蔽导致的：
1. **网络拦截/服务屏蔽**：如果您使用的是 Gmail (`smtp.gmail.com`)，在国内未开启全局网络代理的情况下，Wi-Fi（国内宽带）会直接被 GFW 拦截导致超时；而流量测试时如开启了代理或使用的是漫游卡则可以访问。
2. **SMTP 端口被拦截**：部分宽带运营商（ISP）或 Wi-Fi 路由器防火墙会默认阻断 25、465 或 587 等用于发送邮件的端口。可以尝试更换为 465（SSL）或 587（STARTTLS）测试，或更换到其他 Wi-Fi/流量下测试。
3. **公网 IP 被拉黑**：邮件服务商对来源 IP 有风控机制。如果您的 Wi-Fi 公网 IP 曾因共享或拨号分配到了“高风险”网段，可能被邮件服务器临时拒接。

*建议方案*：在国内使用时，强烈建议将发件邮箱更换为国内的 **QQ 邮箱 (`smtp.qq.com`)** 或 **网易 163 邮箱 (`smtp.163.com`)**，并配合邮箱专属的 **“客户端授权码”**（非登录密码）进行配置。

#### Q2: 手机挂在后台或锁屏很久之后，短信转发失败，日志里报 `timeout 10000` 连接超时？
**A**: 这是因为 Android 系统的**低电耗模式（Doze Mode）或厂商省电策略**在作祟：
* 当手机进入深度休眠后，系统会强制切断非白名单后台应用的网络访问。即使短信广播能唤醒 App 接收短信，但 App 发起连接请求时，网络会被系统拦截，最终导致 10 秒超时。
* **解决方法**：必须在手机设置中将本 App 设置为 **“忽略电池优化”/“无限制”** 运行，并在多任务界面将其 **“加锁”** 锁定，以确保系统在深度休眠时不会限制其网络连接。

#### Q3: 为什么重装或更新 APK 后，收到短信日志提示 `【接收短信(服务已关闭)】` 且不再转发？
**A**: 当卸载重装或覆盖安装新包时，Android 会清空该 App 所有的本地数据和配置（SharedPreferences）。
* 这会导致“启用转发服务”开关和所有的 SMTP 配置、收件人信息被恢复为默认的 **关闭** 和 **空白** 状态。
* **解决方法**：只需重新打开 App，检查并重新填好 SMTP/收件人配置，然后手动开启顶部的 **“启用转发服务”** 开关即可。

#### Q4: 能否关闭通知权限，只在后台默默运行？为什么系统的应用不需要通知权限？
**A**: **不能**。
1. 自 Android 13 起，前台服务（Foreground Service）必须依赖常驻通知来向用户提示应用正在运行。如果关闭通知权限，系统会把应用降级为普通后台程序，并在锁屏或后台挂起数分钟内迅速杀掉其进程。
2. 系统自带的应用（如系统短信、微信等）由于厂商内置了专属的系统级免杀白名单和特权，因此不需要前台通知也能常驻，而第三方安装的 App 必须遵守 Android 的规范来使用通知。
