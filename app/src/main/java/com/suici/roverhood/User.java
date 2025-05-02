package com.suici.roverhood;

public class User {
    public String username;
    public String password;
    public String userType;
    public String team;
    public String id;

    public User() {}

    public User(String username, String password, String userType, String team, String id) {
        this.username = username;
        this.password = password;
        this.userType = userType;
        this.team = team;
        this.id = id;
    }

    public void setId(String id) {
        this.id = id;
    }
}