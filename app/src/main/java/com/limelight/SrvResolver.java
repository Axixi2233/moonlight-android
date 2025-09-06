package com.limelight;

import android.os.Handler;
import android.text.TextUtils;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class SrvResolver {

    public static class ResultCode{
        private int code;
        private String result;

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }
    }


    // SRV 查询入口
    public static ResultCode resolveSRVRecord(String domain) {
        String srvQuery = "_port._tcp." + domain;
        String url = "https://1.1.1.1/dns-query?name=" + srvQuery + "&type=SRV";
        ResultCode resultCode=new ResultCode();
        try {
            disableSSLVerification();
            // 发送 HTTP GET 请求
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/dns-json");
            connection.setConnectTimeout(10000); // 10秒
            connection.setReadTimeout(10000);
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                // 解析响应
                StringBuilder responseBuilder = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBuilder.append(line);
                    }
                }
                // 处理 SRV 记录
                String response = responseBuilder.toString();
                JSONObject jsonResponse = new JSONObject(response);
                if (jsonResponse.has("Answer")) {
                    JSONArray answers = jsonResponse.getJSONArray("Answer");
                    String result=processSRVRecords(answers, domain);
                    resultCode.setCode(0);
                    resultCode.setResult(result);
                } else {
                    resultCode.setCode(1);
                    resultCode.setResult("未找到 SRV 记录: " + srvQuery);
                }
            } else {
                resultCode.setCode(1);
                resultCode.setResult("解析 SRV 记录失败，HTTP 状态码: " + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultCode;
    }

    // 处理 SRV 记录
    private static String processSRVRecords(JSONArray answers, String domain) throws JSONException {
        for (int i = 0; i < answers.length(); i++) {
            JSONObject record = answers.getJSONObject(i);
            String data = record.getString("data");
            String name=record.getString("name");
            //{"nameValuePairs":{"name":"_port._tcp.wtb.plus","type":33,"TTL":600,"data":"0 6 17110 wtb.plus"}}
            LimeLog.info("processSRVRecords:"+new Gson().toJson(record));
            if(TextUtils.isEmpty(name)||!name.contains("_limelightax._tcp")){
                continue;
            }
            // 解析 SRV 数据 (优先级、权重、端口、目标域名)
            String[] srvParts = data.split("\\s+");
            if (srvParts.length >= 4) {
                String priority = srvParts[0];
                String weight = srvParts[1];
                String port = srvParts[2];
                String target = srvParts[3];
                return target+":"+port;
            }
        }
        return null;
    }

    public static void disableSSLVerification() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

