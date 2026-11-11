import * as pool from './pool'

var threads = Object.create(runtime.threads) as Autox.Threads & typeof pool;

Object.assign(threads, pool);
declare global {
    var sync: (func: unknown, lock: unknown) => any
}
global.sync = function (func: unknown, lock: unknown) {
    lock = lock || null;
    return new org.mozilla.javascript.Synchronizer(func, lock);
}

const r = Promise.prototype as any;
r.wait = function () {
    var disposable = threads.disposable();
    this.then((result: any) => {
        disposable.setAndNotify({ result: result });
    }).catch((error: any) => {
        disposable.setAndNotify({ error: error });
    });
    var r = disposable.blockedGet();
    if (r.error) {
        throw r.error;
    }
    return r.result;
}
export function startThread(fn: () => void) {
    threads.start(fn);
}

export default threads;