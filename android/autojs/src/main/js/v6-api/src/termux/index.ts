import { createCallbackWrapper } from "../utils";
export interface CommandOptions {
    //可执行文件路径
    path: string;
    //传递的参数
    arguments?: string[];
    //工作目录，默认为'/data/data/com.termux/files/home'
    workdir?: string;
    //用于指定命令是在后台终端会话还是前台终端会话中运行。默认值为false。
    background?: boolean;
    sessionAction?: '0' | '1' | '2' | '3';
    label?: string;
    description?: string;
}
export interface Result {
    stdout: string;
    stdout_original_length: number;
    stderr: string;
    stderr_original_length: number;
    exitCode: number;
    errCode: number | null;
    errmsg: string | null;
}
export type Callback = {
    (result: Result): void;
};
type ShortcutCommandFunction = {
    (args?: string | string[], callback?: Callback): void;
    (args?: string | string[], workdir?: string, callback?: Callback): void;
    (args?: string | string[], options?: CommandOptions, callback?: Callback): void;
};
const termux = runtime.termux

function createCallback(callback: Callback) {
    if (typeof callback !== 'function')
        throw new Error('Callback must be a function')
    const k = function (result: any) {
        const res: Result = {
            stdout: result.stdout,
            stdout_original_length: Number(result.stdout_original_length) || 0,
            stderr: result.stderr,
            stderr_original_length: Number(result.stderr_original_length) || 0,
            exitCode: result.exitCode,
            errCode: result.errCode,
            errmsg: result.errmsg,
        }
        callback(res)
    }
    return createCallbackWrapper(k)
}

export function createShortcutCommand(options: CommandOptions): ShortcutCommandFunction {
    return function (args?: string | string[],
        op?: CommandOptions | Callback | string,
        callback?: Callback) {
        if (typeof op === 'function') {
            callback = op
            op = undefined
        }
        let workdir: string | undefined
        if (typeof op === 'string') {
            workdir = op
            op = undefined
        }
        const o: CommandOptions = Object.assign({}, options, op)
        if (workdir) {
            o.workdir = workdir
        }
        if (typeof args === 'string') {
            o.arguments = options.arguments ? options.arguments.concat([args]) : [args]
        } else if (Array.isArray(args)) {
            o.arguments = options.arguments ? options.arguments.concat(args) : args
        }
        return runCommand(o, callback)
    }
}

export function runCommand(options: CommandOptions, callback?: Callback): void {
    const command = termux.newCommand(options.path)
    if (options.arguments) {
        command.setArgument(...options.arguments)
    }
    if (options.workdir) {
        command.setWorkdir(options.workdir)
    }
    if (options.background !== undefined) {
        command.setBackground(options.background)
    }
    if (options.sessionAction !== undefined) {
        command.setSessionAction(options.sessionAction)
    }
    if (options.label) {
        command.setLabel(options.label)
    }
    if (options.description) {
        command.setDescription(options.description)
    }
    if (callback) {
        const cb = createCallback(callback)
        return termux.runCommand(command, cb)
    } else
        termux.runCommand(command)
}

//使用 bash -c 运行指定的命令
export const bash: ShortcutCommandFunction = function (
    args?: string | string[],
    o?: CommandOptions | Callback | string,
    callback?: Callback) {
    if (typeof o === 'function') {
        callback = o
        o = undefined
    }
    let workdir: string | undefined
    if (typeof o === 'string') {
        workdir = o
        o = undefined
    }
    const op: CommandOptions = Object.assign({}, o, {
        path: '/data/data/com.termux/files/usr/bin/bash',
        arguments: ['-c']
    })
    if (workdir) {
        op.workdir = workdir
    }
    if (typeof args === 'string') {
        op.arguments!.push(args)
    } else if (Array.isArray(args)) {
        op.arguments!.push(args.join(' '))
    } else {
        throw new Error('Invalid argument type')
    }
    return runCommand(op, callback)
}

export const sh: ShortcutCommandFunction = function (
    args?: string | string[],
    o?: CommandOptions | Callback | string,
    callback?: Callback) {
    if (typeof o === 'function') {
        callback = o
        o = undefined
    }
    let workdir: string | undefined
    if (typeof o === 'string') {
        workdir = o
        o = undefined
    }
    const op: CommandOptions = Object.assign({}, o, {
        path: '/data/data/com.termux/files/usr/bin/sh',
        arguments: ['-c']
    })
    if (workdir) {
        op.workdir = workdir
    }
    if (typeof args === 'string') {
        op.arguments!.push(args)
    } else if (Array.isArray(args)) {
        op.arguments!.push(args.join(' '))
    } else {
        throw new Error('Invalid argument type')
    }
    return runCommand(op, callback)
}

export function checkPermission(): boolean {
    return termux.checkPermission()
}

export function requestPermission(callback?: (r: boolean) => void): void {
    if (checkPermission()) {
        if (typeof callback === 'function') {
            createCallbackWrapper(callback)(true)
        }
        return
    }
    if (callback) {
        if (typeof callback !== 'function')
            throw new Error('Callback must be a function')
        const cb = createCallbackWrapper(callback)
        termux.requestPermission(cb)
    } else {
        termux.requestPermission(null)
    }
}