package com.limelight;

import android.os.Build;
import android.os.Bundle;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.limelight.ui.CreditsWallView;
import com.limelight.utils.UpdateChecker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class CreditsActivity extends BaseActivity {

    private static final String SPONSORED_CONFIG_URL = "https://axixi2233.github.io/res/config/sponsored.json";

    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final Gson GSON = new Gson();

    private static final CreditEntry[] CREDIT_ENTRIES = new CreditEntry[] {
            new CreditEntry("鹿***路", "https://i1.hdslb.com/bfs/face/f05e1dba1d95daa97da6c72cc0f56b21d11a65ce.jpg@128w_1o.webp"),
            new CreditEntry("路***类", "https://i1.hdslb.com/bfs/face/7f341960c6fb723a47d757b536793b5e07b5bb74.jpg@128w_1o.webp"),
            new CreditEntry("千***s", "https://i1.hdslb.com/bfs/face/a8946cc2028951bcc95a0707af554e7665119721.jpg@128w_1o.webp"),
            new CreditEntry("日***a", "https://i2.hdslb.com/bfs/face/050432c154105ff8bd337cb0bda2800821896018.jpg@128w_1o.webp"),
            new CreditEntry("o***o", "https://i1.hdslb.com/bfs/face/bd3d76161113ef0f7d91bc08d395ab49f0e83fba.jpg@128w_1o.webp"),
            new CreditEntry("朝***阳", "https://i2.hdslb.com/bfs/face/4e8cb5cc62296540f5c0de3c04427f802339e6f3.jpg@128w_1o.webp"),
            new CreditEntry("N***Q", "https://i2.hdslb.com/bfs/face/fd404932519cc91c2c1dfca5bdd7c4553d2584dd.jpg@128w_1o.webp"),
            new CreditEntry("动***_", "https://i1.hdslb.com/bfs/face/bf84fb0c25f2ceb5f770fffe60fc770d9b6b5c07.jpg@128w_1o.webp"),
            new CreditEntry("8***i", "https://i1.hdslb.com/bfs/face/member/noface.jpg@128w_1o.webp"),
            new CreditEntry("在***象", "https://i1.hdslb.com/bfs/face/51bd87b452ea333c77404f938b4df5683f155159.jpg@128w_1o.webp"),
            new CreditEntry("五***紫", "https://i1.hdslb.com/bfs/face/1b57cea01ebeb44fb6d42d2c9dc19b854d1a201d.jpg@128w_1o.webp"),
            new CreditEntry("B***n", "https://i2.hdslb.com/bfs/face/00a4ec4953b88a286cb669117a576a0d5d7d045f.jpg@128w_1o.webp"),
            new CreditEntry("小***h", "https://i1.hdslb.com/bfs/face/d90c49d70a677ad917f1321d8f246f52cf0ed169.jpg@128w_1o.webp"),
            new CreditEntry("林***头", "https://i2.hdslb.com/bfs/face/6b4b0e94556c550da0288bb960d8f984e623e1bd.jpg@128w_1o.webp"),
            new CreditEntry("羡***9", "https://i1.hdslb.com/bfs/face/dc074b0c9359dd768f0e5d75e37e52b05fbff9ba.jpg@128w_1o.webp"),
            new CreditEntry("四***堂", "https://i2.hdslb.com/bfs/face/7cda2e85849a2b8d7530bc5ab7b3a4a563dc9e9c.jpg@128w_1o.webp"),
            new CreditEntry("夜***w", "https://i2.hdslb.com/bfs/face/3e410aa4b25ef776829a5c23aab3527b0ad78fdc.jpg@128w_1o.webp"),
            new CreditEntry("耀***技", "https://i2.hdslb.com/bfs/face/a4e0aef2add824a2a1934f06e13f1abfa353da62.jpg@128w_1o.webp"),
            new CreditEntry("荔***眼", "https://i2.hdslb.com/bfs/face/0b6babb147005ff69b88978a8dc600239cab8c7d.jpg@128w_1o.webp"),
            new CreditEntry("M***h", "https://i2.hdslb.com/bfs/face/e4d6b12e0d2daaeb5f130dcd25653fe6cd5f4df1.jpg@128w_1o.webp"),
            new CreditEntry("熬***拜", "https://i0.hdslb.com/bfs/face/b60728d1fa7e7ab8ddeccde607869eace07b7213.jpg@128w_1o.webp"),
            new CreditEntry("残***_", "https://i1.hdslb.com/bfs/face/497980bd873c97114991ca86f1fbd3b58af3ffae.webp@128w_1o.webp"),
            new CreditEntry("白***夜", "https://i2.hdslb.com/bfs/garb/d515dae16548221240649035be81452f50ebdab2.png@128w_1o.webp"),
            new CreditEntry("Y***g", "https://i1.hdslb.com/bfs/face/8873a43112d62e52a769d8ac97f1e0d65634b93e.jpg@128w_1o.webp"),
            new CreditEntry("镜***h", "https://i1.hdslb.com/bfs/face/65f5e7c8f605b149ed5814151c0fc71e251f3ff2.jpg@128w_1o.webp"),
            new CreditEntry("小***巴", "https://i1.hdslb.com/bfs/face/3c29568c4ab0d73cdb5f0b2818cec8d1017e23e8.jpg@128w_1o.webp"),
            new CreditEntry("芋***端", "https://i1.hdslb.com/bfs/face/195301322cd0928fb30cc387f71b0241c32396a7.jpg@128w_1o.webp"),
            new CreditEntry("A***n", "https://i2.hdslb.com/bfs/face/18f86f14bbc1dc81bb135ceec941c21d072ddd86.jpg@128w_1o.webp"),
            new CreditEntry("h***o", "https://i2.hdslb.com/bfs/face/85eb6fb9ab9419505318072dd144613267a374dc.jpg@128w_1o.webp"),
            new CreditEntry("S***e", "https://i1.hdslb.com/bfs/face/6116a47544bb744b9f206ffc5ed090a98b50d351.jpg@128w_1o.webp"),
            new CreditEntry("冰***寒", "https://i1.hdslb.com/bfs/face/1204102a86a5eac380fe80df7509c3b46c0228fc.jpg@128w_1o.webp"),
            new CreditEntry("让***白", "https://i2.hdslb.com/bfs/baselabs/b3283b94df495648262ce268bcaf4c7853efc7ea.png@128w_1o.webp"),
            new CreditEntry("好***丶", "https://i1.hdslb.com/bfs/baselabs/b7314b28919a1af9bd162eaf1dc35a52ad9f7ab9.png@128w_1o.webp"),
            new CreditEntry("A***_", "https://i1.hdslb.com/bfs/face/338d06cf47fed7e0eb5fb2b8d030dc3fb6284aaa.jpg@128w_1o.webp"),
            new CreditEntry("z***e", "https://i1.hdslb.com/bfs/face/member/noface.jpg@128w_1o.webp"),
            new CreditEntry("I***e", "https://i1.hdslb.com/bfs/face/1c6d43f16d24fea7b153dc8817e61a09a5a33da2.jpg@128w_1o.webp"),
            new CreditEntry("S***9", "https://i2.hdslb.com/bfs/face/4c93fcca0e126b7f10fdd2da31041ee128ebc9c3.jpg@128w_1o.webp"),
            new CreditEntry("伪***己", "https://i1.hdslb.com/bfs/face/4a7e9f99214910f93a6d0dd5409a0e1dbeb9f9a3.jpg@128w_1o.webp"),
            new CreditEntry("爱***子", "https://i2.hdslb.com/bfs/face/3e80a894a385dcad81d7632a8b3a1ea82298846b.jpg@128w_1o.webp"),
            new CreditEntry("兰***音", "https://i1.hdslb.com/bfs/face/1360c01280d74553f3ea1b880dfb3205620d967c.jpg@128w_1o.webp"),
            new CreditEntry("微***安", "https://i1.hdslb.com/bfs/face/15d89c4b5434d44fb04ffe9295b1f252e6ceb056.jpg@128w_1o.webp"),
            new CreditEntry("小***奇", "https://i2.hdslb.com/bfs/face/d3cc916a9ec1aa3104fb3e0788938ca09b9bc252.jpg@128w_1o.webp"),
            new CreditEntry("嚯***y", "https://i1.hdslb.com/bfs/face/282bfbdf22a90516b0c682b813afa85749bf6765.webp@128w_1o.webp"),
            new CreditEntry("N***g", "https://i1.hdslb.com/bfs/face/8d88010b828359cad543f88d5058877446382e37.jpg@128w_1o.webp"),
            new CreditEntry("大***N", "https://i2.hdslb.com/bfs/face/38a1a7cd4b069b9aeb385d567324c5b04cb359af.jpg@128w_1o.webp"),
            new CreditEntry("H***i", "https://i2.hdslb.com/bfs/face/9e2e2e0508901b165d72dc5fc8add157f64f1414.jpg@128w_1o.webp"),
            new CreditEntry("北***l", "https://i2.hdslb.com/bfs/face/1a0c9a18f516f63fb00c78abf63eee9a3a55a4ac.jpg@128w_1o.webp"),
            new CreditEntry("辛***喵", "https://i1.hdslb.com/bfs/face/79d51a5e3c940bb948f7fc523c9fdc4f7232997f.jpg@128w_1o.webp"),
            new CreditEntry("m***9", "https://i2.hdslb.com/bfs/face/cdd9d243e6d57e006db53c50803b18a58eb0810b.jpg@128w_1o.webp"),
            new CreditEntry("吾***丶", "https://i1.hdslb.com/bfs/face/4dc426aba20ac39cc3d5ba48b008ea33ec00383e.jpg@128w_1o.webp"),
            new CreditEntry("F***w", "https://i2.hdslb.com/bfs/face/57c2c9a1cf31e73f952ae17fe0bdb46eee6729a1.jpg@128w_1o.webp"),
            new CreditEntry("废***_", "https://i1.hdslb.com/bfs/face/b2cf4b2a08a5472d4ef7902700c9b2dfd156adf3.jpg@128w_1o.webp"),
            new CreditEntry("b***4", "https://i1.hdslb.com/bfs/face/00eca0557111094470357821a22131077f280f8f.jpg@128w_1o.webp"),
            new CreditEntry("y***y", "https://i2.hdslb.com/bfs/face/bab80a31e30a4fa977f51518428d365e6a0cea7a.jpg@128w_1o.webp"),
            new CreditEntry("D***X", "https://i1.hdslb.com/bfs/face/f0557e3779c4bcad649389b1ab6aba83a2315ed5.jpg@128w_1o.webp"),
            new CreditEntry("f***a", "https://i1.hdslb.com/bfs/face/70e112e97295eec88fec23cb85b2b575510387ef.jpg@128w_1o.webp"),
            new CreditEntry("v***叶", "https://i1.hdslb.com/bfs/face/a2b216c1be029753570f62a40bc7ac440561a2fa.jpg@128w_1o.webp"),
            new CreditEntry("海***厅", "https://i1.hdslb.com/bfs/face/ccc4a92512d9d9aa0e5137e42f8e483b7d3883ac.jpg@128w_1o.webp"),
            new CreditEntry("天***1", "https://i1.hdslb.com/bfs/face/member/noface.jpg@128w_1o.webp"),
            new CreditEntry("S***K", "https://i1.hdslb.com/bfs/face/5a5f7b1ce18b9af97c8695fd5f53c542c77ec445.jpg@128w_1o.webp"),
            new CreditEntry("知***格", "https://i1.hdslb.com/bfs/face/737927857ccaa312ef0310e9981271139f6d2085.jpg@128w_1o.webp"),
            new CreditEntry("皮***d", "https://i1.hdslb.com/bfs/baselabs/f338ee7ab9e21bddd7b21d9c7537f0e5ea47b2e1.png@128w_1o.webp"),
            new CreditEntry("6***D", "https://i1.hdslb.com/bfs/face/96e705d7e9e1d32664a03b7531fa8d9a382690ef.jpg@128w_1o.webp"),
            new CreditEntry("f***e", "https://i2.hdslb.com/bfs/face/99bba5562d5329813acbb17427db3c946f006583.jpg@128w_1o.webp"),
            new CreditEntry("叫***漾", "https://i2.hdslb.com/bfs/face/f8f7b1a9900ac98110220466f069eabc36f50ffe.jpg@128w_1o.webp"),
            new CreditEntry("崩***灵", "https://i1.hdslb.com/bfs/face/77760cf838f283dc0306917c51b5078c821ba3de.jpg@128w_1o.webp"),
            new CreditEntry("千***星", "https://i1.hdslb.com/bfs/face/e7078ee11e1e616a90bc2333af7e828b0aecd6e7.jpg@128w_1o.webp"),
            new CreditEntry("霹***子", "https://i1.hdslb.com/bfs/face/5196235351e4303e76ff844226e6f31aff1ab1f9.jpg@128w_1o.webp"),
            new CreditEntry("斯***因", "https://i1.hdslb.com/bfs/face/1dbcdccc317389f98a1d80cde8e148e53849211b.jpg@128w_1o.webp"),
            new CreditEntry("白***圆", "https://i2.hdslb.com/bfs/face/d53a43cb9378901c29d9dcb318780dc78aa03186.jpg@128w_1o.webp"),
            new CreditEntry("Q***g", "https://i2.hdslb.com/bfs/face/2ebca39be0ffefb1d04ab58372ff674440460d94.jpg@128w_1o.webp"),
            new CreditEntry("第***妖", "https://i2.hdslb.com/bfs/face/2c64144e70b1694b496de03a7fd829b69276e644.jpg@128w_1o.webp"),
            new CreditEntry("布***雨", "https://i1.hdslb.com/bfs/face/c316a6ea0b1eb949c5fbe2803c5476ec6c203f06.jpg@128w_1o.webp"),
            new CreditEntry("方***極", "https://i2.hdslb.com/bfs/face/51d893a04cf21d056b0ab1ed844591125e02154b.jpg@128w_1o.webp"),
            new CreditEntry("小***_", "https://i1.hdslb.com/bfs/face/3cc973780d572474bbc01b60f1d9f305fff8c42f.jpg@128w_1o.webp"),
            new CreditEntry("九***才", "https://i2.hdslb.com/bfs/face/33c2a71db7c5dd9161277e0e54b9b02ba0cfd99a.jpg@128w_1o.webp"),
            new CreditEntry("蒸***目", "https://i1.hdslb.com/bfs/face/b8df493e7e74e35e47904f5160b1ade43b919fbb.jpg@128w_1o.webp"),
            new CreditEntry("离***r", "https://i1.hdslb.com/bfs/face/7b3b41a63d1be92002a21474fe9fc87abb4b5898.jpg@128w_1o.webp"),
            new CreditEntry("L***w", "https://i1.hdslb.com/bfs/face/ba838603af5804365a6d1fe8d87df8d00d7c38f8.webp@128w_1o.webp"),
            new CreditEntry("艾***1", "https://i1.hdslb.com/bfs/face/member/noface.jpg@128w_1o.webp"),
            new CreditEntry("嗝***革", "https://i1.hdslb.com/bfs/face/89649589688a93d582cd4cc0c2fcce209041a594.jpg@128w_1o.webp"),
            new CreditEntry("子***0", "https://i2.hdslb.com/bfs/face/c9dd248632ef8ccfa4ea5ba0a44edec9d2fe0e27.jpg@128w_1o.webp"),
            new CreditEntry("辰***r", "https://i1.hdslb.com/bfs/face/e57aba9c8d2c16a8f0a697220b8c8b915b81957a.jpg@128w_1o.webp"),
            new CreditEntry("若***n", "https://i1.hdslb.com/bfs/face/4682acb9e29700d8bc48f5df3ee0706d33be243f.jpg@128w_1o.webp"),
            new CreditEntry("游***Z", "https://i1.hdslb.com/bfs/face/6ebee73b01e60a19fcde6a666ff35607cba15997.webp@128w_1o.webp"),
            new CreditEntry("神***六", "https://i1.hdslb.com/bfs/face/b750fac76c26fb3a541d2089cf817ebc94acf5e7.jpg@128w_1o.webp"),
            new CreditEntry("L***r", "https://i1.hdslb.com/bfs/face/2f6800e7765a2edd3e51d74b8ff4e9fd773a091b.jpg@128w_1o.webp")
    };

    private CreditsWallView creditsWallView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_credits);

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        findViewById(R.id.tx_sponsored).setOnClickListener(v ->
                UpdateChecker.openUrl(CreditsActivity.this, "https://axixi2233.github.io/credits.html"));

        creditsWallView = findViewById(R.id.credits_wall);
        loadRemoteEntries();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (creditsWallView != null) {
            creditsWallView.startAutoScroll();
        }
    }

    @Override
    protected void onPause() {
        if (creditsWallView != null) {
            creditsWallView.stopAutoScroll();
        }
        super.onPause();
    }

    private void loadRemoteEntries() {
        Request request = new Request.Builder()
                .url(SPONSORED_CONFIG_URL)
                .get()
                .build();

        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                applyCreditsEntries(buildLocalWallEntries());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response ignored = response) {
                    if (!response.isSuccessful() || response.body() == null) {
                        applyCreditsEntries(buildLocalWallEntries());
                        return;
                    }

                    List<CreditsWallView.CreditEntry> remoteEntries = parseRemoteEntries(response.body().string());
                    if (remoteEntries.isEmpty()) {
                        applyCreditsEntries(buildLocalWallEntries());
                    } else {
                        applyCreditsEntries(remoteEntries);
                    }
                } catch (Exception ignoredException) {
                    applyCreditsEntries(buildLocalWallEntries());
                }
            }
        });
    }

    private void applyCreditsEntries(List<CreditsWallView.CreditEntry> entries) {
        if (isFinishing()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed()) {
            return;
        }
        runOnUiThread(() -> {
            if (creditsWallView != null) {
                creditsWallView.setEntries(entries);
            }
        });
    }

    private List<CreditsWallView.CreditEntry> buildLocalWallEntries() {
        List<CreditsWallView.CreditEntry> wallEntries = new ArrayList<>(CREDIT_ENTRIES.length);
        for (CreditEntry entry : CREDIT_ENTRIES) {
            wallEntries.add(new CreditsWallView.CreditEntry(entry.name, entry.avatarUrl));
        }
        return wallEntries;
    }

    private List<CreditsWallView.CreditEntry> parseRemoteEntries(String json) {
        List<CreditsWallView.CreditEntry> entries = new ArrayList<>();
        JsonElement root = JsonParser.parseString(json);
        JsonArray array = findEntriesArray(root);
        if (array == null) {
            return entries;
        }

        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject object = element.getAsJsonObject();
            String name = readString(object, "name", "title", "nickName", "nickname");
            String avatarUrl = readString(object, "avatarUrl", "avatar", "avatar_url", "image", "img");

            if (isBlank(name) || isBlank(avatarUrl)) {
                continue;
            }

            entries.add(new CreditsWallView.CreditEntry(name.trim(), avatarUrl.trim()));
        }

        return entries;
    }

    private JsonArray findEntriesArray(JsonElement root) {
        if (root == null || root.isJsonNull()) {
            return null;
        }
        if (root.isJsonArray()) {
            return root.getAsJsonArray();
        }
        if (!root.isJsonObject()) {
            return null;
        }

        JsonObject object = root.getAsJsonObject();
        String[] directKeys = {"entries", "list", "data", "sponsored", "credits"};
        for (String key : directKeys) {
            JsonElement candidate = object.get(key);
            if (candidate != null && candidate.isJsonArray()) {
                return candidate.getAsJsonArray();
            }
            if (candidate != null && candidate.isJsonObject()) {
                JsonArray nestedArray = findEntriesArray(candidate);
                if (nestedArray != null) {
                    return nestedArray;
                }
            }
        }

        return null;
    }

    private String readString(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive()) {
                String text = value.getAsString();
                if (!isBlank(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    private static final class CreditEntry {
        private final String name;
        private final String avatarUrl;

        private CreditEntry(String name, String avatarUrl) {
            this.name = name;
            this.avatarUrl = avatarUrl;
        }
    }
}
