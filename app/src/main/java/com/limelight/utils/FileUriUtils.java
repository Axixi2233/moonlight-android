package com.limelight.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.FileUtils;
import android.support.annotation.RequiresApi;
import android.support.v4.content.FileProvider;
import android.webkit.MimeTypeMap;

import com.google.gson.Gson;
import com.limelight.BuildConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;

/**
 * Description
 * Date: 2024-03-20
 * Time: 13:46
 */
public class FileUriUtils {

    //获取json文件内容
    public static String getKeyBoardJson(Context context,String name){
        File dataBaseFile=new File(context.getFilesDir().getAbsolutePath()+File.separator, name);
        String authority= context.getPackageName()+".fileprovider";
        Uri uri= FileProvider.getUriForFile(context,authority,dataBaseFile);
        return FileUriUtils.openUriForRead(context,uri);
    }

    public static Uri getKeyBoardFile(Context context,String name){
        File dataBaseFile=new File(context.getFilesDir().getAbsolutePath(), name);
        String authority= context.getPackageName()+".fileprovider";
        return FileProvider.getUriForFile(context,authority,dataBaseFile);
    }

    //保存json数据
    public static boolean saveKeyBoardJson(Context context,String name,String json){
        File file1=new File(context.getFilesDir().getAbsolutePath(), name);
        return FileUriUtils.writerFileString(file1,json);
    }

    public static String openUriForRead(Context context, Uri uri) {
        if (uri == null)
            return "";
        InputStream inputStream = null;
        Reader reader = null;
        BufferedReader bufferedReader = null;
        StringBuilder result = new StringBuilder();
        try {
            inputStream = context.getContentResolver().openInputStream(uri);
            reader = new InputStreamReader(inputStream);
            bufferedReader = new BufferedReader(reader);
            String temp;
            while ((temp = bufferedReader.readLine()) != null) {
                result.append(temp);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result.toString();
    }

    public static boolean openUriForWrite(Context context, Uri uri, String content) {
        if (uri == null) {
            return false;
        }

        try {
            //从uri构造输出流
            OutputStream outputStream = context.getContentResolver().openOutputStream(uri);
            //写入文件
            outputStream.write(content.getBytes());
            outputStream.flush();
            outputStream.close();
            return true;
        } catch (Exception e) {
            e.getLocalizedMessage();
        }
        return false;
    }

    public static boolean writerFileString(File file, String content) {
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(content.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static File uriToFileApiQ(Uri uri, Context context) {
        File file = null;
        if (uri == null) return file;
        //android10以上转换
        if (uri.getScheme().equals(ContentResolver.SCHEME_FILE)) {
            file = new File(uri.getPath());
        } else if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
            //把文件复制到沙盒目录
            ContentResolver contentResolver = context.getContentResolver();
            String displayName = System.currentTimeMillis() + Math.round((Math.random() + 1) * 1000)
                    + "." + MimeTypeMap.getSingleton().getExtensionFromMimeType(contentResolver.getType(uri));
            try {
                InputStream is = contentResolver.openInputStream(uri);
                File cache = new File(context.getCacheDir().getAbsolutePath(), displayName);
                FileOutputStream fos = new FileOutputStream(cache);
                FileUtils.copy(is, fos);
                file = cache;
                fos.close();
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return file;
    }

    public static void copyUriToInternalStorage(Context context, Uri uri, File destFile) {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            // 从Uri获取输入流
            inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                return;
            }

            // 创建目标文件
            outputStream = new FileOutputStream(destFile);

            // 缓冲区大小
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            outputStream.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 关闭输入流和输出流
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
