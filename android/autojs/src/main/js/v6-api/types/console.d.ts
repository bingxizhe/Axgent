// 定义 EventEmitter 接口 (简化版，用于 console.emitter)
interface ConsoleEventEmitter {
    /**
     * 注册事件监听器
     * @param event 事件名称: 'println' | 'VERBOSE' | 'DEBUG' | 'INFO' | 'WARN' | 'ERROR' | 'ASSERT'
     * @param listener 回调函数
     */
    on(event: 'println' | 'VERBOSE' | 'DEBUG' | 'INFO' | 'WARN' | 'ERROR' | 'ASSERT', listener: (log: string, level: number, levelString: string) => void): this;

    /**
     * 注册一次性事件监听器
     */
    once(event: 'println' | 'VERBOSE' | 'DEBUG' | 'INFO' | 'WARN' | 'ERROR' | 'ASSERT', listener: (log: string, level: number, levelString: string) => void): this;

    /**
     * 移除监听器
     */
    removeListener(event: 'println' | 'VERBOSE' | 'DEBUG' | 'INFO' | 'WARN' | 'ERROR' | 'ASSERT', listener: Function): this;
}

interface LogConfig {
    /** 日志文件路径，将会把日志写入该文件中 */
    file?: string;
    /** 最大文件大小，单位字节，默认为 512 * 1024 (512KB) */
    maxFileSize?: number;
    /** 写入的日志级别，默认为"ALL" */
    rootLevel?: "OFF" | "DEBUG" | "INFO" | "WARN" | "ERROR" | "FATAL" | "ALL";
    /** 日志备份文件最大数量，默认为 5 */
    maxBackupSize?: number;
    /** 日志写入格式，参见 PatternLayout */
    filePattern?: string;
}

interface AutoJsConsole {
    /**
     * 显示控制台。这会显示一个控制台的悬浮窗(需要悬浮窗权限)。
     * @param autoHide 是否自动隐藏，默认 false。当程序结束的时候是否自动隐藏控制台。
     */
    show(autoHide?: boolean): void;

    /**
     * 隐藏控制台悬浮窗。
     */
    hide(): void;

    /**
     * 清空控制台。
     */
    clear(): void;

    /**
     * 打印到控制台，并带上换行符。
     * 第一个参数作为主要信息，其他参数作为类似于 printf(3) 中的代替值。
     */
    log(data: any, ...args: any[]): void;

    /**
     * 与 console.log 类似，但输出结果以灰色字体显示。
     * 输出优先级低于 log，用于输出观察性质的信息。
     */
    verbose(data: any, ...args: any[]): void;

    /**
     * 与 console.log 类似，但输出结果以绿色字体显示。
     * 输出优先级高于 log, 用于输出重要信息。
     */
    info(data: any, ...args: any[]): void;

    /**
     * 与 console.log 类似，但输出结果以蓝色字体显示。
     * 输出优先级高于 info, 用于输出警告信息。
     */
    warn(data: any, ...args: any[]): void;

    /**
     * 与 console.log 类似，但输出结果以红色字体显示。
     * 输出优先级高于 warn, 用于输出错误信息。
     */
    error(data: any, ...args: any[]): void;

    /**
     * 断言。如果 value 为 false 则输出错误信息 message 并停止脚本运行。
     * @param value 要断言的布尔值
     * @param message value 为 false 时要输出的信息
     */
    assert(value: any, message: string): void;

    /**
     * 启动一个计时器，用以计算一个操作的持续时间。
     * [v4.1.0 新增]
     * @param label 计时器标签，可省略
     */
    time(label?: string): void;

    /**
     * 停止之前通过调用 console.time() 启动的定时器，并打印结果到控制台。
     * [v4.1.0 新增]
     * @param label 计时器标签
     */
    timeEnd(label: string): void;

    /**
     * 与 console.log 类似，同时会打印出调用这个函数所在的调用栈信息。
     * [v4.1.0 新增]
     */
    trace(data?: any, ...args: any[]): void;

    /**
     * 与console.log一样输出信息，并在控制台显示输入框等待输入。
     * 按控制台的确认按钮后会将输入的字符串用 eval 计算后返回。
     * @returns 用户输入并 eval 后的结果
     */
    input(data: any, ...args: any[]): any;

    /**
     * 与 console.log 一样输出信息，并在控制台显示输入框等待输入。
     * 按控制台的确认按钮后会将输入的字符串直接返回。
     * @returns 用户输入的原始字符串
     */
    rawInput(data: any, ...args: any[]): string;

    /**
     * 设置控制台的大小，单位像素。
     * @param w 宽度
     * @param h 高度
     */
    setSize(w: number, h: number): void;

    /**
     * 设置控制台的位置，单位像素。
     * @param x 横坐标
     * @param y 纵坐标
     */
    setPosition(x: number, y: number): void;

    /**
     * 设置日志保存的路径和配置。
     * [v4.1.0 新增] [v7.0.2 弃用]
     * 在 v7.0.4 之后除 file 选项外其他选项将不起作用。
     */
    setGlobalLogConfig(config: LogConfig): void;

    /**
     * 设置标题名称，字体颜色，标题栏高度。
     * [v4.2.5 新增]
     * @param title 标题
     * @param color 颜色值 #AARRGGBB (可选)
     * @param size 标题高度，单位 dp (可选)
     */
    setTitle(title: string, color?: string, size?: number): void;

    /**
     * 设置 log 字号大小。
     * 需要在显示控制台之后才能设置，否则空指针。
     * [v4.2.5 新增]
     * @param size 字号大小，单位是 dp 或 sp，20 以内比较合适
     */
    // BUG
    // setLogSize(size: number): void;

    /**
     * 控制 console 是否可以输入文字。
     * [v4.2.5 新增]
     * @param can true 或 false
     */
    // BUG
    // setCanInput(can: boolean): void;

    /**
     * 设置 console 背景色。
     * 需要在显示控制台之后才能设置，否则空指针。
     * [v4.2.5 新增]
     * @param color 颜色值 #AARRGGBB
     */
    // BUG
    // setBackground(color?: string): void;

    /**
     * 设置 console 显示最大行数，默认-1 (不限)。
     * [v5.0.2 新增]
     * @param maxLines 最大行数
     */
    setMaxLines(maxLines: number): void;

    /**
     * 此对象在控制台打印内容时触发一些事件。
     * [v7.0.4 新增]
     */
    emitter: ConsoleEventEmitter;

    /**
     * 全局控制台的 EventEmiiter 对象。
     * 获得的日志内容会与在 app 控制台中看到的一样，包括其他脚本的打印内容。
     * [v7.0.4 新增]
     */
    globalEmitter: ConsoleEventEmitter;
}

// 声明全局变量 console
declare var console: AutoJsConsole;

// ==============================
// 全局函数别名
// ==============================

/**
 * 打印到控制台，并带上换行符。
 * 相当于 console.log。
 */
declare function log(data: any, ...args: any[]): void;

/**
 * 要打印到控制台的信息。
 * 相当于 log(text)。
 */
declare function print(text: string | object): void;