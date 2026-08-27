package dev.miniwx;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = Math.round(24 * getResources().getDisplayMetrics().density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.TOP);

        TextView title = new TextView(this);
        title.setText("MiniWx");
        title.setTextSize(28);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView body = new TextView(this);
        body.setText("0.3.0\n\n"
                + "已包含：\n"
                + "• 微信主进程注入验证\n"
                + "• DexKit 动态定位 XML 解析器\n"
                + "• 防撤回（实验性）\n"
                + "• 可选 Native/Zygisk 后端架构预留\n\n"
                + "使用：\n"
                + "1. 在 LSPosed 中启用 MiniWx。\n"
                + "2. 作用域只勾选微信。\n"
                + "3. 强制停止微信后重新打开。\n"
                + "4. 在 LSPosed 日志搜索 [MiniWx]。\n\n"
                + "注意：当前防撤回会阻止本机收到的 revokemsg 本地处理，"
                + "暂未区分‘自己主动撤回’场景，也暂不插入额外提示消息。"
        );
        body.setTextSize(16);
        body.setPadding(0, pad, 0, 0);
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }
}
