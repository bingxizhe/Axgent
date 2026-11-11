

const scope = global as any
const runtime = scope.runtime
export function newWebSocket(url: string, options?: { eventThread?: 'this' }) {
    options = options || {};
    return new com.stardust.autojs.core.web.WebXSocket(
        (http as any).__okhttp__, url, runtime, options.eventThread == 'this'
    );
}

export const ByteString = Packages.okio.ByteString

export function newInjectableWebView() {
    return new com.stardust.autojs.core.web.InjectableWebView(
        scope.activity, org.mozilla.javascript.Context.getCurrentContext(), scope);
}

export function newInjectableWebClient() {
    return new com.stardust.autojs.core.web.InjectableWebClient(org.mozilla.javascript.Context.getCurrentContext(), scope);
}