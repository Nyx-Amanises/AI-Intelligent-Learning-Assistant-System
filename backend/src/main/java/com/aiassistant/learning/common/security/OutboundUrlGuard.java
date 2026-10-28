package com.aiassistant.learning.common.security;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.config.AiProperties;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 用户可配置的 AI 服务地址的出站校验，防止服务端请求伪造（SSRF）。
 *
 * <p>AI 配置里的 Base URL 和接口路径由用户填写，后端会带着 API Key 向它发起 POST 请求。
 * 不加限制的话，任何注册用户都能让服务器去访问内网服务或云厂商的元数据地址。这里统一要求：</p>
 * <ul>
 *   <li>只允许 http / https，地址里不能带用户名密码；</li>
 *   <li>主机解析出的任何一个 IP 落在本机、内网、链路本地（含 169.254.169.254 元数据地址）、
 *       运营商级 NAT（100.64.0.0/10）、组播等范围内都拒绝。</li>
 * </ul>
 * <p>保存配置时校验一次（域名暂时解析不了不拦截），每次真正发请求前再按最终 URL 校验一次，
 * 覆盖历史数据和域名解析变化。需要连接本机部署的模型服务（如 Ollama）时，
 * 可设置 APP_AI_ALLOW_PRIVATE_NETWORK=true 放开内网限制。</p>
 */
@Component
public class OutboundUrlGuard {

    private final AiProperties aiProperties;

    public OutboundUrlGuard(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    /**
     * 保存配置时校验地址。域名暂时无法解析时放行，发请求前会再次校验。
     *
     * @param url 待校验的完整地址
     * @param label 出错时提示用的字段名，例如“聊天服务地址”
     */
    public void validateConfiguredUrl(String url, String label) {
        check(parse(url, label), label, false);
    }

    /**
     * 发请求前校验最终要访问的 URI，域名无法解析时直接拒绝。
     *
     * @param uri 最终请求地址
     * @param label 出错时提示用的字段名
     */
    public void checkRequestTarget(URI uri, String label) {
        check(uri, label, true);
    }

    /**
     * 拼接 Base URL 与接口路径，保证两者之间恰好一个斜杠。
     *
     * <p>路径始终接在 Base URL 的主机之后，写成 "@evil.com/x" 这类内容也改变不了请求主机。</p>
     */
    public static URI joinUrl(String baseUrl, String path, String label) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException(label + "未配置");
        }
        String normalizedBase = baseUrl.trim();
        String normalizedPath = StringUtils.hasText(path) ? path.trim() : "";
        if (normalizedPath.contains("://")) {
            throw new BusinessException(label + "的接口路径只能填写路径部分，例如 /v1/chat/completions");
        }
        while (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        if (!normalizedPath.isEmpty() && !normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        return parse(normalizedBase + normalizedPath, label);
    }

    private static URI parse(String url, String label) {
        if (!StringUtils.hasText(url)) {
            throw new BusinessException(label + "未配置");
        }
        try {
            return new URI(url.trim());
        } catch (URISyntaxException exception) {
            throw new BusinessException(label + "格式不正确：" + url.trim());
        }
    }

    private void check(URI uri, String label, boolean requireResolvable) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new BusinessException(label + "只支持 http:// 或 https:// 开头的地址");
        }
        if (uri.getRawUserInfo() != null) {
            throw new BusinessException(label + "不能包含用户名或密码");
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new BusinessException(label + "缺少主机名");
        }
        if (Boolean.TRUE.equals(aiProperties.getAllowPrivateNetwork())) {
            return;
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            if (requireResolvable) {
                throw new BusinessException(label + "的域名无法解析：" + host);
            }
            return;
        }
        for (InetAddress address : addresses) {
            if (isInternalAddress(address)) {
                throw new BusinessException(label + "指向本机或内网地址（" + host + "），已拒绝访问。"
                        + "如需连接本地部署的模型服务，请由管理员设置 APP_AI_ALLOW_PRIVATE_NETWORK=true");
            }
        }
    }

    /**
     * 判断地址是否属于本机、内网或其他不应由用户配置访问的范围。
     */
    static boolean isInternalAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            return isInternalIpv4(bytes);
        }
        if (address instanceof Inet6Address) {
            // fc00::/7 唯一本地地址
            if ((bytes[0] & 0xfe) == 0xfc) {
                return true;
            }
            // ::ffff:a.b.c.d 形式的 IPv4 映射地址，按内嵌的 IPv4 判断
            boolean ipv4Mapped = true;
            for (int index = 0; index < 10; index++) {
                ipv4Mapped &= bytes[index] == 0;
            }
            if (ipv4Mapped && bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff) {
                try {
                    return isInternalAddress(InetAddress.getByAddress(new byte[] {bytes[12], bytes[13], bytes[14], bytes[15]}));
                } catch (UnknownHostException exception) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isInternalIpv4(byte[] bytes) {
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        // 0.0.0.0/8 本网络；100.64.0.0/10 运营商级 NAT，部分云厂商的元数据服务也在这个范围
        return first == 0 || (first == 100 && second >= 64 && second <= 127);
    }
}
