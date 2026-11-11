/**
 * 发送邮件的选项参数
 */
export interface AppEmailOptions {
    /** 收件人的邮件地址。如果有多个收件人，则用字符串数组表示 */
    email?: string | string[];
    /** 抄送收件人的邮件地址。如果有多个抄送收件人，则用字符串数组表示 */
    cc?: string | string[];
    /** 密送收件人的邮件地址。如果有多个密送收件人，则用字符串数组表示 */
    bcc?: string | string[];
    /** 邮件主题 (标题) */
    subject?: string;
    /** 邮件正文 */
    text?: string;
    /** 附件的路径 */
    attachment?: string;
}

/**
 * 构建 Intent 的选项参数
 */
export interface AppIntentOptions {
    /** 意图的 Action，例如 "android.intent.action.VIEW" 或简写 "VIEW" */
    action?: string;
    /** 意图的 MimeType，例如 "text/plain" */
    type?: string;
    /** 意图的 Data，是一个 Uri 字符串，例如 "file:///sdcard/1.txt" */
    data?: string;
    /** 意图的类别 */
    category?: string[];
    /** 目标包名 */
    packageName?: string;
    /** 目标 Activity 或 Service 等组件的名称 */
    className?: string;
    /** 以键值对构成的这个 Intent 的 Extras(额外信息) */
    extras?: { [key: string]: any };
    /** intent 的标识，字符串数组，例如 ["activity_new_task", "grant_read_uri_permission"] */
    flags?: string[];
    /** 是否以 root 权限启动、发送该 intent。使用该参数后，不能使用 context 相关方法 */
    root?: boolean;
}

export interface AppModule {
    /**
     * 当前软件版本号，整数值。例如 160, 256 等。
     * 如果在 Auto.js 中运行则为 Auto.js 的版本号；在打包的软件中则为打包软件的版本号。
     */
    versionCode: number;

    /**
     * 当前软件的版本名称，例如"3.0.0 Beta"。
     * 如果在 Auto.js 中运行则为 Auto.js 的版本名称；在打包的软件中则为打包软件的版本名称。
     */
    versionName: string;

    autojs: {
        /** Auto.js 版本号，整数值。例如 160, 256 等。 */
        versionCode: number;
        /** Auto.js 版本名称，例如"3.0.0 Beta"。 */
        versionName: string;
    };

    /**
     * 通过应用名称启动应用。
     * @param appName 应用名称
     * @returns 如果该名称对应的应用不存在，则返回 false; 否则返回 true。
     */
    launchApp(appName: string): boolean;

    /**
     * 通过应用包名启动应用。
     * @param packageName 应用包名
     * @returns 如果该包名对应的应用不存在，则返回 false；否则返回 true。
     */
    launch(packageName: string): boolean;

    /**
     * 相当于 app.launch(packageName)。
     * @param packageName 应用包名
     */
    launchPackage(packageName: string): boolean;

    /**
     * 获取应用名称对应的已安装的应用的包名。
     * @param appName 应用名称
     * @returns 如果找不到该应用，返回 null 。如果该名称对应多个应用，则只返回其中某一个的包名。
     */
    getPackageName(appName: string): string | null;

    /**
     * 获取应用包名对应的已安装的应用的名称。
     * @param packageName 应用包名
     * @returns 如果找不到该应用，返回 null。
     */
    getAppName(packageName: string): string | null;

    /**
     * 打开应用的详情页 (设置页)。
     * @param packageName 应用包名
     * @returns 如果找不到该应用，返回 false; 否则返回 true。
     */
    openAppSetting(packageName: string): boolean;

    /**
     * 用其他应用查看文件。文件不存在的情况由查看文件的应用处理。
     * 如果找不出可以查看该文件的应用，则抛出 ActivityNotException。
     * @param path 文件路径
     */
    viewFile(path: string): void;

    /**
     * 用其他应用编辑文件。文件不存在的情况由编辑文件的应用处理。
     * 如果找不出可以编辑该文件的应用，则抛出 ActivityNotException。
     * @param path 文件路径
     */
    editFile(path: string): void;

    /**
     * 卸载应用。执行后会会弹出卸载应用的提示框。
     * @param packageName 应用包名
     */
    uninstall(packageName: string): void;

    /**
     * 用浏览器打开网站 url。
     * @param url 网站的 Url，如果不以"http://"或"https://"开头则默认是"http://"。
     */
    openUrl(url: string): void;

    /**
     * 根据选项 options 调用邮箱应用发送邮件。
     * 如果没有安装邮箱应用，则抛出 ActivityNotException。
     * @param options 发送邮件的参数
     */
    sendEmail(options: AppEmailOptions): void;

    /**
     * 启动 Auto.js 的特定界面。
     * @param name 活动名称，可选的值为："console" (日志界面), "settings" (设置界面)
     */
    startActivity(name: "console" | "settings"): void;

    /**
     * 根据选项构造一个 Intent，并启动该 Activity。
     * @param options 选项
     */
    startActivity(options: AppIntentOptions): void;

    /**
     * 根据选项，构造一个意图 Intent 对象。
     * @param options 选项
     */
    intent(options: AppIntentOptions): android.Intent;

    /**
     * 根据选项构造一个 Intent，并发送该广播。
     * @param options 选项
     */
    sendBroadcast(options: AppIntentOptions): void;

    /**
     * 发送特定名称的广播以触发 Auto.js 的布局分析。
     * @param name 特定的广播名称，包括："inspect_layout_hierarchy" (布局层次分析), "inspect_layout_bounds" (布局范围)
     */
    sendBroadcast(name: "inspect_layout_hierarchy" | "inspect_layout_bounds"): void;

    /**
     * 根据选项构造一个 Intent，并启动该服务。
     * @param options 选项
     */
    startService(options: AppIntentOptions): void;

    /**
     * 根据选项构造一个 Intent，转换为对应的 shell 的 intent 命令的参数。
     * 参见: https://developer.android.com/studio/command-line/adb#IntentSpec
     * @param options 选项
     * @returns shell 命令参数字符串
     */
    intentToShell(options: AppIntentOptions): string;

    /**
     * 解析 uri 字符串并返回相应的 Uri 对象。
     * @param uri 一个代表 Uri 的字符串，例如 "file:///sdcard/1.txt"
     */
    parseUri(uri: string): android.Uri;

    /**
     * 从一个文件路径创建一个 uri 对象。
     * 注意：在高版本 Android 上，返回的 Uri 可能是 content:// 形式。
     * @param path 文件路径，例如 "/sdcard/1.txt"
     */
    getUriForFile(path: string): android.Uri;
}
