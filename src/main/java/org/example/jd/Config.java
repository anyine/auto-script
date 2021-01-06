package org.example.jd;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.http.impl.client.BasicCookieStore;

import java.io.Serializable;

@AllArgsConstructor
@Data
@NoArgsConstructor
public class Config implements Serializable {
    private BasicCookieStore basicCookieStore = new BasicCookieStore();
    private OrderParam orderParam;
    private String area;
    private String ticket;
    private String cookieTime;
    private Boolean isLogin;
    private String skuids;
}
