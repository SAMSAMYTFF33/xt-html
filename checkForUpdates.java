package com.yourpackage.name;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class GitHubUpdateChecker {
	
	private static final String TAG = "GitHubUpdateChecker";
	private static final String UPDATE_JSON_URL = "https://raw.githubusercontent.com/SAMSAMYTFF33/UPDATEFILE/main/update.json";
	
	public interface UpdateCallback {
		void onProgress(int progress);
		void onUpdateFound();
		void onNoUpdate();
		void onError(String error);
	}
	
	public static void checkForUpdates(Context context, File appFilesDir, UpdateCallback callback) {
		new Thread(() -> {
			try {
				// 1. الاتصال بـ GitHub وجلب ملف JSON
				URL url = new URL(UPDATE_JSON_URL);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("GET");
				
				if (conn.getResponseCode() != 200) {
					callback.onError("فشل الاتصال بالسيرفر: " + conn.getResponseCode());
					return;
				}
				
				BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				StringBuilder sb = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) sb.append(line);
				reader.close();
				
				JSONObject updateJson = new JSONObject(sb.toString());
				JSONArray filesArray = updateJson.getJSONArray("files");
				
				// 2. قراءة الإصدارات المحلية من ملف versions.json (الملف الذي طلبته)
				File versionsFile = new File(appFilesDir, "versions.json");
				JSONObject localVersions = new JSONObject();
				if (versionsFile.exists()) {
					BufferedReader vr = new BufferedReader(new FileReader(versionsFile));
					StringBuilder vrsb = new StringBuilder();
					while ((line = vr.readLine()) != null) vrsb.append(line);
					vr.close();
					localVersions = new JSONObject(vrsb.toString());
				}
				
				boolean anyFileUpdated = false; // متغير للتأكد هل حدث تغيير فعلي
				int totalFiles = filesArray.length();
				
				for (int i = 0; i < totalFiles; i++) {
					JSONObject fileObj = filesArray.getJSONObject(i);
					String fileName = fileObj.getString("file_name");
					int latestVersion = fileObj.getInt("latest_version_code");
					String htmlUrl = fileObj.getString("html_url");
					
					int localVersion = localVersions.optInt(fileName, 0);
					
					// 3. المقارنة: التحديث يتم فقط إذا كان إصدار GitHub أكبر من المحلي
					if (latestVersion > localVersion) {
						Log.d(TAG, "تحديث وجد للملف: " + fileName);
						if (downloadFile(htmlUrl, new File(appFilesDir, fileName))) {
							localVersions.put(fileName, latestVersion);
							anyFileUpdated = true;
						}
					}
					
					int progressPercent = (int) (((i + 1) * 100.0f) / totalFiles);
					callback.onProgress(progressPercent);
				}
				
				// 4. حفظ أرقام الإصدارات الجديدة في الملف المحلي
				if (anyFileUpdated) {
					try (FileOutputStream fos = new FileOutputStream(versionsFile)) {
						fos.write(localVersions.toString().getBytes());
					}
					callback.onUpdateFound(); // سيظهر "تم التحديث بنجاح"
					} else {
					callback.onNoUpdate(); // سيظهر "لا توجد تحديثات"
				}
				
				} catch (Exception e) {
				Log.e(TAG, "خطأ أثناء التحديث", e);
				callback.onError(e.getMessage());
			}
		}).start();
	}
	
	private static boolean downloadFile(String fileUrl, File destFile) {
		try {
			URL url = new URL(fileUrl);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			if (conn.getResponseCode() != 200) return false;
			
			InputStream is = conn.getInputStream();
			FileOutputStream fos = new FileOutputStream(destFile);
			byte[] buffer = new byte[4096];
			int len;
			while ((len = is.read(buffer)) != -1) {
				fos.write(buffer, 0, len);
			}
			fos.close();
			is.close();
			return true;
			} catch (Exception e) {
			return false;
		}
	}
	
}