package de.robv.android.xposed;

public class XC_MethodReplacement extends XC_MethodHook {

    public static XC_MethodReplacement returnConstant(Object result) {
        return new XC_MethodReplacement();
    }
}
