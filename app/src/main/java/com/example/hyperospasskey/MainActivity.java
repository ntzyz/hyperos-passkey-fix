package com.example.hyperospasskey;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public final class MainActivity extends AppCompatActivity {
    private static final int SHIZUKU_PERMISSION_REQUEST_CODE = 1001;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ISettingsService settingsService;
    private EditText componentInput;
    private TextView status;
    private Button applyButton;
    private Button readButton;

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = this::updateShizukuState;
    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        settingsService = null;
        updateShizukuState();
    };
    private final Shizuku.OnRequestPermissionResultListener permissionListener = (requestCode, grantResult) -> {
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) updateShizukuState();
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            settingsService = ISettingsService.Stub.asInterface(service);
            status.setText("Shizuku 已连接，可以写入设置。");
            updateControls();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            settingsService = null;
            updateShizukuState();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        applySystemBarInsets(findViewById(R.id.root));
        componentInput = findViewById(R.id.component_input);
        status = findViewById(R.id.status);
        applyButton = findViewById(R.id.apply_button);
        readButton = findViewById(R.id.read_button);

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionListener);

        findViewById(R.id.grant_button).setOnClickListener(v -> requestShizukuPermission());
        applyButton.setOnClickListener(v -> writeSettings());
        readButton.setOnClickListener(v -> readSettings());
        updateShizukuState();
    }

    /** Keeps the screen usable with display cutouts, gesture navigation, and Android 15 edge-to-edge. */
    private void applySystemBarInsets(View root) {
        final int initialLeft = root.getPaddingLeft();
        final int initialTop = root.getPaddingTop();
        final int initialRight = root.getPaddingRight();
        final int initialBottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(
                    initialLeft + insets.left,
                    initialTop + insets.top,
                    initialRight + insets.right,
                    initialBottom + insets.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void requestShizukuPermission() {
        if (!Shizuku.pingBinder()) {
            status.setText("未检测到 Shizuku。请先启动 Shizuku 服务后重试。");
            return;
        }
        if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            bindSettingsService();
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            status.setText("Shizuku 已拒绝本应用的授权。请在 Shizuku 管理器中允许本应用。");
        } else {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE);
        }
    }

    private void updateShizukuState() {
        if (!Shizuku.pingBinder()) {
            status.setText("等待 Shizuku 服务…");
            updateControls();
            return;
        }
        if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            bindSettingsService();
        } else {
            status.setText("Shizuku 已运行，但尚未授权本应用。");
            updateControls();
        }
    }

    private void bindSettingsService() {
        if (settingsService != null) {
            updateControls();
            return;
        }
        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                new ComponentName(this, SettingsUserService.class))
                .daemon(false)
                .tag("hyperos-passkey-settings")
                // Version bump replaces the old failed UserService record on existing installs.
                .version(2)
                .processNameSuffix("passkey-settings");
        Shizuku.bindUserService(args, serviceConnection);
        status.setText("正在连接 Shizuku…");
    }

    private void writeSettings() {
        String component = componentInput.getText().toString().trim();
        runRemote("正在写入…", service -> service.setCredentialService(component));
    }

    private void readSettings() {
        runRemote("正在读取…", ISettingsService::readCredentialService);
    }

    private void runRemote(String workingText, RemoteAction action) {
        if (settingsService == null) {
            status.setText("Shizuku 服务尚未连接，请先授权并稍候重试。");
            return;
        }
        status.setText(workingText);
        applyButton.setEnabled(false);
        readButton.setEnabled(false);
        worker.execute(() -> {
            try {
                String result = action.run(settingsService);
                runOnUiThread(() -> status.setText(result));
            } catch (RemoteException e) {
                runOnUiThread(() -> status.setText("Shizuku 服务调用失败：" + e.getMessage()));
            } finally {
                runOnUiThread(this::updateControls);
            }
        });
    }

    private void updateControls() {
        boolean ready = settingsService != null;
        applyButton.setEnabled(ready);
        readButton.setEnabled(ready);
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        worker.shutdownNow();
        super.onDestroy();
    }

    private interface RemoteAction {
        String run(@NonNull ISettingsService service) throws RemoteException;
    }
}
