package org.example.jd;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.UnicodeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.http.cookie.Cookie;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;

/**
 * Hello world!
 *
 */
@Slf4j
public class App
{
    public static void main( String[] args ){
        Commons.config.setArea("22_1930_50945_52158"); //下单地区 下单页面获取 $("#hideAreaIds").val().replace(/-/g, '_')
        Commons.config.setSkuids("100008488857"); //商品sku https://item.jd.com/100013985188.html#crumb-wrap 其中 100013985188 为sku
        Commons.config.setTicket("");
        Commons.config.setIsLogin(false);
        Commons.config.setOrderParam(new OrderParam());
        App app = new App();
        app.login();
        app.initGoodsData();

    }

    public void login() {
        //加载二维码
        scanCode();
        checkQr();
        qrTicketValidation();
    }

    /**
     * 扫描二维码登陆
     */
    private static void scanCode() {
        BufferedImage image = Http.getResponse(StrUtil.format(Commons.QR_URL,System.currentTimeMillis())).getBufferedImage();
        //输出文件
        File file = new File(Commons.USER_PATH + "/images/123.jpg");
        try {
            ImageIO.write(image, "JPG", file);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * 检测二维码扫码
     */
    private void checkQr() {
        Map<String, String> map = new HashMap<>();
        map.put("Host", "qr.m.jd.com");
        map.put("Referer", "https://passport.jd.com/new/login.aspx");
        Cookie cookie = Commons.getCookie("wlfstk_smdl");
        long start = System.currentTimeMillis();
        for (; ; ) {
            if (System.currentTimeMillis() - start > 60000) {
                log.info("扫码超时，请重新开始");
                System.exit(0);
            }
            Response response = Http.getResponse(StrUtil.format(Commons.QR_CHECK_URL,System.currentTimeMillis(),
                    cookie.getValue(),System.currentTimeMillis()), null, map);
            JSONObject res = parseJSONPtoMap(response.getBody());
            if (res != null && res.get("code").equals(200)) {
                //扫码成功后调用
                Commons.config.setTicket(res.get("ticket").toString());
                break;
            }
            log.info("请打开图片扫码...");
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static JSONObject parseJSONPtoMap(String jsonp) {
        int startIndex = jsonp.indexOf("(");
        int endIndex = jsonp.lastIndexOf(")");
        String json = jsonp.substring(startIndex + 1, endIndex);
        return JSONUtil.parseObj(json);
    }

    /**
     * 验证二维码
     */
    private void qrTicketValidation() {
        Map<String, String> map = new HashMap<>();
        map.put("Host", "passport.jd.com");
        map.put("Referer", "https://passport.jd.com/uc/login?ltype=logout");
        Http.getResponse(StrUtil.format(Commons.QR_TICKET_VALIDATION_URL
                , Commons.config.getTicket()), null, map);
        Commons.config.setIsLogin(true);
        Commons.config.setCookieTime(DateUtil.formatDateTime(new Date()));
    }




    /**
     * 初始化商品到购物车
     */
    private void initGoodsData() {
        try {
            String goodsListString = Commons.config.getSkuids();
            String[] goodsList = goodsListString.split(",");
            for (String item : goodsList) {
                String[] goodsAndNum = item.split(":");
                Goods goods = new Goods();
                goods.setSku(goodsAndNum[0]);
                if (goodsAndNum.length > 1) {
                    goods.setNum(Integer.parseInt(goodsAndNum[1]));
                } else {
                    goods.setNum(1);
                }
                Response response = Http.getResponse(StrUtil.format(Commons.GOODS_URL, goods.getSku()));
                if (null == response || !response.getStatusCode().equals(HttpStatus.SC_OK)) {
                    Commons.goodsMap.remove(goods.getSku());
                    log.info("总{}，sku:{}「{}」", goodsList.length, goods.getSku(), "获取商品超时");
                    return;
                }
                String body = response.getBody();
                Matcher goodsNameMatcher = Commons.GOODS_NAME_PATTERN.matcher(body);
                if (goodsNameMatcher.find()) {
                    goods.setName(UnicodeUtil.toString(goodsNameMatcher.group(1)));
                }
                // 已下架的商品移除
                Matcher takeOffPattern = Commons.TAKEOFF_PATTERN.matcher(body);
                if (takeOffPattern.find()) {
                    Commons.goodsMap.remove(goods.getSku());
                    log.info("总{}，监控{}，放弃{}，sku:{}「{}」", goodsList.length, goods.getSku(), goods.getName());
                    return;
                }
                //供应商
                Matcher venderIdMatcher = Commons.VENDERID_PATTERN.matcher(body);
                if (venderIdMatcher.find()) {
                    goods.setVenderId(venderIdMatcher.group(1));
                }
                Matcher catMatcher = Commons.CAT_PATTERN.matcher(body);
                if (catMatcher.find()) {
                    goods.setCat(catMatcher.group(1));
                }
                log.info("总{}，监控{}，放弃{}，sku:{}「{}」", goodsList.length, goods.getSku(), goods.getName());
                Commons.goodsMap.put(goods.getSku(), goods);
            }

            log.info("商品数据检测完成，总{}，监控{}，放弃{}", goodsList.length);

            log.info("开始添加商品到购物车，总{}", Commons.goodsMap.size());
            // 加入购物车
            Iterator<Map.Entry<String, Goods>> iterator = Commons.goodsMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Goods> entry = iterator.next();
                if (null == entry.getValue().getInCart() || !entry.getValue().getInCart()) {
                    if (!addCart(entry.getValue())) {
                        // 商品不能加入购物车，删除
                        iterator.remove();
                    }
                }
            }

            double limit = 90d;
            int goodsCount = Commons.goodsMap.size();
            if (goodsCount < 1) {
                return;
            }
            List<String> array = new ArrayList(Commons.goodsMap.keySet());
            StringBuilder stringBuilder = new StringBuilder();
            for (String string : array) {
                stringBuilder.append(string);
                stringBuilder.append(",");
            }
            String url = StrUtil.format(Commons.STOCKS_URL,
                    Commons.config.getArea(),
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    stringBuilder.toString());

            Response response_s = Http.getResponse(url);
            if (null != response_s) {
                JSONObject json = parseJSONPtoMap(response_s.getBody());
                for (String sku : json.keySet()) {
                    if (Commons.STOCK_STATE_PATTERN.matcher(json.get(sku).toString()).find()) {
                        continue;
                    }
                    Goods goods = Commons.goodsMap.get(sku);
                    log.info("开始下单购买，sku:{}「{}」", goods.getSku(), goods.getName());
                    submit(goods);
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }


    /**
     * 加入购物车
     *
     * @param goods
     * @return
     */
    private synchronized boolean addCart(Goods goods) {

        Response response = Http.getResponse(StrUtil.format(Commons.ADD_CART_URL,
                System.currentTimeMillis(), goods.getSku(), System.currentTimeMillis()));
        try {
            TimeUnit.MILLISECONDS.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Matcher matcher = Commons.CART_SUCCESS_PATTERN.matcher(response.getBody());
        if (matcher.find()) {
            log.info("加入购物车成功，sku:{}「{}」", goods.getSku(), goods.getName());
            return true;
        } else {
            log.info("加入购物车失败，sku:{}，「{}」", goods.getSku(), goods.getName());
            log.info(response.toString());
        }
        return false;
    }

    /**
     * 购买，提交订单
     *
     * @param goods
     */
    private boolean submit(Goods goods) {
        Map<String, String> map = new HashMap<>();
        map.put("overseaPurchaseCookies", "");
        map.put("vendorRemarks", "[]");
        map.put("submitOrderParam.sopNotPutInvoice", "false");
        map.put("submitOrderParam.trackID", "TestTrackId");
        map.put("submitOrderParam.ignorePriceChange", "0");
        map.put("submitOrderParam.btSupport", "0");
        map.put("submitOrderParam.eid", Commons.config.getOrderParam().getEid());
        map.put("submitOrderParam.fp", Commons.config.getOrderParam().getFp());
        map.put("riskControl", Commons.config.getOrderParam().getRiskControl());
        map.put("submitOrderParam.isBestCoupon", "1");
        map.put("submitOrderParam.jxj", "1");
        map.put("submitOrderParam.trackId", Commons.config.getOrderParam().getTrackId());

        Map<String, String> header = new HashMap<>();
        header.put("Host", "trade.jd.com");
        header.put("Referer", "http://trade.jd.com/shopping/order/getOrderInfo.action");

        Response response = Http.getResponse(Commons.SUBMIT_URL, map, header);
        if (Commons.SUCCESS_PATTERN.matcher(response.getBody()).find()) {
            log.info("已经下单成功,请去支付");
            return true;
        } else {
            JSONObject jsonObject = JSONUtil.parseObj(response.getBody());
            log.info("下单失败，失败原因{}，{}「{}」",jsonObject.get("message") ,goods.getSku(), goods.getName());
            log.info(response.toString());
        }
        return false;
    }
}
