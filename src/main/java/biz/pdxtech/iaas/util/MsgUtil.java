/*************************************************************************
 * Copyright (C) 2016-2019 PDX Technologies, Inc. All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *************************************************************************/

package biz.pdxtech.iaas.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

public class MsgUtil {
    private static Integer x_eid = 12826;
    private static String x_uid = "pax_2018";
    private static String x_pwd_md5 = "F774EEB53ECEFD4D031F8FA2D346C612";//F774EEB53ECEFD4D031F8FA2D346C612
    private static Integer x_gate_id = 300;

    public static String SendSms(String mobile, String content) throws UnsupportedEncodingException {
        Integer x_ac = 10;//发送信息
        HttpURLConnection httpconn = null;
        String result = "-20";
        String memo = content.length() < 70 ? content.trim() : content.trim().substring(0, 70);
        StringBuilder sb = new StringBuilder();
        sb.append("http://gateway.woxp.cn:6630/utf8/web_api/?x_eid=");
        sb.append(x_eid);
        sb.append("&x_uid=").append(x_uid);
        sb.append("&x_pwd_md5=").append(x_pwd_md5);
        sb.append("&x_ac=").append(x_ac);
        sb.append("&x_gate_id=").append(x_gate_id);
        sb.append("&x_target_no=").append(mobile);
        sb.append("&x_memo=").append(URLEncoder.encode(memo, "utf-8"));
        try {
            URL url = new URL(sb.toString());
            httpconn = (HttpURLConnection) url.openConnection();
            BufferedReader rd = new BufferedReader(new InputStreamReader(httpconn.getInputStream()));
            result = rd.readLine();
            rd.close();
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            if (httpconn != null) {
                httpconn.disconnect();
                httpconn = null;
            }
        }
        return result;
    }

    public static String SplicSms(String content, String... value) {

        for (int i = 0; i < value.length; i++) {

            content = content.replace("{" + (i + 1) + "}", value[i]);
        }

        return content;
    }

    public static void main(String[] args) {
        System.out.println(SplicSms("测试{1}一下", "hello"));
    }
}
