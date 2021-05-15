package com.github.steveice10.mc.protocol;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.exception.request.InvalidCredentialsException;
import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.service.AuthenticationService;
import com.github.steveice10.mc.auth.service.MojangAuthenticationService;
import com.github.steveice10.mc.auth.service.MsaAuthenticationService;

import java.net.URI;
import java.util.List;

/**
 * Automatically signs in with either Mojang or Microsoft authentication
 */
public class AutoAuthenticationService extends AuthenticationService {
    private String username;
    private String password;
    private final MojangAuthenticationService mojangAuth;
    private final MsaAuthenticationService msaAuth;
    private AuthType authType;

    /**
     * Creates an automatic wrapper for {@link MojangAuthenticationService} and {@link MsaAuthenticationService}
     *
     * @param clientToken Azure client token used for Microsoft Authentication
     */
    public AutoAuthenticationService(String clientToken) {
        super(URI.create(""));
        this.mojangAuth = new MojangAuthenticationService(clientToken);
        this.msaAuth = new MsaAuthenticationService(clientToken);
    }

    /**
     * Automatically determines which auth method was used and returns the corresponding service
     *
     * @return The auth service for the auth method used (either {@link MojangAuthenticationService} or {@link MsaAuthenticationService})
     */
    private AuthenticationService getAuth() {
        return this.authType == AuthType.Mojang ? mojangAuth : msaAuth;
    }

    @Override
    public String getAccessToken() {
        return this.getAuth().getAccessToken();
    }

    @Override
    public void setAccessToken(String accessToken) {
        this.getAuth().setAccessToken(accessToken);
    }

    @Override
    public boolean isLoggedIn() {
        return this.getAuth().isLoggedIn();
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    @Override
    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public List<GameProfile.Property> getProperties() {
        return this.getAuth().getProperties();
    }

    @Override
    public List<GameProfile> getAvailableProfiles() {
        return this.getAuth().getAvailableProfiles();
    }

    @Override
    public GameProfile getSelectedProfile() {
        return this.getAuth().getSelectedProfile();
    }

    @Override
    public void logout() throws RequestException {
        this.getAuth().logout();
    }

    @Override
    public void login() throws RequestException {
        try {
            // First attempt Mojang auth
            System.out.println("[AutoAuthService] Attempting Mojang authentication...");
            this.mojangAuth.setUsername(this.username);
            this.mojangAuth.setPassword(this.password);
            this.mojangAuth.login();
            this.authType = AuthType.Mojang;
            System.out.println("[AutoAuthService] Authenticated using: Mojang");
        } catch (InvalidCredentialsException ex) {
            // If it fails, attempt Microsoft
            System.out.println("[AutoAuthService] Mojang authentication failed! Attempting Microsoft authentication...");
            this.msaAuth.setUsername(this.username);
            this.msaAuth.setPassword(this.password);
            this.msaAuth.login();
            this.authType = AuthType.Microsoft;
            System.out.println("[AutoAuthService] Authenticated using: Microsoft");
        }
    }

    public enum AuthType {
        Mojang, Microsoft
    }
}

