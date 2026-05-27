package com.yourapp.gradedstock;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpHelper {

    public static String get(String urlString) throws Exception{

        URL url=new URL(urlString);

        HttpURLConnection conn=(HttpURLConnection)url.openConnection();

        conn.setRequestMethod("GET");

        InputStream in=conn.getInputStream();

        StringBuilder sb=new StringBuilder();

        int ch;

        while((ch=in.read())!=-1)
            sb.append((char)ch);

        in.close();

        return sb.toString();
    }

    public static String postJson(String urlString,String json) throws Exception{

        URL url=new URL(urlString);

        HttpURLConnection conn=(HttpURLConnection)url.openConnection();

        conn.setRequestMethod("POST");

        conn.setDoOutput(true);

        conn.setRequestProperty("Content-Type","application/json");

        OutputStream os=conn.getOutputStream();

        os.write(json.getBytes());

        os.close();

        InputStream in=conn.getInputStream();

        StringBuilder sb=new StringBuilder();

        int ch;

        while((ch=in.read())!=-1)
            sb.append((char)ch);

        in.close();

        return sb.toString();
    }
}