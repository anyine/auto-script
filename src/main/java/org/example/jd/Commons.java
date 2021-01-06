package org.example.jd;

import org.apache.http.cookie.Cookie;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class Commons {
    public static final String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:73.0) Gecko/20100101 Firefox/73.0";

    public final static String USER_PATH = System.getProperty("user.dir");

    public final static String QR_URL = "https://qr.m.jd.com/show?appid=133&size=147&t={}";
    public final static String QR_CHECK_URL = "https://qr.m.jd.com/check?callback={}&appid=133&token={}&_={}";
    public final static String QR_TICKET_VALIDATION_URL = "https://passport.jd.com/uc/qrCodeTicketValidation?t={}";
    public final static String GOODS_URL = "https://item.jd.com/{}.html";

    public final static String STOCKS_URL = "http://c0.3.cn/stocks?type=getstocks&area={}&callback=jQuery{}&_={}&skuIds={}";

    public final static String SUBMIT_URL = "https://trade.jd.com/shopping/order/submitOrder.action";
    public final static String ADD_CART_URL = "https://cart.jd.com/gate.action?callback=jQuery{}&pid={}&f=3&ptype=1&pcount=1&_={}";

    public final static Pattern STOCK_STATE_PATTERN = Pattern.compile("无货");
    // 供应商
    public final static Pattern VENDERID_PATTERN = Pattern.compile("venderId:(\\d+),");
    public final static Pattern CAT_PATTERN = Pattern.compile("cat: \\[(\\d{3,5},\\d{3,5},\\d{3,5})\\]");
    //商品已下架
    public final static Pattern TAKEOFF_PATTERN = Pattern.compile("该商品已下柜，欢迎挑选其他商品！");
    public final static Pattern SUCCESS_PATTERN = Pattern.compile("\"success\": ?true");
    //匹配商品名称
    public final static Pattern GOODS_NAME_PATTERN = Pattern.compile("name: '([\\u4e00-\\u9fa5|a-z|A-Z|0-9|\\s|*]+)'");
    //是否登陆授权
    public final static Pattern CART_SUCCESS_PATTERN = Pattern.compile("\"flag\":true");

    public static Config config = new Config();
    public static final Map<String, Goods> goodsMap = new ConcurrentHashMap<>();

    public static Cookie getCookie(String key) {
        if (null == config.getBasicCookieStore()) {
            return null;
        }
        for (Cookie cookie : config.getBasicCookieStore().getCookies()) {
            if (key.equals(cookie.getName())) {
                return cookie;
            }
        }
        return null;
    }
}
