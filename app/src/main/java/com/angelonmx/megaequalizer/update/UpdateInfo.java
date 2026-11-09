package com.angelonmx.megaequalizer.update;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Parsed version.json from the latest GitHub release. */
public final class UpdateInfo {

    public static final class Asset {
        public final String name;
        public final String url;
        public final String sha256;

        public Asset(String name, String url, String sha256) {
            this.name = name;
            this.url = url;
            this.sha256 = sha256;
        }
    }

    public final int versionCode;
    public final String versionName;
    public final String notes;
    public final Map<String, Asset> assets;

    private UpdateInfo(int versionCode, String versionName, String notes, Map<String, Asset> assets) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.notes = notes;
        this.assets = assets;
    }

    public static UpdateInfo fromJson(JSONObject object) {
        int code = object.optInt("versionCode", 0);
        String name = object.optString("versionName", "");
        String notes = object.optString("notes", "");
        Map<String, Asset> assets = new LinkedHashMap<>();
        JSONObject assetObject = object.optJSONObject("assets");
        if (assetObject != null) {
            java.util.Iterator<String> keys = assetObject.keys();
            while (keys.hasNext()) {
                String abi = keys.next();
                JSONObject asset = assetObject.optJSONObject(abi);
                if (asset == null) {
                    continue;
                }
                String assetName = asset.optString("name", "");
                String url = asset.optString("url", "");
                String sha = asset.optString("sha256", "");
                assets.put(abi, new Asset(assetName, url, sha));
            }
        }
        return new UpdateInfo(code, name, notes, assets);
    }
}
