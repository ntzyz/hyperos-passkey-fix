package com.example.hyperospasskey;

import android.os.RemoteException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Runs inside Shizuku's shell/root app_process. This must extend the AIDL Stub directly;
 * it is not an Android framework Service and is instantiated by Shizuku via reflection.
 */
public final class SettingsUserService extends ISettingsService.Stub {

    public SettingsUserService() {
        // A public no-argument constructor is required by Shizuku.
    }

    @Override
    public void destroy() {
        System.exit(0);
    }

    @Override
    public String setCredentialService(String componentName) throws RemoteException {
            if (!isValidComponent(componentName)) {
                return "失败：组件名格式无效。应为 包名/.类名 或 包名/完整类名。";
            }

            CommandResult first = run("settings", "put", "secure", "credential_service", componentName);
            if (first.exitCode != 0) return first.describe("credential_service");

            CommandResult second = run("settings", "put", "secure", "credential_service_primary", componentName);
            if (second.exitCode != 0) return second.describe("credential_service_primary");

            return "已写入两个 secure 设置项：\n"
                    + "credential_service=" + componentName + "\n"
                    + "credential_service_primary=" + componentName;
    }

    @Override
    public String readCredentialService() throws RemoteException {
        CommandResult normal = run("settings", "get", "secure", "credential_service");
        CommandResult primary = run("settings", "get", "secure", "credential_service_primary");
        if (normal.exitCode != 0) return normal.describe("读取 credential_service");
        if (primary.exitCode != 0) return primary.describe("读取 credential_service_primary");
        return "credential_service=" + normal.output.trim()
                + "\ncredential_service_primary=" + primary.output.trim();
    }

    private static boolean isValidComponent(String value) {
        return value != null && value.matches("[A-Za-z0-9_.]+/(?:\\.[A-Za-z0-9_.$]+|[A-Za-z0-9_.$]+)");
    }

    private static CommandResult run(String... command) {
        try {
            Process process = new ProcessBuilder(command).start();
            String stdout = readAll(process.getInputStream());
            String stderr = readAll(process.getErrorStream());
            int exitCode = process.waitFor();
            return new CommandResult(exitCode, stdout, stderr);
        } catch (IOException e) {
            return new CommandResult(-1, "", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandResult(-1, "", "命令被中断");
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line).append('\n');
        }
        return result.toString();
    }

    private static final class CommandResult {
        final int exitCode;
        final String output;
        final String error;

        CommandResult(int exitCode, String output, String error) {
            this.exitCode = exitCode;
            this.output = output;
            this.error = error;
        }

        String describe(String operation) {
            String details = error.isEmpty() ? output : error;
            return "失败（" + operation + "，退出码 " + exitCode + "）：\n" + details.trim();
        }
    }
}
