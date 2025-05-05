package io.flutter.plugins.webview_cookie_manager;

import android.net.Uri;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;

import androidx.annotation.NonNull;

import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

/**
 * WebviewCookieManagerPlugin
 */
public class WebviewCookieManagerPlugin implements FlutterPlugin, MethodChannel.MethodCallHandler {
    private MethodChannel channel;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "webview_cookie_manager");
        channel.setMethodCallHandler(this);
    }

    @Override
    public void onMethodCall(MethodCall methodCall, MethodChannel.Result result) {
        switch (methodCall.method) {
            case "clearCookies":
                clearCookies(result);
                break;
            case "hasCookies":
                hasCookies(result);
                break;
            case "getCookies":
                getCookies(methodCall, result);
                break;
            case "setCookies":
                setCookies(methodCall, result);
                break;
            default:
                result.notImplemented();
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
        channel = null;
    }

    private static void hasCookies(final MethodChannel.Result result) {
        CookieManager cookieManager = CookieManager.getInstance();
        result.success(cookieManager.hasCookies());
    }

    private static void clearCookies(final MethodChannel.Result result) {
        CookieManager cookieManager = CookieManager.getInstance();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.removeAllCookies(value -> result.success(cookieManager.hasCookies()));
            cookieManager.flush();
        } else {
            cookieManager.removeAllCookie();
            result.success(cookieManager.hasCookies());
        }
    }

    private static void getCookies(final MethodCall methodCall, final MethodChannel.Result result) {
        if (!(methodCall.arguments() instanceof Map)) {
            result.error("Invalid argument", "Expected Map<String,String>", null);
            return;
        }

        final Map<String, String> arguments = methodCall.arguments();
        CookieManager cookieManager = CookieManager.getInstance();
        final String url = arguments.get("url");
        final String allCookiesString = url == null ? null : cookieManager.getCookie(url);
        final ArrayList<String> individualCookieStrings = allCookiesString == null
                ? new ArrayList<>()
                : new ArrayList<>(Arrays.asList(allCookiesString.split(";")));

        ArrayList<Map<String, Object>> serializedCookies = new ArrayList<>();
        for (String cookieString : individualCookieStrings) {
            try {
                final HttpCookie cookie = HttpCookie.parse(cookieString).get(0);
                if (cookie.getDomain() == null) {
                    cookie.setDomain(Uri.parse(url).getHost());
                }
                if (cookie.getPath() == null) {
                    cookie.setPath("/");
                }
                serializedCookies.add(cookieToMap(cookie));
            } catch (IllegalArgumentException e) {
                // Ignore invalid cookies
            }
        }

        result.success(serializedCookies);
    }

    private static void setCookies(final MethodCall methodCall, final MethodChannel.Result result) {
        if (!(methodCall.arguments() instanceof List)) {
            result.error("Invalid argument", "Expected List<Map<String,String>>", null);
            return;
        }

        final List<Map<String, Object>> serializedCookies = methodCall.arguments();
        CookieManager cookieManager = CookieManager.getInstance();

        for (Map<String, Object> cookieMap : serializedCookies) {
            Object origin = cookieMap.get("origin");
            String domainString = origin instanceof String ? (String) origin : null;
            if (domainString == null) {
                Object domain = cookieMap.get("domain");
                domainString = domain instanceof String ? (String) domain : "";
            }
            final String value = cookieMap.get("asString").toString();
            cookieManager.setCookie(domainString, value);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.flush();
        }

        result.success(null);
    }

    private static Map<String, Object> cookieToMap(HttpCookie cookie) {
        final HashMap<String, Object> resultMap = new HashMap<>();
        resultMap.put("name", cookie.getName());
        resultMap.put("value", cookie.getValue());
        resultMap.put("path", cookie.getPath());
        resultMap.put("domain", cookie.getDomain());
        resultMap.put("secure", cookie.getSecure());

        if (!cookie.hasExpired() && !cookie.getDiscard() && cookie.getMaxAge() > 0) {
            long expires = (System.currentTimeMillis() / 1000) + cookie.getMaxAge();
            resultMap.put("expires", expires);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resultMap.put("httpOnly", cookie.isHttpOnly());
        }

        return resultMap;
    }
}