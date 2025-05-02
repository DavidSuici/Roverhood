package com.suici.roverhood;

public class User {
    public String username;
    public String accessCode;
    public String userType;
    public String team;
    public String id;

    public User() {}

    public User(String username, String accessCode, String userType, String team, String id) {
        this.username = username;
        this.accessCode = accessCode;
        this.userType = userType;
        this.team = team;
        this.id = id;
    }

    public void setId(String id) {
        this.id = id;
    }
}