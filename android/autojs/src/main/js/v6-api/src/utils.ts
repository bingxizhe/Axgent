import { startThread } from "./therads";

export function setGlobal(obj: { [key: string]: any }): void
export function setGlobal(key: string, value: any): void
export function setGlobal(key: string | { [key: string]: any }, value?: any) {
    if (typeof key === "string") {
        (global as any)[key] = value;
        return
    }
    if (typeof key === "object") {
        for (const [k, value] of Object.entries(key)) {
            setGlobal(k, value);
        }
        return
    }
}

export function setGlobalAnd$(obj: { [key: string]: any }): void
export function setGlobalAnd$(key: string, value: any): void
export function setGlobalAnd$(key: string | { [key: string]: any }, value?: any) {
    if (typeof key === "object") {
        for (const [k, value] of Object.entries(key)) {
            setGlobalAnd$(k, value);
        }
        return
    }
    if (key.startsWith("$")) {
        key = key.substring(1);
    }
    setGlobal(key, value);
    setGlobal('$' + key, value);
}

export function exitIfError(action: () => void, defReturnValue?: any) {
    try {
        return action();
    } catch (err) {
        if (err instanceof java.lang.Throwable) {
            exit(err);
        } else if (err instanceof Error) {
            const e: any = err
            exit(new org.mozilla.javascript.EvaluatorException(err.name + ": " + err.message, e.fileName, e.lineNumber));
        } else {
            exit();
        }
        return defReturnValue;
    }
};

export function defineGetter(obj: any, prop: string, getter: <T>() => T) {
    Object.defineProperty(obj, prop, { get: getter });
}

export function asGlobal(obj: any, keys: string[]) {
    var len = keys.length;
    for (var i = 0; i < len; i++) {
        var funcName = keys[i];
        var func = obj[funcName]
        if (!func) {
            continue;
        }
        (global as any)[funcName] = func.bind(obj);
    }
}


const loopers = runtime.loopers
const weakReferenceKey = (runtime as any).weakReferenceKey

export function createCallbackWrapper<T>(callback: T): T {
    if (typeof callback !== 'function')
        throw new Error('Callback must be a function')
    const t = loopers.createAndAddAsyncTask('callback_wrapper')
    const v = weakReferenceKey.newRefValue(callback)
    const fn = function (...args: any[]) {
        const cb = v.value
        if (cb !== null) {
            startThread(() => {
                try {
                    exitIfError(() => {
                        cb(...args)
                    })
                } catch (e: any) {
                    //ignore
                }
            })
            loopers.removeAsyncTask(t)
        } else {
            loopers.removeAsyncTask(t)
        }
    }
    fn.name = `callback_wrapper<${callback.name}>`

    return fn as any
}