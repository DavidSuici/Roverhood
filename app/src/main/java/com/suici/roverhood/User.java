package com.suici.roverhood;

public class User {
    private String username;
    private String accessCode;
    private String userType;
    private String team;
    private String id;
    private boolean offlineUser = false;

    public User() {}

    public User(String username, String accessCode, String userType, String team, String id, boolean offlineUser) {
        this.username = username;
        this.accessCode = accessCode;
        this.userType = userType;
        this.team = team;
        this.id = id;
        this.offlineUser = offlineUser;
    }

    public void setId(String id) { this.id = id; }
    public String getId() { return id; }
    public void setUsername(String username) {
        this.username = username;
    }
    public String getUsername() { return username; }
    public void setAccessCode(String accessCode) {
        this.accessCode = accessCode;
    }
    public String getAccessCode() { return accessCode; }
    public void setTeam(String team) { this.team = team; }
    public String getTeam() { return team; }
    public void setUserType(String userType) { this.userType = userType; }
    public String getUserType() { return userType; }
    public void setOfflineUser(boolean offlineUser) { this.offlineUser = offlineUser; }
    public boolean isOfflineUser() { return offlineUser; }
}