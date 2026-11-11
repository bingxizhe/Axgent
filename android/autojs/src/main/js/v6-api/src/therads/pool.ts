
const main = runtime.threads.currentThread()
const t = runtime.threads as any


export function runOnMainThread(fn: () => void) {
    main.setImmediate(fn);
}

export function runOnThreadPool(fn: () => void) {
    t.runTaskForThreadPool(fn);
}

export function runOnIoThreadPool(fn: () => void) {
    t._runTaskForIoThreadPool(fn);
}

export function runAsync<T>(fn: () => T): Promise<T> {
    return new Promise(function (resolve, reject) {
        runOnThreadPool(function () {
            try {
                const result: T = fn();
                resolve(result)
            } catch (e) {
                reject(e)
            }
        })
    })
}