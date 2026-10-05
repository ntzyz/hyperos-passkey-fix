package com.example.hyperospasskey;

interface ISettingsService {
    /** Writes both HyperOS credential service keys and returns the combined command output. */
    String setCredentialService(String componentName) = 1;

    /** Reads both HyperOS credential service keys and returns a human-readable result. */
    String readCredentialService() = 2;

    /** Reserved Shizuku transaction; used to stop the remote app_process cleanly. */
    void destroy() = 16777114;
}
